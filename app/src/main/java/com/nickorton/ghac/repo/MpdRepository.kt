package com.nickorton.ghac.repo

import android.util.Log
import com.nickorton.ghac.data.ConnectionState
import com.nickorton.ghac.data.mpd.DirEntry
import com.nickorton.ghac.data.mpd.MpdClient
import com.nickorton.ghac.data.mpd.PlayState
import com.nickorton.ghac.data.mpd.PlayerState
import com.nickorton.ghac.data.mpd.PlaylistEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the MPD connection and exposes player and queue state as flows.
 *
 * The Bubble Tea original drove this with a goroutine that emitted `tea.Msg`
 * values into the update loop and re-subscribed after each one. The direct
 * translation is a coroutine that parks on `idle`, writes to a [StateFlow], and
 * loops — with the addition of reconnect, which the desktop version did not
 * need.
 */
class MpdRepository(private val scope: CoroutineScope) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _playlist = MutableStateFlow<List<PlaylistEntry>>(emptyList())
    val playlist: StateFlow<List<PlaylistEntry>> = _playlist.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.NotConfigured)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    @Volatile
    private var client: MpdClient? = null
    private var sessionJob: Job? = null

    init {
        startProgressTicker()
    }

    /**
     * Advances the elapsed time locally once a second while playing, so the
     * progress bar moves smoothly between MPD's own updates. This is the
     * counterpart of the TUI's `MsgTick`.
     */
    private fun startProgressTicker() {
        scope.launch {
            while (isActive) {
                delay(1.seconds)
                _playerState.update { state ->
                    if (state.isPlaying && state.elapsed < state.total) {
                        state.copy(elapsed = state.elapsed + 1.seconds)
                    } else {
                        state
                    }
                }
            }
        }
    }

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

                val connected = MpdClient.connect(host, port)
                client = connected
                _connection.value = ConnectionState.Connected
                backoff = INITIAL_BACKOFF

                refresh(connected)
                watchForChanges(connected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "MPD session ended: ${e.message}")
                _connection.value = ConnectionState.Reconnecting(e.message ?: "connection lost")
            } finally {
                closeClient()
            }

            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
        }
    }

    /** Parks on `idle`, refreshing only the state the server says has changed. */
    private suspend fun watchForChanges(client: MpdClient) {
        while (currentCoroutineContext().isActive) {
            val changed = client.waitForChanges()
            if (PLAYLIST in changed) {
                _playlist.value = client.playlistInfo()
            }
            // `options` carries the random flag, which lives in player state.
            if (PLAYER in changed || OPTIONS in changed) {
                _playerState.value = client.playerState()
            }
        }
    }

    private suspend fun refresh(client: MpdClient) {
        _playerState.value = client.playerState()
        _playlist.value = client.playlistInfo()
    }

    private fun closeClient() {
        client?.close()
        client = null
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /**
     * Runs a command, discarding failures.
     *
     * This matches the Go version's error strategy (`docs/architecture.md`
     * §11): the server is the source of truth, and a failed command simply
     * means the idle notification that would have confirmed it never arrives,
     * leaving the displayed state correct.
     */
    private fun command(name: String, block: suspend (MpdClient) -> Unit) {
        val active = client ?: return
        scope.launch {
            runCatching { block(active) }
                .onFailure { Log.w(TAG, "MPD $name failed: ${it.message}") }
        }
    }

    fun togglePlayPause() {
        val playing = _playerState.value.state == PlayState.PLAY
        command("play/pause") { if (playing) it.pause() else it.play() }
    }

    fun toggleRandom() {
        val next = !_playerState.value.random
        command("random") { it.setRandom(next) }
    }

    fun playAt(pos: Int) = command("play") { it.play(pos) }

    fun removeAt(pos: Int) = command("delete") { it.delete(pos) }

    fun removeAll(positions: Collection<Int>) = command("delete") { it.delete(positions) }

    fun clearQueue() = command("clear") { it.clear() }

    fun move(from: Int, to: Int) = command("move") { it.move(from, to) }

    fun enqueue(uris: List<String>) = command("add") { client ->
        uris.forEach { client.add(it) }
    }

    /**
     * Lists a library directory. Unlike the other commands this returns its
     * result, because the navigator needs the listing synchronously to render
     * the folder the user just opened.
     */
    suspend fun listDirectory(path: String): List<DirEntry> {
        val active = client ?: return emptyList()
        return runCatching { active.lsInfo(path) }
            .onFailure { Log.w(TAG, "MPD lsinfo failed: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "MpdRepository"
        const val PLAYER = "player"
        const val PLAYLIST = "playlist"
        const val OPTIONS = "options"

        val INITIAL_BACKOFF: Duration = 1.seconds
        val MAX_BACKOFF: Duration = 30.seconds
    }
}
