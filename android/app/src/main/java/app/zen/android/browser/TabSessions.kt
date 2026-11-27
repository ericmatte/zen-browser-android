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
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse

const val PRIVATE_PREFIX = "private:"

fun isPrivateTab(tabId: String) = tabId.startsWith(PRIVATE_PREFIX)

interface TabEvents {
    fun onLocation(tabId: String, url: String)
    fun onTitle(tabId: String, title: String)
    fun onPageStart(tabId: String) {}
    fun onPageLoaded(tabId: String)
    fun onCanGoBack(tabId: String, canGoBack: Boolean)
    fun onCanGoForward(tabId: String, canGoForward: Boolean) {}
    fun onScroll(tabId: String, scrollY: Int)
    fun onProgress(tabId: String, progress: Int)
    fun onOpenInNewTab(fromTabId: String, url: String)
    fun onContextMenu(tabId: String, element: GeckoSession.ContentDelegate.ContextElement) {}
    fun onFullScreen(tabId: String, fullScreen: Boolean) {}
    fun onDownload(tabId: String, response: WebResponse) {}
    fun onSecurity(tabId: String, secure: Boolean) {}
    fun onMediaPlaying(tabId: String, playing: Boolean) {}
    /** The phone turned sideways while this tab plays a video, even a silent one MediaSession ignores. */
    fun onVideoRotated(tabId: String) {}
    fun onSiteUsage(tabId: String, bytes: Long) {}
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
            installHelpers(it)
            it.webExtensionController.promptDelegate = AdBlockerPrompts
            // Add-ons run in their own process, which GeckoView only starts once the app allows it (as Fenix does).
            it.webExtensionController.enableExtensionProcessSpawning()
        }
    }

    /**
     * uBlock Origin comes from addons.mozilla.org rather than the APK: it stays
     * Mozilla-signed and GeckoView keeps it updated. Only the switch in
     * Settings decides whether it runs.
     */
    const val UBLOCK_ID = "uBlock0@raymondhill.net"
    private const val UBLOCK_XPI = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
    private var adBlocking: Boolean? = null

    /** Read by the helper's cookies.js when a page loads. */
    @Volatile var rejectCookieBanners = true
        private set
    private var adBlockerChecked = false

    private fun setAdBlocking(runtime: GeckoRuntime, on: Boolean) {
        if (adBlocking == on) return
        adBlocking = on
        val controller = runtime.webExtensionController
        controller.list().accept({ list ->
            val ext = list?.firstOrNull { it.id == UBLOCK_ID }
            when {
                ext == null && on -> controller.install(UBLOCK_XPI).accept(
                    { installed -> installed?.let { controller.setAllowedInPrivateBrowsing(it, true) } },
                    { error ->
                        android.util.Log.w("Zen", "uBlock Origin install failed", error)
                        adBlocking = null
                        null
                    },
                )
                ext == null -> Unit
                on && !ext.metaData.enabled -> controller.enable(ext, WebExtensionController.EnableSource.APP)
                !on && ext.metaData.enabled -> controller.disable(ext, WebExtensionController.EnableSource.APP)
            }
            if (ext != null && on && !adBlockerChecked) {
                adBlockerChecked = true
                controller.update(ext)
            }
        }, { null })
    }

    /** Installs and updates the app asks for itself are granted; nothing else installs silently. */
    private object AdBlockerPrompts : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val ours = extension.id == UBLOCK_ID
            return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(ours, ours, false))
        }

        override fun onUpdatePrompt(
            extension: WebExtension,
            newPermissions: Array<String>,
            newOrigins: Array<String>,
            newDataCollectionPermissions: Array<String>,
        ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(if (extension.id == UBLOCK_ID) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
    }

    private var helpers: WebExtension? = null
    private val waitingForHelpers = mutableListOf<(WebExtension) -> Unit>()

    /** Runs [use] with the built-in helper extension once it is installed. */
    fun withHelpers(use: (WebExtension) -> Unit) = synchronized(waitingForHelpers) {
        helpers?.let(use) ?: waitingForHelpers.add(use)
    }

    /**
     * Gecko prefs with no runtime-settings API. Rotating into landscape is not a
     * user gesture, so fullscreen-on-rotate needs untrusted fullscreen requests;
     * FullScreenGate puts the gesture requirement back in the app (a recent
     * touch on the page, or a rotation while that page plays media), so a page
     * cannot cover the browser alone.
     */
    private fun writeConfig(context: Context): java.io.File =
        java.io.File(context.filesDir, "geckoview-config.yaml").apply {
            // Session state (history, scroll) is collected every few seconds rather than Gecko's 15 s, so
            // a tab Android reclaims in the background comes back close to where it was left.
            writeText("prefs:\n  full-screen-api.allow-trusted-requests-only: false\n  browser.sessionstore.interval: 3000\n  browser.sessionstore.platform_collection: true\n")
        }

    private fun installHelpers(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn("resource://android/assets/extensions/google-dark/", "google-dark@zen.android")
            .accept({ extension ->
                if (extension == null) return@accept
                if (!extension.metaData.allowedInPrivateBrowsing) controller.setAllowedInPrivateBrowsing(extension, true)
                synchronized(waitingForHelpers) {
                    helpers = extension
                    waitingForHelpers.forEach { it(extension) }
                    waitingForHelpers.clear()
                }
            }, { null })
    }

    /** Applies the privacy and display settings that desktop exposes in about:preferences. */
    fun apply(context: Context, prefs: app.zen.android.ui.Preferences) {
        val r = runtime(context)
        setAdBlocking(r, prefs.adBlocking)
        val strict = prefs.tracking == app.zen.android.ui.TrackingProtection.STRICT
        r.settings.contentBlocking.apply {
            setEnhancedTrackingProtectionLevel(if (strict) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
            setAntiTracking(if (strict) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP)
            setStrictSocialTrackingProtection(strict)
            setQueryParameterStrippingEnabled(true)
            setQueryParameterStrippingPrivateBrowsingEnabled(true)
        }
        r.settings.setGlobalPrivacyControl(true)
        rejectCookieBanners = prefs.rejectCookieBanners
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
    /** Tabs whose state is kept on disk; throwaway tabs (scratch, sign-in, glance) start fresh every time. */
    private val keepsState: (String) -> Boolean = { true },
) {
    private val snapshots = mutableMapOf<String, TabSnapshot>()
    private val latestScroll = mutableMapOf<String, Int>()
    private val scrollFlushes = mutableSetOf<String>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingScroll = mutableMapOf<String, Int>()
    private val states = TabStateStore(java.io.File(context.filesDir, "tabstate"), java.util.concurrent.Executors.newSingleThreadExecutor())

    private val live = LinkedHashMap<String, GeckoSession>(16, 0.75f, true)
    private val desktopMode = mutableSetOf<String>()

    fun obtain(tabId: String, url: String): GeckoSession {
        live[tabId]?.let { return it }
        val session = create(tabId)
        val snapshot = if (keepsState(tabId)) TabSnapshot.decode(states.load(tabId)) else null
        val saved = snapshot?.gecko?.let { GeckoSession.SessionState.fromString(it) }
        if (saved != null) session.restoreState(saved) else session.loadUri(url.ifBlank { "about:blank" })
        if (saved != null && snapshot.scrollY > 0) pendingScroll[tabId] = snapshot.scrollY
        snapshot?.let { snapshots[tabId] = it }
        live[tabId] = session
        trim(keep = tabId)
        return session
    }

    fun peek(tabId: String): GeckoSession? = live[tabId]

    fun close(tabId: String) {
        desktopMode.remove(tabId)
        live.remove(tabId)?.close()
    }

    /** Closes the tab for good: unlike an eviction from memory, its saved state goes too. */
    fun discard(tabId: String) {
        close(tabId)
        snapshots.remove(tabId)
        pendingScroll.remove(tabId)
        latestScroll.remove(tabId)
        states.delete(tabId)
    }

    fun discardAll(where: (String) -> Boolean) {
        live.keys.filter(where).forEach { discard(it) }
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

    /** The page's scroll offset, in CSS pixels. */
    private fun onPageScrolled(tabId: String, scrollY: Int) {
        events.onScroll(tabId, scrollY)
        // Saved once the page has been still for a moment, not on every report.
        latestScroll[tabId] = scrollY
        if (tabId !in scrollFlushes) {
            scrollFlushes += tabId
            main.postDelayed({
                scrollFlushes -= tabId
                latestScroll[tabId]?.let { y -> remember(tabId) { it.copy(scrollY = y) } }
            }, 800)
        }
    }

    private fun remember(tabId: String, change: (TabSnapshot) -> TabSnapshot) {
        if (!keepsState(tabId)) return
        val next = change(snapshots[tabId] ?: TabSnapshot(null, 0))
        snapshots[tabId] = next
        states.save(tabId, next.encode())
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

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = events.onCanGoForward(tabId, canGoForward)

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                if (events.interceptNavigation(tabId, request.uri)) return GeckoResult.deny()
                val scheme = request.uri.substringBefore(':').lowercase()
                if (scheme !in WEB_SCHEMES && openExternally(request.uri)) return GeckoResult.deny()
                if (AppLinks.handsOff(request.hasUserGesture, request.isDirectNavigation, isPrivateTab(tabId))) {
                    val app = AppLinks.find(context, request.uri)
                    if (app != null && AppLinks.open(context, request.uri, app)) return GeckoResult.deny()
                }
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
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                pendingScroll.remove(tabId)?.let { y ->
                    session.panZoomController.scrollTo(ScreenLength.zero(), ScreenLength.fromPixels(y.toDouble()), PanZoomController.SCROLL_BEHAVIOR_AUTO)
                }
                events.onPageLoaded(tabId)
            }
            override fun onSecurityChange(session: GeckoSession, info: GeckoSession.ProgressDelegate.SecurityInformation) =
                events.onSecurity(tabId, info.isSecure)

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                remember(tabId) { it.copy(gecko = sessionState.toString()) }
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) = events.onMediaPlaying(tabId, true)
            override fun onPause(session: GeckoSession, mediaSession: MediaSession) = events.onMediaPlaying(tabId, false)
            override fun onStop(session: GeckoSession, mediaSession: MediaSession) = events.onMediaPlaying(tabId, false)
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) = events.onMediaPlaying(tabId, false)
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) = onPageScrolled(tabId, scrollY)
        }
        // Only the helper's content script can send these: pages have no access to native messaging.
        Gecko.withHelpers { extension ->
            session.webExtensionController.setMessageDelegate(extension, object : WebExtension.MessageDelegate {
                override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
                    val json = message as? org.json.JSONObject
                    when (json?.optString("type")) {
                        "videoRotated" -> events.onVideoRotated(tabId)
                        "siteUsage" -> events.onSiteUsage(tabId, json.optLong("bytes"))
                        // Answers travel as plain values: GeckoView cannot pass a JSONObject back to the page.
                        "cookieBanners" -> return GeckoResult.fromValue(Gecko.rejectCookieBanners)
                    }
                    return GeckoResult.fromValue(true)
                }
            }, HELPERS_APP)
        }
        prompts?.let { session.promptDelegate = it }
        permissions?.let { session.permissionDelegate = it }
        session.open(Gecko.runtime(context))
        return session
    }

    private companion object {
        const val HELPERS_APP = "zen"
        val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "file", "javascript", "moz-extension", "resource", "view-source")
    }
}
