package app.zen.android.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse

const val PRIVATE_PREFIX = "private:"

fun isPrivateTab(tabId: String) = tabId.startsWith(PRIVATE_PREFIX)

interface TabEvents {
    fun onLocation(tabId: String, url: String)
    fun onTitle(tabId: String, title: String)
    fun onPageStart(tabId: String) {}
    fun onPageLoaded(tabId: String)
    fun onCanGoBack(tabId: String, canGoBack: Boolean)
    fun onScroll(tabId: String, scrollY: Int)
    fun onProgress(tabId: String, progress: Int)
    fun onOpenInNewTab(fromTabId: String, url: String)
    fun onContextMenu(tabId: String, element: GeckoSession.ContentDelegate.ContextElement) {}
    fun onFullScreen(tabId: String, fullScreen: Boolean) {}
    fun onDownload(tabId: String, response: WebResponse) {}
    fun onSecurity(tabId: String, secure: Boolean) {}
    /** Returns true when the navigation was consumed (e.g. the FxA OAuth redirect). */
    fun interceptNavigation(tabId: String, url: String): Boolean = false
}

object Gecko {
    @Volatile private var runtime: GeckoRuntime? = null

    fun runtime(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
        runtime ?: GeckoRuntime.create(
            context.applicationContext,
            GeckoRuntimeSettings.Builder()
                .contentBlocking(
                    ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
                        .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                        .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                        .build()
                )
                .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
                .configFilePath(writeConfig(context).absolutePath)
                .consoleOutput(false)
                .aboutConfigEnabled(false)
                .build(),
        ).also {
            runtime = it
            installGoogleDark(it)
        }
    }

    /**
     * Gecko prefs with no runtime-settings API. Rotating into landscape is not a
     * user gesture, so fullscreen-on-rotate needs untrusted fullscreen requests;
     * FullScreenGate puts the gesture requirement back in the app (a recent
     * touch on the page or a rotation), so a page cannot cover the browser alone.
     */
    private fun writeConfig(context: Context): java.io.File =
        java.io.File(context.filesDir, "geckoview-config.yaml").apply {
            writeText("prefs:\n  full-screen-api.allow-trusted-requests-only: false\n")
        }

    private fun installGoogleDark(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn("resource://android/assets/extensions/google-dark/", "google-dark@zen.android")
            .accept({ extension ->
                if (extension != null && !extension.metaData.allowedInPrivateBrowsing) {
                    controller.setAllowedInPrivateBrowsing(extension, true)
                }
            }, { null })
    }

    /** Applies the privacy and display settings that desktop exposes in about:preferences. */
    fun apply(context: Context, prefs: app.zen.android.ui.Preferences) {
        val r = runtime(context)
        val strict = prefs.tracking == app.zen.android.ui.TrackingProtection.STRICT
        r.settings.contentBlocking.apply {
            setEnhancedTrackingProtectionLevel(if (strict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
            setAntiTracking(if (strict) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
            setStrictSocialTrackingProtection(strict)
            setQueryParameterStrippingEnabled(prefs.stripTrackingParams)
            setQueryParameterStrippingPrivateBrowsingEnabled(true)
        }
        r.settings.setGlobalPrivacyControl(prefs.globalPrivacyControl)
        r.settings.setAllowInsecureConnections(
            when (prefs.httpsOnly) {
                app.zen.android.ui.HttpsOnly.OFF -> GeckoRuntimeSettings.ALLOW_ALL
                app.zen.android.ui.HttpsOnly.PRIVATE -> GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE
                app.zen.android.ui.HttpsOnly.ALL -> GeckoRuntimeSettings.HTTPS_ONLY
            }
        )
        r.settings.setTrustedRecursiveResolverMode(
            when (prefs.dns) {
                app.zen.android.ui.DnsOverHttps.OFF -> GeckoRuntimeSettings.TRR_MODE_OFF
                app.zen.android.ui.DnsOverHttps.DEFAULT -> GeckoRuntimeSettings.TRR_MODE_FIRST
                app.zen.android.ui.DnsOverHttps.MAX -> GeckoRuntimeSettings.TRR_MODE_ONLY
            }
        )
        if (prefs.dns != app.zen.android.ui.DnsOverHttps.OFF) r.settings.setTrustedRecursiveResolverUri(DOH_URI)
        r.settings.setAutomaticFontSizeAdjustment(false)
        r.settings.setFontSizeFactor(prefs.textScale)
        r.settings.setForceUserScalableEnabled(prefs.forceZoom)
    }

    /** Firefox's default DoH provider. */
    private const val DOH_URI = "https://mozilla.cloudflare-dns.com/dns-query"

    fun setColorScheme(context: Context, scheme: Int) {
        runtime(context).settings.preferredColorScheme = scheme
    }

    fun clearData(context: Context): GeckoResult<Void> =
        runtime(context).storageController.clearData(StorageController.ClearFlags.ALL)
}

/**
 * Keeps at most [maxLive] GeckoSessions alive. Older tabs are closed and
 * reopened from their URL on demand, which keeps memory (and therefore
 * responsiveness) bounded no matter how many tabs the desktop syncs over.
 * Private tabs are never evicted: reopening them would lose their state.
 */
class TabSessions(
    private val context: Context,
    private val events: TabEvents,
    private val prompts: GeckoSession.PromptDelegate? = null,
    private val permissions: GeckoSession.PermissionDelegate? = null,
    private val maxLive: Int = 4,
) {
    private val live = LinkedHashMap<String, GeckoSession>(16, 0.75f, true)
    private val desktopMode = mutableSetOf<String>()

    fun obtain(tabId: String, url: String): GeckoSession {
        live[tabId]?.let { return it }
        val session = create(tabId)
        session.loadUri(url.ifBlank { "about:blank" })
        live[tabId] = session
        trim(keep = tabId)
        return session
    }

    fun peek(tabId: String): GeckoSession? = live[tabId]

    fun close(tabId: String) {
        desktopMode.remove(tabId)
        live.remove(tabId)?.close()
    }

    fun closeAll(where: (String) -> Boolean) {
        live.keys.filter(where).forEach { close(it) }
    }

    fun setActive(tabId: String) {
        live.forEach { (id, session) -> session.setActive(id == tabId) }
    }

    fun isDesktopMode(tabId: String) = tabId in desktopMode

    fun toggleDesktopMode(tabId: String) {
        val session = live[tabId] ?: return
        val desktop = tabId !in desktopMode
        if (desktop) desktopMode += tabId else desktopMode -= tabId
        session.settings.userAgentMode = if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode = if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        session.reload()
    }

    private fun trim(keep: String) {
        while (live.count { !isPrivateTab(it.key) } > maxLive) {
            val eldest = live.keys.firstOrNull { it != keep && !isPrivateTab(it) } ?: return
            close(eldest)
        }
    }

    private fun openExternally(uri: String): Boolean {
        val intent = if (uri.startsWith("intent:")) {
            runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return false
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        }
        intent.addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.component = null
        intent.selector = null
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun create(tabId: String): GeckoSession {
        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(isPrivateTab(tabId))
                .useTrackingProtection(true)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .build()
        )
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                url?.let { events.onLocation(tabId, it) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = events.onCanGoBack(tabId, canGoBack)

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                if (events.interceptNavigation(tabId, request.uri)) return GeckoResult.deny()
                val scheme = request.uri.substringBefore(':').lowercase()
                if (scheme !in WEB_SCHEMES && openExternally(request.uri)) return GeckoResult.deny()
                return null
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                events.onOpenInNewTab(tabId, uri)
                return null
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let { events.onTitle(tabId, it) }
            }

            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) =
                events.onContextMenu(tabId, element)

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) = events.onFullScreen(tabId, fullScreen)

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) = events.onDownload(tabId, response)

            override fun onCrash(session: GeckoSession) {
                close(tabId)
            }

            override fun onKill(session: GeckoSession) {
                close(tabId)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) = events.onProgress(tabId, progress)
            override fun onPageStart(session: GeckoSession, url: String) = events.onPageStart(tabId)
            override fun onPageStop(session: GeckoSession, success: Boolean) = events.onPageLoaded(tabId)
            override fun onSecurityChange(session: GeckoSession, info: GeckoSession.ProgressDelegate.SecurityInformation) =
                events.onSecurity(tabId, info.isSecure)
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) = events.onScroll(tabId, scrollY)
        }
        prompts?.let { session.promptDelegate = it }
        permissions?.let { session.permissionDelegate = it }
        session.open(Gecko.runtime(context))
        return session
    }

    private companion object {
        val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "file", "javascript", "moz-extension", "resource", "view-source")
    }
}
