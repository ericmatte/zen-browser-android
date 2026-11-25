package app.zen.android.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface CredentialStore {
    fun load(): SyncCredentials?
    fun save(credentials: SyncCredentials?)
}

class FileCredentialStore(private val file: File) : CredentialStore {
    override fun load(): SyncCredentials? = runCatching {
        val json = JSONObject(file.readText())
        SyncCredentials(json.getString("refresh"), json.getString("kid"), B64.fromStd(json.getString("k")))
    }.getOrNull()

    override fun save(credentials: SyncCredentials?) {
        if (credentials == null) {
            file.delete()
            return
        }
        file.writeText(
            JSONObject().put("refresh", credentials.refreshToken).put("kid", credentials.keyId)
                .put("k", B64.std(credentials.syncKey)).toString()
        )
    }
}

sealed interface SyncStatus {
    data object SignedOut : SyncStatus
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** Narrow seam over [SyncClient] so the sync loop can be tested without a network. */
interface SpacesRemote {
    fun fetch(newerThan: Double): Pair<List<Bso>, Double>
    fun upload(records: List<Pair<String, JSONObject>>)
}

class SpacesRepository(
    private val dir: File,
    private val credentialStore: CredentialStore,
    private val remoteFactory: (SyncCredentials) -> SpacesRemote = { creds ->
        val client = SyncClient(creds)
        object : SpacesRemote {
            override fun fetch(newerThan: Double) = client.fetch(SPACES_COLLECTION, newerThan)
            override fun upload(records: List<Pair<String, JSONObject>>) { client.upload(SPACES_COLLECTION, records) }
        }
    },
) {
    private val stateFile = File(dir, "spaces-state.json")
    private val mutex = Mutex()

    private var serverState = SpacesState()
    private var lastModified = 0.0
    private val pending = mutableListOf<LocalEdit>()
    private var remote: SpacesRemote? = null

    private val _view = MutableStateFlow(SpacesState())
    val view: StateFlow<SpacesState> = _view.asStateFlow()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.SignedOut)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val json = JSONObject(stateFile.readText())
                serverState = SpacesState.fromJson(json.getJSONObject("records"))
                lastModified = json.optDouble("lastModified", 0.0)
                pending.clear()
                json.optJSONArray("pending")?.let { arr ->
                    (0 until arr.length()).forEach { pending += LocalEdit.fromJson(arr.getJSONObject(it)) }
                }
            }
            publish()
            credentialStore.load()?.let {
                remote = remoteFactory(it)
                _status.value = SyncStatus.Idle
            }
        }
    }

    suspend fun signIn(credentials: SyncCredentials) = withContext(Dispatchers.IO) {
        mutex.withLock {
            credentialStore.save(credentials)
            remote = remoteFactory(credentials)
            serverState = SpacesState()
            lastModified = 0.0
            _status.value = SyncStatus.Idle
            persist()
        }
        sync()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        mutex.withLock {
            credentialStore.save(null)
            remote = null
            serverState = SpacesState()
            lastModified = 0.0
            pending.clear()
            stateFile.delete()
            publish()
            _status.value = SyncStatus.SignedOut
        }
    }

    suspend fun edit(edit: LocalEdit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            pending += edit
            publish()
            persist()
        }
    }

    suspend fun sync() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = remote ?: return@withLock
            _status.value = SyncStatus.Syncing
            try {
                val (incoming, modified) = r.fetch(lastModified)
                serverState = serverState.withIncoming(incoming)
                lastModified = modified
                val (merged, changes) = serverState.apply(pending)
                if (changes.isNotEmpty()) {
                    r.upload(changes.map { (id, cleartext) -> id to (cleartext ?: tombstone(id)) })
                }
                serverState = merged
                pending.clear()
                publish()
                persist()
                _status.value = SyncStatus.Idle
            } catch (e: Exception) {
                _status.value = SyncStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun publish() {
        _view.value = serverState.apply(pending).first
    }

    private fun persist() {
        dir.mkdirs()
        val json = JSONObject()
            .put("records", serverState.toJson())
            .put("lastModified", lastModified)
            .put("pending", JSONArray().also { arr -> pending.forEach { arr.put(it.toJson()) } })
        val tmp = File(dir, "spaces-state.json.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(stateFile)
    }

    private fun tombstone(id: String) = JSONObject().put("id", id).put("deleted", true)
}
