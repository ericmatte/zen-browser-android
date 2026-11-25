package app.zen.android.browser

import android.content.Context
import android.net.Uri
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

    class Permission(override val title: String, val message: String, val answer: (Boolean) -> Unit) : PageRequest {
        override fun cancel() = answer(false)
    }
}

/** Hooks only the Activity can provide: Android runtime permissions and the system file picker. */
interface ActivityBridge {
    fun requestPermissions(permissions: Array<String>, result: (Boolean) -> Unit)
    fun pickFiles(mimeTypes: Array<String>, multiple: Boolean, result: (List<Uri>) -> Unit)
}

class PageRequests(private val context: Context) : PromptDelegate, GeckoSession.PermissionDelegate {
    var current by mutableStateOf<PageRequest?>(null)
        private set
    var bridge: ActivityBridge? = null

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
        val host = runCatching { Uri.parse(perm.uri).host }.getOrNull() ?: "This site"
        val what = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW)
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "know your location"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "send notifications"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "store data on your device"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "play protected content"
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "use its cookies here"
            else -> return GeckoResult.fromValue(ContentPermission.VALUE_DENY)
        }
        return respond { r ->
            PageRequest.Permission(host, "$host wants to $what.") { ok ->
                r.complete(if (ok) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
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
        val host = runCatching { Uri.parse(uri).host }.getOrNull() ?: "This site"
        val wants = listOfNotNull(video?.firstOrNull()?.let { "camera" }, audio?.firstOrNull()?.let { "microphone" }).joinToString(" and ")
        show(PageRequest.Permission(host, "$host wants to use your $wants.") { ok ->
            if (ok) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
        })
    }
}
