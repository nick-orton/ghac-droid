package com.nickorton.ghac.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickorton.ghac.data.ConnectionState
import com.nickorton.ghac.data.mpd.DirEntry
import com.nickorton.ghac.repo.MpdRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Library Navigator screen.
 *
 * The TUI moved through directories with `l` and `h`; here descending is a tap
 * and ascending is the system back gesture, so the screen keeps its own path
 * and intercepts back until it reaches the library root.
 */
class LibraryViewModel(private val mpd: MpdRepository) : ViewModel() {

    data class UiState(
        /** Current directory as an MPD URI; empty string is the library root. */
        val path: String = "",
        val entries: List<DirEntry> = emptyList(),
        val loading: Boolean = false,
        /** MPD URIs the user has selected for a bulk enqueue. */
        val selected: Set<String> = emptySet(),
    ) {
        val atRoot: Boolean get() = path.isEmpty()

        /** Breadcrumb shown in the app bar. */
        val breadcrumb: String get() = if (atRoot) "Library" else path
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val connection = mpd.connection

    /**
     * URIs already sitting in the queue, so browsed files can be marked as
     * enqueued — the touch equivalent of the navigator's `inPlaylist` map.
     */
    val queuedUris: StateFlow<Set<String>> = mpd.playlist
        .map { entries -> entries.map { it.song.file }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        // The first listing cannot be fetched until MPD is actually connected,
        // and it must be re-fetched after any reconnect.
        viewModelScope.launch {
            combine(mpd.connection, _state.map { it.path }.distinctUntilChanged(), ::Pair)
                .collect { (connection, path) ->
                    if (connection is ConnectionState.Connected) load(path)
                }
        }
    }

    private suspend fun load(path: String) {
        _state.update { it.copy(loading = true) }
        val entries = mpd.listDirectory(path)
        _state.update {
            // Guard against a listing that arrived after the user moved on.
            if (it.path != path) it else it.copy(entries = entries, loading = false)
        }
    }

    fun open(entry: DirEntry) {
        if (!entry.isDir) return
        _state.update { it.copy(path = entry.path, entries = emptyList(), selected = emptySet()) }
    }

    /** Ascends one level. Returns false at the root so back can exit the screen. */
    fun navigateUp(): Boolean {
        val current = _state.value.path
        if (current.isEmpty()) return false
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
        _state.update { it.copy(path = parent, entries = emptyList(), selected = emptySet()) }
        return true
    }

    fun toggleSelection(entry: DirEntry) = _state.update { current ->
        val selected = current.selected
        current.copy(
            selected = if (entry.path in selected) selected - entry.path else selected + entry.path,
        )
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Enqueues one entry. MPD expands a directory URI recursively. */
    fun enqueue(entry: DirEntry) = mpd.enqueue(listOf(entry.path))

    fun enqueueSelected() {
        val uris = _state.value.selected.toList()
        if (uris.isEmpty()) return
        mpd.enqueue(uris)
        clearSelection()
    }

    fun refresh() {
        viewModelScope.launch { load(_state.value.path) }
    }
}
