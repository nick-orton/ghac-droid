package com.nickorton.ghac.ui.volume

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.ghac.data.snapcast.SnapClientInfo
import com.nickorton.ghac.repo.SnapcastRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Player Volume screen.
 *
 * Most of this class exists to stop a slider fighting the server. Dragging
 * emits a continuous stream of values; each one sent to SnapCast provokes a
 * broadcast notification, which makes the repository re-read the full server
 * status, which would then rewrite the slider position under the user's thumb —
 * with whatever lag the round trip took. So while a client is being dragged its
 * displayed value comes from [pendingVolumes] rather than from the server, and
 * outbound writes are debounced.
 */
class VolumeViewModel(private val snapcast: SnapcastRepository) : ViewModel() {

    val clients: StateFlow<List<SnapClientInfo>> = snapcast.clients
    val connection = snapcast.connection

    private val _pendingVolumes = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Volumes owned by an in-progress drag; these win over the server's value. */
    val pendingVolumes: StateFlow<Map<String, Int>> = _pendingVolumes.asStateFlow()

    private val sendJobs = mutableMapOf<String, Job>()

    /** What the slider should show: the drag value if there is one. */
    fun displayedVolume(client: SnapClientInfo): Int =
        _pendingVolumes.value[client.id] ?: client.volume

    fun onVolumeChanged(clientId: String, percent: Int) {
        _pendingVolumes.update { it + (clientId to percent) }

        sendJobs[clientId]?.cancel()
        sendJobs[clientId] = viewModelScope.launch {
            delay(SEND_DEBOUNCE_MS)
            snapcast.setVolume(clientId, percent)
        }
    }

    fun onVolumeChangeFinished(clientId: String) {
        val finalValue = _pendingVolumes.value[clientId] ?: return

        sendJobs[clientId]?.cancel()
        viewModelScope.launch {
            snapcast.setVolume(clientId, finalValue)
            // Hold the drag value briefly so the slider does not visibly jump
            // back to a stale server reading while the resulting notification
            // and status re-fetch complete.
            delay(SETTLE_MS)
            _pendingVolumes.update { it - clientId }
        }
    }

    fun toggleMute(client: SnapClientInfo) = snapcast.setMuted(client.id, !client.muted)

    /** The TUI's `M`: mute everything unless everything is already muted. */
    fun toggleMuteAll() {
        val current = clients.value
        if (current.isEmpty()) return
        snapcast.setAllMuted(muted = current.any { !it.muted })
    }

    /** The TUI's `H` / `L`: nudge every client together. */
    fun adjustAll(delta: Int) = snapcast.adjustAllVolumes(delta)

    fun rename(clientId: String, name: String) = snapcast.setName(clientId, name)

    private companion object {
        const val SEND_DEBOUNCE_MS = 100L
        const val SETTLE_MS = 400L
    }
}
