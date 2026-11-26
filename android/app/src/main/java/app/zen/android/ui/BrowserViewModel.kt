package app.zen.android.ui

import kotlinx.coroutines.flow.asStateFlow
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import app.zen.android.browser.DownloadStatus
import app.zen.android.browser.DownloadItem
import app.zen.android.browser.Downloads
import app.zen.android.sync.SidebarItem
import app.zen.android.sync.FolderItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import app.zen.android.sync.LocalTabs
import app.zen.android.browser.Release
import app.zen.android.browser.Updates
import android.app.Application
import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zen.android.browser.Dismissal
import app.zen.android.browser.DismissedSuggestions
import app.zen.android.browser.EmptySelection
import app.zen.android.browser.FullScreenGate
import app.zen.android.browser.Gecko
import app.zen.android.browser.History
import app.zen.android.browser.HistorySort
import app.zen.android.browser.RecentSearches
import app.zen.android.browser.Visit
import app.zen.android.browser.PRIVATE_PREFIX
import app.zen.android.browser.PageRequests
import app.zen.android.browser.TabEvents
import app.zen.android.browser.TabSessions
import app.zen.android.browser.UrlInput
import app.zen.android.browser.isPrivateTab
import app.zen.android.sync.FileCredentialStore
import app.zen.android.sync.FxaAuth
import app.zen.android.sync.LocalEdit
import app.zen.android.sync.PendingLogin
import app.zen.android.sync.Space
import app.zen.android.sync.SpacesRepository
import app.zen.android.sync.SpacesState
import app.zen.android.sync.SyncStatus
import app.zen.android.sync.TabItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement
import org.mozilla.geckoview.GeckoSession.FinderResult
import org.mozilla.geckoview.WebResponse

const val SCRATCH_TAB = "__scratch__"

/** No page is shown: the launch search, or private mode waiting for the user to pick a tab. */
const val NO_TAB = ""

/** Reload feedback (pull indicator, favicon spinner) never outlasts this, even on pages that never settle. */
const val RELOAD_FEEDBACK_MILLIS = 2000L
const val LOGIN_TAB = "__login__"
const val GLANCE_TAB = "__glance__"

/** Tabs that exist only on this phone: never written to the synced spaces collection. */
const val ESSENTIAL_PREFIX = "ess:"
const val MAX_ESSENTIALS = 12

private fun isLocalOnly(tabId: String) =
    tabId == SCRATCH_TAB || tabId == LOGIN_TAB || tabId.endsWith(GLANCE_TAB) || isPrivateTab(tabId) || tabId.startsWith(ESSENTIAL_PREFIX)

sealed interface Suggestion {
    data class OpenTab(val tab: TabItem) : Suggestion
    data class Search(val text: String, val recent: Boolean = false) : Suggestion
    data class Visited(val visit: Visit) : Suggestion
}

/** Identifies a suggestion across lists, so a swiped-away one stays away. */
val Suggestion.dismissKey: String
    get() = when (this) {
        is Suggestion.OpenTab -> "tab:" + tab.url
        is Suggestion.Search -> "search:" + text.trim().lowercase()
        is Suggestion.Visited -> "page:" + visit.url
    }

class BrowserViewModel(app: Application) : AndroidViewModel(app), TabEvents {

    val repository = SpacesRepository(
        dir = File(app.filesDir, "sync"),
        credentialStore = FileCredentialStore(File(app.noBackupFilesDir, "fxa.json")),
    )
    val settings = AppSettings(app)
    val requests = PageRequests(app) { settings.state.value }
    private val visits = History(File(app.filesDir, "history.json"))
    private var saveVisits: Job? = null
    private val auth = FxaAuth()
    private val sessions = TabSessions(app, this, requests, requests, keepsState = { id ->
        !isPrivateTab(id) && id != SCRATCH_TAB && id != LOGIN_TAB && !id.endsWith(GLANCE_TAB)
    })

    private val localTabs = MutableStateFlow(LocalTabs.decode(settings.string("localTabs")))

    /** Synced spaces with the phone's own temporary tabs under each one (see LocalTabs). */
    val spaces: StateFlow<List<Space>> = combine(repository.view, localTabs) { state, local -> LocalTabs.merge(state.spaces(), local) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun allSpaces(): List<Space> = LocalTabs.merge(repository.view.value.spaces(), localTabs.value)

    private fun isLocalTab(id: String) = localTabs.value.any { it.id == id }

    private fun setLocalTabs(list: List<TabItem>) {
        localTabs.value = list
        settings.putString("localTabs", LocalTabs.encode(list))
    }
    val essentials: StateFlow<List<TabItem>> = settings.essentials

    var accountEmail by mutableStateOf(settings.string("accountEmail"))
        private set
    var showLibrary by mutableStateOf(false)
    var showWelcome by mutableStateOf(settings.string("welcomed") == null)
        private set

    fun finishWelcome() {
        showWelcome = false
        settings.putString("welcomed", "1")
    }
    var libraryTab by mutableStateOf(LibraryTab.HISTORY)
    val status: StateFlow<SyncStatus> = repository.status
    val lastSync = repository.lastSync

    var ready by mutableStateOf(false)
        private set
    var activeSpace by mutableStateOf<String?>(null)
    var activeTab by mutableStateOf(NO_TAB)
        private set
    var session by mutableStateOf<GeckoSession?>(null)
        private set
    var url by mutableStateOf("")
        private set
    var title by mutableStateOf("")
        private set

    /** The desktop-style search shown at launch and for every new tab. */
    var launcherOpen by mutableStateOf(false)
        private set
    var secure by mutableStateOf(true)
        private set
    var progress by mutableIntStateOf(100)
        private set

    /** Between Gecko's page start and stop; progress alone can stall below 100 on pages that keep fetching. */
    var loading by mutableStateOf(false)
        private set
    private val loadingTabs = mutableSetOf<String>()
    var chromeVisible by mutableStateOf(true)
        private set
    var fullScreen by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var loginSession by mutableStateOf<GeckoSession?>(null)
        private set
    var showSettings by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var pageTopColor by mutableStateOf<Color?>(null)
    var contextMenu by mutableStateOf<ContextElement?>(null)

    var privateMode by mutableStateOf(false)
        private set
    val privateTabs = mutableStateListOf<TabItem>()

    var glance by mutableStateOf<GeckoSession?>(null)
        private set
    var glanceUrl by mutableStateOf("")
        private set

    var findQuery by mutableStateOf<String?>(null)
        private set
    var findResult by mutableStateOf("")
        private set

    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    private var suggestJob: Job? = null

    /** Bumped whenever the visible page may have changed colour, so the UI re-samples it. */
    var captureRequest by mutableIntStateOf(0)
        private set

    private val titles = mutableMapOf<String, String>()
    private val urls = mutableMapOf<String, String>()
    private val backHistory = mutableMapOf<String, Boolean>()
    private val forwardHistory = mutableMapOf<String, Boolean>()
    private var lastScrollY = 0
    private var scrollSettle: Job? = null
    private var pendingLogin: PendingLogin? = null
    private var syncLoop: Job? = null
    private var lastNormalTab: TabItem? = null
    // Kept across launches, so leaving the launch search returns to the tab used last.
    private val recentIds = ArrayDeque(settings.string("recentTabs")?.split(',')?.filter { it.isNotEmpty() }.orEmpty())

    private val home get() = settings.state.value.searchEngine.home

    private val openedHere = settings.string("openedHere")?.split(',')?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()
    private val lastSeen = mutableMapOf<String, Long>()

    init {
        applyThemeMode(settings.state.value.themeMode)
        viewModelScope.launch { settings.state.collect { Gecko.apply(getApplication(), it) } }
        viewModelScope.launch {
            repository.load()
            migrateTemporaryTabs()
            refreshEmail()
            activeSpace = repository.view.value.spaces().firstOrNull()?.uuid
            ready = true
            val link = incoming
            incoming = null
            when {
                privateRequested -> startPrivateBrowsing()
                link != null -> openIncoming(link)
                else -> openLauncher()
            }
            startSyncLoop()
            while (isActive) {
                delay(10 * 60_000)
                cleanUpIdleTabs()
            }
        }
    }

    /** Closes the phone's temporary tabs once they sit unseen longer than the chosen delay. */
    private fun cleanUpIdleTabs() {
        val hours = settings.state.value.tabCleanup.hours ?: return
        val ids = localTabs.value.map { it.id }
        val expired = app.zen.android.browser.TabCleanupRule.expired(ids, ids.toSet(), lastSeen, activeTab, System.currentTimeMillis(), hours * 3_600_000)
        closeTabs(expired)
    }

    /** Once: temporary tabs earlier versions synced stay on desktop, and the phone keeps its own copies. */
    private fun migrateTemporaryTabs() {
        if (settings.string("localTabsMigrated") != null) return
        val copies = LocalTabs.migrate(repository.view.value.spaces(), openedHere, SpacesState::newTabId)
        setLocalTabs(localTabs.value + copies)
        settings.putString("localTabsMigrated", "1")
    }

    private fun refreshEmail() {
        if (repository.status.value == SyncStatus.SignedOut) return
        viewModelScope.launch {
            val creds = withContext(Dispatchers.IO) { FileCredentialStore(File(getApplication<Application>().noBackupFilesDir, "fxa.json")).load() } ?: return@launch
            val email = withContext(Dispatchers.IO) { runCatching { auth.email(creds) }.getOrNull() } ?: return@launch
            accountEmail = email
            settings.putString("accountEmail", email)
        }
    }


    fun startSyncLoop() {
        if (syncLoop?.isActive == true) return
        syncLoop = viewModelScope.launch {
            while (isActive) {
                repository.sync()
                // Desktop pushes changes within minutes; this keeps the phone fresh without draining the battery.
                delay(5 * 60_000)
            }
        }
    }

    fun stopSyncLoop() {
        syncLoop?.cancel()
    }

    fun syncNow() = viewModelScope.launch { repository.sync() }

    /** A newer release than this build, once a check found one. Drives the yellow dots. */
    var update by mutableStateOf<Release?>(cachedUpdate())
        private set
    var checkingUpdates by mutableStateOf(false)
        private set

    private fun cachedUpdate(): Release? {
        val version = settings.string("updateVersion") ?: return null
        val page = settings.string("updatePage") ?: return null
        return Release(version, page).takeIf { Updates.isNewer(version, app.zen.android.BuildConfig.VERSION_NAME) }
    }

    /** At most twice a day unless [force]d; one anonymous request to GitHub's public API. */
    fun checkForUpdates(force: Boolean = false) {
        if (!force && !settings.state.value.checkUpdates) return
        val last = settings.string("updateChecked")?.toLongOrNull() ?: 0L
        if (!force && System.currentTimeMillis() - last < Updates.CHECK_EVERY_MILLIS) return
        if (checkingUpdates) return
        checkingUpdates = true
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { Updates.fetchLatest() }
            checkingUpdates = false
            if (latest == null) {
                if (force) message = "Could not reach GitHub"
                return@launch
            }
            settings.putString("updateChecked", System.currentTimeMillis().toString())
            settings.putString("updateVersion", latest.version)
            settings.putString("updatePage", latest.pageUrl)
            update = latest.takeIf { Updates.isNewer(it.version, app.zen.android.BuildConfig.VERSION_NAME) }
            if (force && update == null) message = "Zen is up to date"
        }
    }

    fun space(uuid: String?): Space? = allSpaces().firstOrNull { it.uuid == uuid }

    fun isDesktopMode() = sessions.isDesktopMode(activeTab)

    fun selectTab(tabId: String, tabUrl: String) {
        val now = System.currentTimeMillis()
        lastSeen[activeTab] = now
        lastSeen[tabId] = now
        val s = sessions.obtain(tabId, urls[tabId] ?: tabUrl)
        sessions.setActive(tabId)
        activeTab = tabId
        session = s
        url = urls[tabId] ?: tabUrl
        title = titles[tabId] ?: knownTab(tabId)?.title ?: UrlInput.display(url)
        recentIds.remove(tabId)
        recentIds.addFirst(tabId)
        if (!isPrivateTab(tabId)) settings.putString("recentTabs", recentIds.filter { !isPrivateTab(it) }.take(20).joinToString(","))
        canGoBack = backHistory[tabId] == true
        canGoForward = forwardHistory[tabId] == true
        reloading = false
        loading = tabId in loadingTabs
        chromeVisible = true
        lastScrollY = 0
        endFind()
        if (!isPrivateTab(tabId)) {
            lastNormalTab = TabItem(tabId, url, titles[tabId] ?: url, false)
            // Temporary tabs show in every space, so only a synced tab says which space it belongs to.
            if (!isLocalTab(tabId)) repository.view.value.spaces().firstOrNull { sp -> sp.tabs.any { it.id == tabId } }?.let { activeSpace = it.uuid }
        }
        requestCapture()
    }

    fun selectTab(tab: TabItem) = selectTab(tab.id, tab.url)

    val hasPage get() = activeTab != NO_TAB

    private fun showNothing() {
        lastSeen[activeTab] = System.currentTimeMillis()
        sessions.setActive(NO_TAB)
        activeTab = NO_TAB
        session = null
        url = ""
        title = ""
        canGoBack = false
        canGoForward = false
        loading = false
        progress = 100
        chromeVisible = true
        endFind()
    }

    private fun knownTab(id: String): TabItem? =
        privateTabs.firstOrNull { it.id == id } ?: localTabs.value.firstOrNull { it.id == id } ?: repository.view.value.tab(id)

    /** Most recently used tabs of the current mode, newest first, for the long-press switcher. */
    fun recentTabs(limit: Int = 6): List<TabItem> = recentIds
        .asSequence()
        .filter { it != activeTab && isPrivateTab(it) == privateMode }
        .mapNotNull { id -> knownTab(id)?.let { it.copy(title = titles[id] ?: it.title, url = urls[id] ?: it.url) } }
        .take(limit)
        .toList()

    fun openLauncher() {
        clearSuggestions()
        launcherOpen = true
        launcherFromBack = false
        suggest("")
    }

    /**
     * Back pressed on a page with no history: the home search shows instead of the app closing. It opens
     * without the keyboard and lets the next back leave the app, so back never loops between page and search.
     */
    var launcherFromBack by mutableStateOf(false)
        private set

    fun openHomeFromBack() {
        openLauncher()
        launcherFromBack = true
    }

    fun searchFieldFocused() {
        launcherFromBack = false
    }

    private var recentSearches = RecentSearches.decode(settings.string("recentSearches"))

    private fun rememberSearch(input: String) {
        if (privateMode || !settings.state.value.rememberHistory) return
        if (!UrlInput.isSearch(input, settings.state.value.searchEngine)) return
        recentSearches = RecentSearches.add(recentSearches, input)
        settings.putString("recentSearches", RecentSearches.encode(recentSearches))
    }

    /** What the search shows before anything is typed: recent searches, then recently visited pages. Nothing in private. */
    private fun startSuggestions(): List<Suggestion> {
        if (privateMode) return emptyList()
        val searches = recentSearches.take(3).map { Suggestion.Search(it, recent = true) }
        val pages = if (settings.state.value.historyInSuggestions) {
            // Result pages repeat the recent searches above them, so only real pages are listed.
            val results = settings.state.value.searchEngine.search("")
            visits.browse("", null, HistorySort.LAST_VISITED)
                .filter { it.url != url && !it.url.startsWith(results) }
                .take(4)
                .map { Suggestion.Visited(it) }
        } else {
            emptyList()
        }
        return DismissedSuggestions.without(searches + pages, dismissed) { it.dismissKey }
    }

    fun closeLauncher() {
        if (!launcherOpen) return
        clearSuggestions()
        launcherOpen = false
        launcherFromBack = false
        if (!hasPage) showFallback()
    }

    fun showFallback() {
        val current = space(activeSpace)
        val spaceTabs = current?.tabs.orEmpty()
        val recent = recentTabs(1).map { it.id }
        val temporary = current?.regularTabs.orEmpty().map { it.id }
        when (val next = EmptySelection.afterSearch(privateMode, privateTabs.map { it.id }, spaceTabs.map { it.id }, recent, temporary)) {
            is Dismissal.Select -> knownTab(next.tabId)?.let(::selectTab)
            Dismissal.LeavePrivate -> togglePrivateMode()
            Dismissal.Blank -> selectTab(SCRATCH_TAB, home)
        }
    }

    /** Text typed in the launcher opens in a new tab of the current space, like desktop's new-tab search. */
    fun launch(input: String) {
        if (input.isBlank()) return closeLauncher()
        rememberSearch(input)
        clearSuggestions()
        launcherOpen = false
        newTab(UrlInput.resolve(input, settings.state.value.searchEngine))
    }

    fun pick(suggestion: Suggestion, inNewTab: Boolean) {
        clearSuggestions()
        when (suggestion) {
            is Suggestion.OpenTab -> selectTab(suggestion.tab)
            is Suggestion.Visited -> if (inNewTab) newTab(suggestion.visit.url) else navigate(suggestion.visit.url)
            is Suggestion.Search -> if (inNewTab) launch(suggestion.text) else navigate(suggestion.text)
        }
        launcherOpen = false
    }
    fun hasNeighbor(step: Int): Boolean {
        val tabs = if (privateMode) privateTabs.toList() else space(activeSpace)?.tabs ?: return false
        val index = tabs.indexOfFirst { it.id == activeTab }
        return index >= 0 && tabs.getOrNull(index + step) != null
    }

    /** The tab a swipe of [step] would land on, with its live title and address. */
    fun neighbor(step: Int): TabItem? {
        val tabs = if (privateMode) privateTabs.toList() else space(activeSpace)?.tabs ?: return null
        val index = tabs.indexOfFirst { it.id == activeTab }
        if (index < 0) return null
        return tabs.getOrNull(index + step)?.let { it.copy(title = titles[it.id] ?: it.title, url = urls[it.id] ?: it.url) }
    }

    /** Swiping the address bar walks the visible tab list in sidebar order. */
    fun switchTab(step: Int): Boolean {
        val tabs = if (privateMode) privateTabs.toList() else space(activeSpace)?.tabs ?: return false
        val index = tabs.indexOfFirst { it.id == activeTab }
        val target = tabs.getOrNull(if (index < 0) 0 else index + step) ?: return false
        selectTab(target)
        return true
    }

    fun navigate(input: String) {
        val target = UrlInput.resolve(input, settings.state.value.searchEngine)
        rememberSearch(input)
        if (!hasPage) return newTab(target)
        session?.loadUri(target) ?: selectTab(activeTab, target)
        suggestions = emptyList()
    }

    /** Pull-to-refresh: always reloads, even while a load is still running. */
    /** True for a short while after a reload: the bar swaps the favicon for a spinner even when the icon is known. */
    var reloading by mutableStateOf(false)
        private set
    private var reloadFeedback: Job? = null

    private fun startReload() {
        session?.reload()
        reloading = true
        reloadFeedback?.cancel()
        reloadFeedback = viewModelScope.launch {
            delay(RELOAD_FEEDBACK_MILLIS)
            reloading = false
        }
    }

    fun refresh() = startReload()

    fun reload() {
        if (!loading) return startReload()
        session?.stop()
        loadingTabs.remove(activeTab)
        loading = false
        progress = 100
    }

    fun goForward() = session?.goForward()

    fun toggleDesktopMode() = sessions.toggleDesktopMode(activeTab)

    fun newTab(target: String = home) {
        if (privateMode) return newPrivateTab(target)
        val space = activeSpace
        if (space == null) {
            sessions.close(SCRATCH_TAB)
            urls.remove(SCRATCH_TAB)
            selectTab(SCRATCH_TAB, target)
            return
        }
        val id = SpacesState.newTabId()
        setLocalTabs(localTabs.value + TabItem(id, target, UrlInput.display(target), pinned = false))
        selectTab(id, target)
    }

    fun newPrivateTab(target: String = home) {
        if (!privateMode) togglePrivateMode()
        val tab = TabItem(PRIVATE_PREFIX + UUID.randomUUID(), target, UrlInput.display(target), false)
        privateTabs += tab
        selectTab(tab)
    }

    fun togglePrivateMode() {
        if (privateMode) {
            privateMode = false
            applyThemeMode(settings.state.value.themeMode)
            val back = lastNormalTab ?: space(activeSpace)?.tabs?.firstOrNull()
            if (back != null) selectTab(back) else selectTab(SCRATCH_TAB, home)
        } else {
            privateMode = true
            // Private browsing always renders sites dark, which also tells it apart from normal mode at a glance.
            Gecko.setColorScheme(getApplication(), GeckoRuntimeSettings.COLOR_SCHEME_DARK)
            showNothing()
        }
    }

    private var privateRequested = false

    fun requestPrivateBrowsing() {
        if (ready) startPrivateBrowsing() else privateRequested = true
    }

    /** Entry point of the "New private tab" launcher shortcut. */
    fun startPrivateBrowsing() {
        if (!privateMode) togglePrivateMode()
        openLauncher()
    }

    fun closePrivateTabs() {
        privateTabs.forEach { forget(it.id) }
        privateTabs.clear()
        if (privateMode) togglePrivateMode()
    }

    fun closeTab(tabId: String) = closeTabs(listOf(tabId))

    /** Temporary tabs are one list shared by every space, so clearing from any space clears them all. */
    fun clearRegularTabs() {
        closeTabs(localTabs.value.map { it.id })
    }

    private fun forget(tabId: String) {
        loadingTabs -= tabId
        playingTabs -= tabId
        sessions.discard(tabId)
        urls.remove(tabId)
        titles.remove(tabId)
        backHistory.remove(tabId)
        forwardHistory.remove(tabId)
    }

    private fun closeTabs(ids: List<String>) {
        if (ids.isEmpty()) return
        ids.forEach(::forget)
        val privateIds = ids.filter(::isPrivateTab).toSet()
        if (privateIds.isNotEmpty()) {
            privateTabs.removeAll { it.id in privateIds }
            if (activeTab in privateIds) {
                privateTabs.lastOrNull()?.let(::selectTab) ?: togglePrivateMode()
            }
        }
        val local = ids.filter(::isLocalTab).toSet()
        if (local.isNotEmpty()) setLocalTabs(localTabs.value.filter { it.id !in local })
        val synced = ids.filter { !isLocalOnly(it) && it !in local }
        viewModelScope.launch {
            synced.forEach { repository.edit(LocalEdit.CloseTab(it)) }
            if (activeTab in ids && !isPrivateTab(activeTab)) {
                val recent = if (settings.state.value.selectRecentOnClose) recentTabs(1).firstOrNull() else null
                val next = recent ?: space(activeSpace)?.tabs?.firstOrNull()
                if (next != null) selectTab(next) else selectTab(SCRATCH_TAB, home)
            }
            if (synced.isNotEmpty()) repository.sync()
        }
    }

    /** Routes a drag: phone tabs reorder locally, crossing the separator pins (syncs) or unpins (un-syncs) a tab. */
    fun move(edit: LocalEdit.MoveItem) {
        // The server decides: a tab it holds as pinned is synced, whatever the phone's list still remembers.
        val synced = repository.view.value.tab(edit.itemId)?.takeIf { it.pinned }
        val local = if (synced == null) localTabs.value.firstOrNull { it.id == edit.itemId } else null
        when {
            local != null && edit.pinned == true -> pinIntoSpace(local, edit)
            local != null -> setLocalTabs(LocalTabs.insert(localTabs.value, local, edit.beforeId))
            synced != null && edit.pinned == false -> unpinToPhone(synced, edit.beforeId)
            else -> edit(edit)
        }
    }

    private fun pinIntoSpace(tab: TabItem, move: LocalEdit.MoveItem) {
        val space = spaceHolding(move.parentId) ?: activeSpace ?: return
        val live = TabItem(tab.id, urls[tab.id] ?: tab.url, titles[tab.id] ?: tab.title, pinned = false)
        setLocalTabs(localTabs.value.filter { it.id != tab.id })
        viewModelScope.launch {
            repository.edit(LocalEdit.OpenTab(live.id, space, live.url, live.title))
            repository.edit(move.copy(pinned = true))
            repository.sync()
        }
    }

    private fun unpinToPhone(tab: TabItem, beforeId: String?) {
        val live = TabItem(tab.id, urls[tab.id] ?: tab.url, titles[tab.id] ?: tab.title, pinned = false)
        viewModelScope.launch {
            repository.edit(LocalEdit.CloseTab(tab.id))
            setLocalTabs(LocalTabs.insert(localTabs.value, live, beforeId))
            repository.sync()
        }
    }

    /** The synced space that is [id] or holds the folder [id]. */
    private fun spaceHolding(id: String): String? {
        fun holds(items: List<SidebarItem>): Boolean = items.any { it.id == id || (it is FolderItem && holds(it.items)) }
        return repository.view.value.spaces().firstOrNull { it.uuid == id || holds(it.items) }?.uuid
    }

    private fun edit(edit: LocalEdit) = viewModelScope.launch {
        repository.edit(edit)
        repository.sync()
    }

    fun renameSpace(uuid: String, name: String) = edit(LocalEdit.RenameSpace(uuid, name))

    fun renameFolder(folderId: String, name: String) = edit(LocalEdit.RenameFolder(folderId, name))

    /** Pinned tabs go last in the current space's pinned section; unpinned ones go first among the temporary tabs, like desktop. */
    fun setPinned(tab: TabItem, pinned: Boolean, spaceUuid: String) {
        val synced = repository.view.value.tab(tab.id)?.pinned == true
        when {
            pinned && !synced -> pinIntoSpace(tab, LocalEdit.MoveItem(tab.id, spaceUuid, null, true))
            !pinned && synced -> unpinToPhone(tab, localTabs.value.firstOrNull { it.id != tab.id }?.id)
        }
    }

    fun essentialsFull() = essentials.value.size >= MAX_ESSENTIALS

    /** Moves a synced tab into this phone's essentials (it closes in the space, as desktop does). */
    fun addEssential(tab: TabItem) {
        if (essentialsFull()) {
            message = "Essentials are full ($MAX_ESSENTIALS)"
            return
        }
        val essential = TabItem(ESSENTIAL_PREFIX + UUID.randomUUID(), urls[tab.id] ?: tab.url, titles[tab.id] ?: tab.title, pinned = true)
        settings.setEssentials(essentials.value + essential)
        val wasActive = activeTab == tab.id
        closeTabs(listOf(tab.id))
        if (wasActive) selectTab(essential)
    }

    fun removeEssential(id: String) {
        settings.setEssentials(essentials.value.filter { it.id != id })
        forget(id)
    }

    /** Drops an essential back into a space as a new synced tab. */
    fun essentialToSpace(essential: TabItem, spaceUuid: String) {
        val wasActive = activeTab == essential.id
        val url = urls[essential.id] ?: essential.url
        val title = titles[essential.id] ?: essential.title
        removeEssential(essential.id)
        val id = SpacesState.newTabId()
        setLocalTabs(localTabs.value + TabItem(id, url, title, pinned = false))
        if (wasActive) selectTab(id, url)
    }

    fun historyEntries(query: String, since: Long?, sort: app.zen.android.browser.HistorySort) = visits.browse(query, since, sort)

    fun forgetVisit(url: String) {
        visits.forget(url)
        persistVisits()
    }

    fun clearHistorySince(since: Long?) {
        if (since == null) visits.clear() else visits.clearSince(since)
        persistVisits()
    }

    fun goBack(): Boolean {
        if (backHistory[activeTab] != true) return false
        session?.goBack()
        return true
    }

    fun openGlance(target: String) {
        val id = (if (isPrivateTab(activeTab)) PRIVATE_PREFIX else "") + GLANCE_TAB
        sessions.close(id)
        glanceUrl = target
        glance = sessions.obtain(id, target)
    }

    fun closeGlance() {
        sessions.close(PRIVATE_PREFIX + GLANCE_TAB)
        sessions.close(GLANCE_TAB)
        glance = null
    }

    fun expandGlance() {
        val target = glanceUrl
        closeGlance()
        newTab(target)
    }

    fun startFind() {
        findQuery = ""
        findResult = ""
    }

    fun find(text: String, backwards: Boolean = false) {
        val finder = session?.finder ?: return
        val changed = text != findQuery
        findQuery = text
        if (text.isEmpty()) {
            finder.clear()
            findResult = ""
            return
        }
        val flags = if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else 0
        finder.find(if (changed) text else null, flags).accept { r: FinderResult? ->
            findResult = if (r == null || !r.found) "0/0" else "${r.current}/${r.total}"
        }
    }

    fun endFind() {
        if (findQuery == null) return
        session?.finder?.clear()
        findQuery = null
        findResult = ""
    }

    /** Results appear only once something is typed, like desktop's new-tab search. */
    fun suggest(text: String) {
        suggestJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) {
            suggestions = startSuggestions()
            return
        }
        if (query == url) {
            suggestions = emptyList()
            return
        }
        val prefs = settings.state.value
        val candidates = when {
            !prefs.openTabsInSuggestions -> emptyList()
            privateMode -> privateTabs.toList()
            else -> allSpaces().flatMap { it.tabs } + essentials.value
        }
        val openTabs = candidates
            .filter { it.id != activeTab && (it.title.contains(query, true) || it.url.contains(query, true)) }
            .distinctBy { it.id }
            .take(3)
            .map { Suggestion.OpenTab(it) }
        val openUrls = openTabs.map { it.tab.url }.toSet()
        val visited = if (privateMode || !prefs.historyInSuggestions) emptyList() else visits.search(query, 4).filter { it.url !in openUrls }.map { Suggestion.Visited(it) }
        suggestions = DismissedSuggestions.without(openTabs + visited, dismissed) { it.dismissKey }
        if (!prefs.searchSuggestions || (privateMode && !prefs.suggestionsInPrivate)) return
        suggestJob = viewModelScope.launch {
            delay(120)
            val remote = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(settings.state.value.searchEngine.suggestUrl(query)).openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    try {
                        UrlInput.parseSuggestions(conn.inputStream.bufferedReader().use { it.readText() }, limit = 4)
                    } finally {
                        conn.disconnect()
                    }
                }.getOrDefault(emptyList())
            }
            suggestions = DismissedSuggestions.without(openTabs + visited + remote.map { Suggestion.Search(it) }, dismissed) { it.dismissKey }
        }
    }

    private var dismissed = RecentSearches.decode(settings.string("dismissedSuggestions"))

    /** Swiped away in the search: a recent search is forgotten, anything else is never suggested again. */
    fun dismissSuggestion(suggestion: Suggestion) {
        if (suggestion is Suggestion.Search && suggestion.recent) {
            recentSearches = recentSearches.filterNot { it.equals(suggestion.text, ignoreCase = true) }
            settings.putString("recentSearches", RecentSearches.encode(recentSearches))
        } else {
            dismissed = DismissedSuggestions.add(dismissed, suggestion.dismissKey)
            settings.putString("dismissedSuggestions", RecentSearches.encode(dismissed))
        }
        suggestions = suggestions - suggestion
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        suggestions = emptyList()
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
        accountEmail = null
        settings.putString("accountEmail", null)
        activeSpace = null
        sessions.discardAll { !isPrivateTab(it) }
        selectTab(SCRATCH_TAB, home)
    }

    fun applyThemeMode(mode: ThemeMode) {
        val scheme = when (mode) {
            ThemeMode.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
            ThemeMode.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
            ThemeMode.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        }
        Gecko.setColorScheme(getApplication(), scheme)
        // Android 12+ stores the app's own night mode, so the splash screen matches the chosen theme on the next launch.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val ui = getApplication<Application>().getSystemService(android.app.UiModeManager::class.java)
            ui?.setApplicationNightMode(
                when (mode) {
                    ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
                }
            )
        }
    }

    fun clearBrowsingData() {
        visits.clear()
        persistVisits()
        Gecko.clearData(getApplication()).accept { message = "Browsing data cleared" }
    }

    private fun persistVisits() {
        saveVisits?.cancel()
        saveVisits = viewModelScope.launch(Dispatchers.IO) {
            delay(1500)
            visits.save()
        }
    }

    fun clearSitePermissions() {
        val storage = Gecko.runtime(getApplication()).storageController
        storage.getAllPermissions().accept { perms ->
            perms?.forEach { storage.setPermission(it, GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT) }
            message = "Site permissions reset"
        }
    }

    fun requestCapture() {
        captureRequest++
    }

    fun exitFullScreen() {
        session?.exitFullScreen()
    }

    private var incoming: String? = null

    /** A link or text another app handed us: opened as a new tab in the current space, like desktop's external links. */
    fun openIncoming(text: String) {
        if (!ready) {
            incoming = text
            return
        }
        val target = UrlInput.resolveIncoming(text, settings.state.value.searchEngine)
        clearSuggestions()
        launcherOpen = false
        if (privateMode) newPrivateTab(target) else newTab(target)
    }

    override fun interceptNavigation(tabId: String, url: String): Boolean {
        if (tabId != LOGIN_TAB || !auth.isRedirect(url)) return false
        val pending = pendingLogin ?: return true
        viewModelScope.launch {
            try {
                val credentials = withContext(Dispatchers.IO) { auth.completeLogin(pending, url) }
                repository.signIn(credentials)
                refreshEmail()
                repository.view.value.spaces().firstOrNull()?.let { activeSpace = it.uuid }
            } catch (e: Exception) {
                message = "Sign-in failed: ${e.message}"
            } finally {
                cancelLogin()
            }
        }
        return true
    }

    override fun onLocation(tabId: String, url: String) {
        if (tabId == LOGIN_TAB) return
        if (tabId.endsWith(GLANCE_TAB)) {
            glanceUrl = url
            return
        }
        if (UrlInput.isPlaceholder(url)) return
        urls[tabId] = url
        if (tabId == activeTab) this.url = url
        updatePrivateTab(tabId) { it.copy(url = url) }
    }

    override fun onTitle(tabId: String, title: String) {
        if (tabId == LOGIN_TAB || tabId.endsWith(GLANCE_TAB)) return
        titles[tabId] = title
        if (tabId == activeTab) this.title = title
        updatePrivateTab(tabId) { it.copy(title = title.ifBlank { it.url }) }
    }

    private fun updatePrivateTab(tabId: String, change: (TabItem) -> TabItem) {
        if (!isPrivateTab(tabId)) return
        val i = privateTabs.indexOfFirst { it.id == tabId }
        if (i >= 0) privateTabs[i] = change(privateTabs[i])
    }

    override fun onPageStart(tabId: String) {
        loadingTabs += tabId
        if (tabId == activeTab) {
            loading = true
            chromeVisible = true
            lastScrollY = 0
        }
    }

    override fun onPageLoaded(tabId: String) {
        loadingTabs -= tabId
        if (tabId == activeTab) {
            loading = false
            progress = 100
            reloading = false
        }
        if (tabId == activeTab) requestCapture()
        if (!isPrivateTab(tabId) && tabId != LOGIN_TAB && !tabId.endsWith(GLANCE_TAB)) {
            if (settings.state.value.rememberHistory) urls[tabId]?.let { visits.record(it, titles[tabId].orEmpty()) }
            persistVisits()
        }
        if (isLocalOnly(tabId)) return
        val u = urls[tabId]?.takeUnless(UrlInput::isPlaceholder) ?: return
        localTabs.value.firstOrNull { it.id == tabId }?.let { tab ->
            val title = titles[tabId] ?: tab.title
            if (tab.url != u || tab.title != title) setLocalTabs(localTabs.value.map { if (it.id == tabId) it.copy(url = u, title = title) else it })
            return
        }
        val synced = repository.view.value.tab(tabId) ?: return
        val title = titles[tabId] ?: synced.title
        if (synced.url == u && synced.title == title) return
        viewModelScope.launch { repository.edit(LocalEdit.UpdateTab(tabId, u, title)) }
    }

    override fun onCanGoBack(tabId: String, canGoBack: Boolean) {
        backHistory[tabId] = canGoBack
        if (tabId == activeTab) this.canGoBack = canGoBack
    }

    override fun onCanGoForward(tabId: String, canGoForward: Boolean) {
        forwardHistory[tabId] = canGoForward
        if (tabId == activeTab) this.canGoForward = canGoForward
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
        scrollSettle?.cancel()
        scrollSettle = viewModelScope.launch {
            delay(250)
            requestCapture()
        }
    }

    override fun onProgress(tabId: String, progress: Int) {
        if (tabId == activeTab) this.progress = progress
    }

    override fun onSecurity(tabId: String, secure: Boolean) {
        if (tabId == activeTab) this.secure = secure
    }

    override fun onOpenInNewTab(fromTabId: String, url: String) {
        if (isPrivateTab(fromTabId)) newPrivateTab(url) else newTab(url)
    }

    override fun onContextMenu(tabId: String, element: ContextElement) {
        if (tabId == activeTab && (element.linkUri != null || element.srcUri != null)) contextMenu = element
    }

    private var lastPageTouch = 0L
    private var lastRotation = 0L

    fun notePageTouch() {
        lastPageTouch = System.currentTimeMillis()
    }

    fun noteRotation() {
        lastRotation = System.currentTimeMillis()
    }

    private val playingTabs = mutableSetOf<String>()

    override fun onMediaPlaying(tabId: String, playing: Boolean) {
        if (playing) playingTabs += tabId else playingTabs -= tabId
    }

    private val videoRotated = mutableMapOf<String, Long>()

    override fun onVideoRotated(tabId: String) {
        videoRotated[tabId] = System.currentTimeMillis()
    }

    override fun onFullScreen(tabId: String, fullScreen: Boolean) {
        val now = System.currentTimeMillis()
        if (fullScreen && !FullScreenGate.allowed(now, lastPageTouch, lastRotation, tabId in playingTabs, videoRotated[tabId] ?: 0)) {
            sessions.peek(tabId)?.exitFullScreen()
            return
        }
        if (tabId == activeTab) this.fullScreen = fullScreen
    }

    private val _downloads = MutableStateFlow(Downloads.afterRestart(Downloads.decode(settings.string("downloads"))))
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private fun putDownload(item: DownloadItem) {
        _downloads.value = Downloads.upsert(_downloads.value, item)
        settings.putString("downloads", Downloads.encode(_downloads.value))
    }

    fun removeDownload(item: DownloadItem, deleteFile: Boolean) {
        if (deleteFile) item.uri?.let { uri -> runCatching { getApplication<Application>().contentResolver.delete(Uri.parse(uri), null, null) } }
        _downloads.value = _downloads.value.filter { it.id != item.id }
        settings.putString("downloads", Downloads.encode(_downloads.value))
    }

    /**
     * Saves the response Gecko already opened, rather than asking Android to
     * fetch the address again: the page's cookies and session come along, so
     * files behind a sign-in (and blob: or data: links) download too.
     */
    override fun onDownload(tabId: String, response: WebResponse) {
        val app = getApplication<Application>()
        val mime = response.headers["Content-Type"]?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
        val name = URLUtil.guessFileName(response.uri, response.headers["Content-Disposition"], mime)
        val total = response.headers["Content-Length"]?.toLongOrNull() ?: 0L
        val body = response.body
        val start = DownloadItem(UUID.randomUUID().toString(), name, mime, null, total, 0, DownloadStatus.RUNNING, System.currentTimeMillis(), response.uri)
        if (body == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runCatching { body?.close() }
            putDownload(start.copy(status = DownloadStatus.FAILED))
            message = "Download failed"
            return
        }
        putDownload(start)
        message = "Downloading $name"
        // A link that opens straight onto a file leaves an empty tab behind; desktop closes it, and so do we.
        if (backHistory[tabId] != true && (urls[tabId] ?: "").let { it.isEmpty() || it == response.uri || UrlInput.isPlaceholder(it) }) closeTab(tabId)
        viewModelScope.launch {
            val resolver = app.contentResolver
            val done = withContext(Dispatchers.IO) {
                var uri: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        mime?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("no row")
                    var received = 0L
                    var reported = 0L
                    body.use { input ->
                        resolver.openOutputStream(uri)!!.use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                received += n
                                if (received - reported > 256 * 1024) {
                                    reported = received
                                    val now = start.copy(uri = uri.toString(), received = received)
                                    withContext(Dispatchers.Main) { putDownload(now) }
                                }
                            }
                        }
                    }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    // Android renames a file whose name is taken ("report (1).pdf"); show the name it really has.
                    val saved = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: name
                    start.copy(name = saved, uri = uri.toString(), received = received, total = maxOf(total, received), status = DownloadStatus.DONE)
                } catch (e: Exception) {
                    uri?.let { runCatching { resolver.delete(it, null, null) } }
                    start.copy(status = DownloadStatus.FAILED)
                }
            }
            putDownload(done)
            message = if (done.status == DownloadStatus.DONE) "Downloaded ${done.name}" else "Download failed: ${done.name}"
        }
    }

    override fun onCleared() {
        stopSyncLoop()
    }
}
