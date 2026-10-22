package com.durpalla.driver.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Logged-in driver: Sanctum bearer token + driver id (for `private-driver.{id}`). Never logged. */
data class Session(val token: String, val driverId: String) {
    override fun toString() = "Session(driverId=$driverId, token=***)"
}

interface TokenStore {
    suspend fun load(): Session?
    suspend fun save(session: Session?)
}

private val Context.sessionStore by preferencesDataStore("session")

/** App-private DataStore (not backed up: see data_extraction_rules / backup rules). */
class DataStoreTokenStore(private val ctx: Context) : TokenStore {
    private val token = stringPreferencesKey("token")
    private val driver = stringPreferencesKey("driver_id")
    override suspend fun load(): Session? = ctx.sessionStore.data.first().let { p ->
        val t = p[token] ?: return null; Session(t, p[driver].orEmpty())
    }
    override suspend fun save(session: Session?) {
        ctx.sessionStore.edit { p -> if (session == null) p.clear() else { p[token] = session.token; p[driver] = session.driverId } }
    }
}

class InMemoryTokenStore(private var s: Session? = null) : TokenStore {
    override suspend fun load() = s
    override suspend fun save(session: Session?) { s = session }
}

/** Single source of truth for auth. A 401 from the API calls [expire], which logs the driver out everywhere. */
class SessionStore(private val store: TokenStore, private val scope: CoroutineScope) {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the server rejected the token (401): UI navigates to login. */
    val expired: SharedFlow<Unit> = _expired

    val token: String? get() = _session.value?.token

    suspend fun restore(): Session? = store.load()?.also { _session.value = it }
    suspend fun login(s: Session) { _session.value = s; store.save(s) }
    suspend fun logout() { _session.value = null; store.save(null) }

    fun expire() {
        if (_session.value == null) return
        _session.value = null
        scope.launch { store.save(null) }
        _expired.tryEmit(Unit)
    }
}
