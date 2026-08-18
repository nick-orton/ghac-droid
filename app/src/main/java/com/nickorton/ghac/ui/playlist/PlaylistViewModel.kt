package com.nickorton.ghac.ui.playlist

import androidx.lifecycle.ViewModel
import com.nickorton.ghac.data.mpd.PlaylistEntry
import com.nickorton.ghac.repo.MpdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Backs the Playlist Control screen.
 *
 * The TUI kept a set of selected indices toggled with `space`; on a touchscreen
 * the same set is entered by long-pressing a row, and the screen switches to a
 * contextual app bar while it is non-empty.
 */
class PlaylistViewModel(private val mpd: MpdRepository) : ViewModel() {

    val playlist: StateFlow<List<PlaylistEntry>> = mpd.playlist
    val playerState = mpd.playerState
    val connection = mpd.connection

    private val _selected = MutableStateFlow<Set<Int>>(emptySet())

    /** Queue positions the user has selected. Empty means normal browsing. */
    val selected: StateFlow<Set<Int>> = _selected.asStateFlow()

    fun toggleSelection(pos: Int) = _selected.update { current ->
        if (pos in current) current - pos else current + pos
    }

    fun clearSelection() = _selected.update { emptySet() }

    fun selectAll() = _selected.update { playlist.value.map { it.pos }.toSet() }

    fun play(pos: Int) = mpd.playAt(pos)

    fun remove(pos: Int) {
        mpd.removeAt(pos)
        _selected.update { it - pos }
    }

    /**
     * Removes every selected position. Ordering is handled inside
     * [com.nickorton.ghac.data.mpd.MpdClient.delete], which deletes from the
     * bottom up so earlier removals do not shift later positions.
     */
    fun removeSelected() {
        val positions = _selected.value
        if (positions.isEmpty()) return
        mpd.removeAll(positions)
        clearSelection()
    }

    fun clearQueue() {
        mpd.clearQueue()
        clearSelection()
    }

    fun move(from: Int, to: Int) {
        if (from == to) return
        mpd.move(from, to)
        clearSelection()
    }
}
