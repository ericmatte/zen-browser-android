package app.zen.android.browser

import android.content.Context
import android.net.Uri
import app.zen.android.ui.Autoplay
import app.zen.android.ui.Preferences
import app.zen.android.ui.SitePermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/** What the UI must ask the user; every request resolves exactly once. */
sealed interface PageRequest {
    val title: String
    fun cancel()

    class Alert(override val title: String, val message: String, val done: () -> Unit) : PageRequest {
        override fun cancel() = done()
    }

    class Confirm(override val title: String, val message: String, val answer: (Boolean) -> Unit) : PageRequest {
        override fun cancel() = answer(false)
    }

    class TextInput(override val title: String, val message: String, val initial: String, val answer: (String?) -> Unit) : PageRequest {
        override fun cancel() = answer(null)
    }

    data class Option(val id: String, val label: String, val selected: Boolean, val enabled: Boolean, val header: Boolean)

    class Choice(override val title: String, val options: List<Option>, val multiple: Boolean, val answer: (List<String>?) -> Unit) : PageRequest {
        override fun cancel() = answer(null)
    }

    /** [canRemember] is false in private tabs, which never store decisions. */
    class Permission(
        override val title: String,
        val message: String,
        val canRemember: Boolean,
        val answer: (allow: Boolean, remember: Boolean) -> Unit,
    ) : PageRequest {
        override fun cancel() = answer(false, false)
    }
}

/** Hooks only the Activity can provide: Android runtime permissions and the system file picker. */
interface ActivityBridge {
    fun requestPermissions(permissions: Array<String>, result: (Boolean) -> Unit)
    fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, result: (List<Uri>) -> Unit)
}

class PageRequests(
    private val context: Context,
    private val preferences: () -> Preferences = { Preferences() },
) : PromptDelegate, GeckoSession.PermissionDelegate {
    var current by mutableStateOf<PageRequest?>(null)
        private set
    var bridge: ActivityBridge? = null
    private val mediaStore = context.getSharedPreferences("site-media", Context.MODE_PRIVATE)
    val media = MediaDecisions(
        read = { if (mediaStore.contains(it)) mediaStore.getBoolean(it, false) else null },
        write = { k, v -> mediaStore.edit().apply { if (v == null) remove(k) else putBoolean(k, v) }.apply() },
    )

    fun forgetMedia() = mediaStore.edit().clear().apply()

    private val queue = ArrayDeque<PageRequest>()

    private fun show(request: PageRequest) {
        if (current == null) current = request else queue += request
    }

    fun resolved() {
        current = queue.removeFirstOrNull()
    }

    private fun <T> respond(build: (GeckoResult<T>) -> PageRequest): GeckoResult<T> {
        val result = GeckoResult<T>()
        show(build(result))
        return result
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse> =
        respond { r -> PageRequest.Alert(prompt.title.orEmpty(), prompt.message.orEmpty()) { r.complete(prompt.dismiss()) } }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse> =
        respond { r ->
            PageRequest.Confirm(prompt.title.orEmpty(), prompt.message.orEmpty()) { ok ->
                r.complete(prompt.confirm(if (ok) PromptDelegate.ButtonPrompt.Type.POSITIVE else PromptDelegate.ButtonPrompt.Type.NEGATIVE))
            }
        }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse> =
        respond { r ->
            PageRequest.TextInput(prompt.title.orEmpty(), prompt.message.orEmpty(), prompt.defaultValue.orEmpty()) { text ->
                r.complete(if (text == null) prompt.dismiss() else prompt.confirm(text))
            }
        }

    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse> {
        val byId = mutableMapOf<String, PromptDelegate.ChoicePrompt.Choice>()
        fun flatten(choices: Array<PromptDelegate.ChoicePrompt.Choice>): List<PageRequest.Option> = choices.flatMap { c ->
            val children = c.items
            if (children != null) {
                listOf(PageRequest.Option(c.id, c.label, false, false, header = true)) + flatten(children)
            } else if (c.separator) {
                emptyList()
            } else {
                byId[c.id] = c
                listOf(PageRequest.Option(c.id, c.label, c.selected, !c.disabled, header = false))
            }
        }
        val options = flatten(prompt.choices)
        val multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE
        return respond { r ->
            PageRequest.Choice(prompt.title.orEmpty(), options, multiple) { ids ->
                val picked = ids?.mapNotNull { byId[it] }
                r.complete(
                    when {
                        picked == null -> prompt.dismiss()
                        multiple -> prompt.confirm(picked.toTypedArray())
                        picked.isEmpty() -> prompt.dismiss()
                        else -> prompt.confirm(picked.first())
                    }
                )
            }
        }
    }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        val picker = bridge
        if (picker == null) {
            result.complete(prompt.dismiss())
            return result
        }
        val types = prompt.mimeTypes?.takeIf { it.isNotEmpty() } ?: arrayOf("*/*")
        picker.pickFiles(types, prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE) { uris ->
            result.complete(
                when {
                    uris.isEmpty() -> prompt.dismiss()
                    uris.size == 1 -> prompt.confirm(context, uris[0])
                    else -> prompt.confirm(context, uris.toTypedArray())
                }
            )
        }
        return result
    }

    override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int> {
        val prefs = preferences()
        val host = runCatching { Uri.parse(perm.uri).host }.getOrNull() ?: "This site"
        fun decided(allow: Boolean) = GeckoResult.fromValue(if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
        val what = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return decided(prefs.autoplay != Autoplay.BLOCK_ALL)
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> return decided(prefs.autoplay == Autoplay.ALLOW)
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                if (prefs.location == SitePermission.BLOCK) return decided(false) else "know your location"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION ->
                if (prefs.notifications == SitePermission.BLOCK) return decided(false) else "send notifications"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "store data on your device"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected content"
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "use its cookies here"
            else -> return decided(false)
        }
        return respond { r ->
            PageRequest.Permission(host, "$host wants to $what.", canRemember = !perm.privateMode) { allow, remember ->
                val value = if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY
                // Stored decisions stop Gecko from asking again on every page of the site.
                if (remember && !perm.privateMode) Gecko.runtime(context).storageController.setPermission(perm, value)
                r.complete(value)
            }
        }
    }

    override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<out String>?,
        callback: GeckoSession.PermissionDelegate.Callback,
    ) {
        val picker = bridge
        if (permissions.isNullOrEmpty() || picker == null) {
            callback.reject()
            return
        }
        picker.requestPermissions(arrayOf(*permissions)) { granted -> if (granted) callback.grant() else callback.reject() }
    }

    override fun onMediaPermissionRequest(
        session: GeckoSession,
        uri: String,
        video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
        callback: GeckoSession.PermissionDelegate.MediaCallback,
    ) {
        val prefs = preferences()
        val wantsVideo = video?.firstOrNull() != null
        val wantsAudio = audio?.firstOrNull() != null
        if ((wantsVideo && prefs.camera == SitePermission.BLOCK) || (wantsAudio && prefs.microphone == SitePermission.BLOCK)) {
            callback.reject()
            return
        }
        val host = runCatching { Uri.parse(uri).host }.getOrNull() ?: "This site"
        val kinds = listOfNotNull(CAMERA_KIND.takeIf { wantsVideo }, MICROPHONE_KIND.takeIf { wantsAudio })
        fun answer(ok: Boolean) = if (ok) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
        media.decided(host, kinds)?.let { return answer(it) }
        val wants = listOfNotNull(video?.firstOrNull()?.let { "camera" }, audio?.firstOrNull()?.let { "microphone" }).joinToString(" and ")
        show(PageRequest.Permission(host, "$host wants to use your $wants.", canRemember = !session.settings.usePrivateMode) { ok, remember ->
            if (remember) media.remember(host, kinds, ok)
            answer(ok)
        })
    }
}
