package app.zen.android.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zen.android.browser.TabEvents
import app.zen.android.browser.TabSessions
import app.zen.android.browser.UrlInput
import app.zen.android.sync.FileCredentialStore
import app.zen.android.sync.FxaAuth
import app.zen.android.sync.LocalEdit
import app.zen.android.sync.PendingLogin
import app.zen.android.sync.SpacesRepository
import app.zen.android.sync.SpacesState
import app.zen.android.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession
import java.io.File

const val SCRATCH_TAB = "__scratch__"
const val LOGIN_TAB = "__login__"
private const val HOME_URL = "https://duckduckgo.com/"

class BrowserViewModel(app: Application) : AndroidViewModel(app), TabEvents {

    val repository = SpacesRepository(
        dir = File(app.filesDir, "sync"),
        credentialStore = FileCredentialStore(File(app.noBackupFilesDir, "fxa.json")),
    )
    private val auth = FxaAuth()
    private val sessions = TabSessions(app, this)

    val spaces: StateFlow<SpacesState> = repository.view
    val status: StateFlow<SyncStatus> = repository.status

    var activeSpace by mutableStateOf<String?>(null)
    var activeTab by mutableStateOf(SCRATCH_TAB)
        private set
    var session by mutableStateOf<GeckoSession?>(null)
        private set
    var url by mutableStateOf("")
        private set
    var progress by mutableStateOf(100)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var chromeVisible by mutableStateOf(true)
        private set
    var loginSession by mutableStateOf<GeckoSession?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    private val titles = mutableMapOf<String, String>()
    private val urls = mutableMapOf<String, String>()
    private var lastScrollY = 0
    private var pendingLogin: PendingLogin? = null
    private var syncLoop: Job? = null

    init {
        viewModelScope.launch {
            repository.load()
            val first = repository.view.value.spaces().firstOrNull()
            activeSpace = first?.uuid
            val tab = first?.tabs?.firstOrNull()
            if (tab != null) selectTab(tab.id, tab.url) else selectTab(SCRATCH_TAB, HOME_URL)
            startSyncLoop()
        }
    }

    fun startSyncLoop() {
        if (syncLoop?.isActive == true) return
        syncLoop = viewModelScope.launch {
            while (isActive) {
                repository.sync()
                delay(60_000)
            }
        }
    }

    fun stopSyncLoop() {
        syncLoop?.cancel()
    }

    fun syncNow() = viewModelScope.launch { repository.sync() }

    fun selectTab(tabId: String, tabUrl: String) {
        val s = sessions.obtain(tabId, urls[tabId] ?: tabUrl)
        sessions.setActive(tabId)
        activeTab = tabId
        session = s
        url = urls[tabId] ?: tabUrl
        chromeVisible = true
        repository.view.value.spaces().firstOrNull { sp -> sp.tabs.any { it.id == tabId } }?.let { activeSpace = it.uuid }
    }

    fun navigate(input: String) {
        val target = UrlInput.resolve(input)
        session?.loadUri(target) ?: selectTab(activeTab, target)
    }

    fun newTab(target: String = HOME_URL) {
        val space = activeSpace
        if (space == null) {
            sessions.close(SCRATCH_TAB)
            urls.remove(SCRATCH_TAB)
            selectTab(SCRATCH_TAB, target)
            return
        }
        val id = SpacesState.newTabId()
        viewModelScope.launch {
            repository.edit(LocalEdit.OpenTab(id, space, target, UrlInput.display(target)))
            selectTab(id, target)
            repository.sync()
        }
    }

    fun closeTab(tabId: String) {
        sessions.close(tabId)
        urls.remove(tabId)
        titles.remove(tabId)
        viewModelScope.launch {
            if (tabId != SCRATCH_TAB) repository.edit(LocalEdit.CloseTab(tabId))
            if (tabId == activeTab) {
                val next = repository.view.value.spaces().firstOrNull { it.uuid == activeSpace }?.tabs?.firstOrNull()
                if (next != null) selectTab(next.id, next.url) else selectTab(SCRATCH_TAB, HOME_URL)
            }
            repository.sync()
        }
    }

    fun goBack(): Boolean {
        if (!canGoBack) return false
        session?.goBack()
        return true
    }

    fun beginLogin() {
        val pending = PendingLogin().also { pendingLogin = it }
        loginSession = sessions.obtain(LOGIN_TAB, pending.authorizationUrl())
    }

    fun cancelLogin() {
        sessions.close(LOGIN_TAB)
        loginSession = null
        pendingLogin = null
    }

    fun signOut() = viewModelScope.launch {
        repository.signOut()
        activeSpace = null
        selectTab(SCRATCH_TAB, HOME_URL)
    }

    override fun interceptNavigation(tabId: String, url: String): Boolean {
        if (tabId != LOGIN_TAB || !auth.isRedirect(url)) return false
        val pending = pendingLogin ?: return true
        viewModelScope.launch {
            try {
                val credentials = withContext(Dispatchers.IO) { auth.completeLogin(pending, url) }
                repository.signIn(credentials)
                repository.view.value.spaces().firstOrNull()?.let { activeSpace = it.uuid }
            } catch (e: Exception) {
                error = "Sign-in failed: ${e.message}"
            } finally {
                cancelLogin()
            }
        }
        return true
    }

    override fun onLocation(tabId: String, url: String) {
        if (tabId == LOGIN_TAB) return
        urls[tabId] = url
        if (tabId == activeTab) this.url = url
    }

    override fun onTitle(tabId: String, title: String) {
        if (tabId != LOGIN_TAB) titles[tabId] = title
    }

    override fun onPageLoaded(tabId: String) {
        if (tabId == LOGIN_TAB || tabId == SCRATCH_TAB) return
        val u = urls[tabId] ?: return
        val synced = repository.view.value.tab(tabId) ?: return
        val title = titles[tabId] ?: synced.title
        if (synced.url == u && synced.title == title) return
        viewModelScope.launch { repository.edit(LocalEdit.UpdateTab(tabId, u, title)) }
    }

    override fun onCanGoBack(tabId: String, canGoBack: Boolean) {
        if (tabId == activeTab) this.canGoBack = canGoBack
    }

    override fun onScroll(tabId: String, scrollY: Int) {
        if (tabId != activeTab) return
        val delta = scrollY - lastScrollY
        lastScrollY = scrollY
        when {
            scrollY < 24 -> chromeVisible = true
            delta > 12 -> chromeVisible = false
            delta < -12 -> chromeVisible = true
        }
    }

    override fun onProgress(tabId: String, progress: Int) {
        if (tabId == activeTab) this.progress = progress
    }

    override fun onOpenInNewTab(url: String) = newTab(url)

    override fun onCleared() {
        stopSyncLoop()
    }
}
