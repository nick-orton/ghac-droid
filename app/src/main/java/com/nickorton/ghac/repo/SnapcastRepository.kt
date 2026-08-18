package com.nickorton.ghac.repo

import android.util.Log
import com.nickorton.ghac.data.ConnectionState
import com.nickorton.ghac.data.snapcast.SnapClientInfo
import com.nickorton.ghac.data.snapcast.SnapcastClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the SnapCast connection and exposes the speaker list.
 *
 * Like the Go original, any notification triggers a full `Server.GetStatus`
 * re-fetch rather than an attempt to apply a delta: SnapCast's notifications
 * describe several different kinds of change, and re-reading the whole status
 * is both simpler and immune to missed messages.
 */
class SnapcastRepository(private val scope: CoroutineScope) {

    private val _clients = MutableStateFlow<List<SnapClientInfo>>(emptyList())
    val clients: StateFlow<List<SnapClientInfo>> = _clients.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    @Volatile
    private var client: SnapcastClient? = null
    private var sessionJob: Job? = null

    fun connect(host: String, port: Int) {
        sessionJob?.cancel()
        sessionJob = scope.launch { runSession(host, port) }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        closeClient()
        _connection.value = ConnectionState.NotConfigured
    }

    private suspend fun runSession(host: String, port: Int) {
        var backoff = INITIAL_BACKOFF
        while (currentCoroutineContext().isActive) {
            try {
                _connection.value = ConnectionState.Connecting

                val connected = SnapcastClient.connect(host, port)
                client = connected
                _connection.value = ConnectionState.Connected
                backoff = INITIAL_BACKOFF

                _clients.value = connected.getServerStatus()

                // The notification flow is a SharedFlow, so collecting it never
                // returns — not even when the socket dies. The collector is
                // therefore run alongside awaitClosed(), which is what actually
                // reports the disconnect and drops us into the retry path.
                coroutineScope {
                    val collector = launch {
                        connected.notifications.collect {
                            _clients.value = connected.getServerStatus()
                        }
                    }
                    val reason = connected.awaitClosed()
                    collector.cancel()
                    throw reason ?: IOException("SnapCast connection closed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "SnapCast session ended: ${e.message}")
                _connection.value = ConnectionState.Reconnecting(e.message ?: "connection lost")
            } finally {
                closeClient()
            }

            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
        }
    }

    private fun closeClient() {
        client?.close()
        client = null
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /**
     * Sets a client's volume, updating local state immediately.
     *
     * The optimistic write matters for the slider: without it the knob would
     * snap back to its old position until the server's notification completed
     * a round trip.
     */
    fun setVolume(clientId: String, percent: Int) {
        val current = _clients.value.firstOrNull { it.id == clientId } ?: return
        applyLocally(clientId) { it.copy(volume = percent) }
        send("setVolume") { it.setVolume(clientId, percent, current.muted) }
    }

    fun setMuted(clientId: String, muted: Boolean) {
        val current = _clients.value.firstOrNull { it.id == clientId } ?: return
        applyLocally(clientId) { it.copy(muted = muted) }
        send("setMuted") { it.setMuted(clientId, muted, current.volume) }
    }

    /** Mutes or unmutes every known client, the equivalent of the TUI's `M`. */
    fun setAllMuted(muted: Boolean) {
        val snapshot = _clients.value
        _clients.value = snapshot.map { it.copy(muted = muted) }
        send("setAllMuted") { client ->
            snapshot.forEach { client.setMuted(it.id, muted, it.volume) }
        }
    }

    /** Nudges every client's volume by [delta], the equivalent of `H` / `L`. */
    fun adjustAllVolumes(delta: Int) {
        val snapshot = _clients.value
        val updated = snapshot.map {
            it.copy(volume = (it.volume + delta).coerceIn(MIN_VOLUME, MAX_VOLUME))
        }
        _clients.value = updated
        send("adjustAll") { client ->
            updated.forEach { client.setVolume(it.id, it.volume, it.muted) }
        }
    }

    fun setName(clientId: String, name: String) {
        applyLocally(clientId) { it.copy(name = name) }
        send("setName") { it.setName(clientId, name) }
    }

    private fun applyLocally(clientId: String, transform: (SnapClientInfo) -> SnapClientInfo) {
        _clients.value = _clients.value.map { if (it.id == clientId) transform(it) else it }
    }

    private fun send(name: String, block: suspend (SnapcastClient) -> Unit) {
        val active = client ?: return
        scope.launch {
            runCatching { block(active) }
                .onFailure { Log.w(TAG, "SnapCast $name failed: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "SnapcastRepository"
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 100

        val INITIAL_BACKOFF: Duration = 1.seconds
        val MAX_BACKOFF: Duration = 30.seconds
    }
}
