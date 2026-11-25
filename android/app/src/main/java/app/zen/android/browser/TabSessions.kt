package app.zen.android.browser

import android.content.Context
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

interface TabEvents {
    fun onLocation(tabId: String, url: String)
    fun onTitle(tabId: String, title: String)
    fun onPageLoaded(tabId: String)
    fun onCanGoBack(tabId: String, canGoBack: Boolean)
    fun onScroll(tabId: String, scrollY: Int)
    fun onProgress(tabId: String, progress: Int)
    fun onOpenInNewTab(url: String)
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
                .consoleOutput(false)
                .aboutConfigEnabled(false)
                .build(),
        ).also { runtime = it }
    }
}

/**
 * Keeps at most [maxLive] GeckoSessions alive. Older tabs are closed and
 * reopened from their URL on demand, which keeps memory (and therefore
 * responsiveness) bounded no matter how many tabs the desktop syncs over.
 */
class TabSessions(
    private val context: Context,
    private val events: TabEvents,
    private val maxLive: Int = 4,
) {
    private val live = LinkedHashMap<String, GeckoSession>(16, 0.75f, true)

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
        live.remove(tabId)?.close()
    }

    fun setActive(tabId: String) {
        live.forEach { (id, session) -> session.setActive(id == tabId) }
    }

    private fun trim(keep: String) {
        while (live.size > maxLive) {
            val eldest = live.keys.first { it != keep }
            live.remove(eldest)?.close()
        }
    }

    private fun create(tabId: String): GeckoSession {
        val session = GeckoSession(
            GeckoSessionSettings.Builder()
                .usePrivateMode(false)
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
            ): GeckoResult<AllowOrDeny>? =
                if (events.interceptNavigation(tabId, request.uri)) GeckoResult.deny() else null

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                events.onOpenInNewTab(uri)
                return null
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                title?.let { events.onTitle(tabId, it) }
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) = events.onProgress(tabId, progress)
            override fun onPageStop(session: GeckoSession, success: Boolean) = events.onPageLoaded(tabId)
        }
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) = events.onScroll(tabId, scrollY)
        }
        session.open(Gecko.runtime(context))
        return session
    }
}
