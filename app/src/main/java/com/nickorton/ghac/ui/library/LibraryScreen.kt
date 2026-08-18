package com.nickorton.ghac.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickorton.ghac.data.mpd.DirEntry
import com.nickorton.ghac.ui.components.ConnectionBanner
import com.nickorton.ghac.ui.components.EmptyState

/**
 * Library Navigator — browse the MPD library and enqueue from it.
 *
 * `l` and `h` in the TUI become a tap to descend and the system back gesture to
 * ascend. Back is intercepted only while below the library root, so at the top
 * level it still leaves the screen as a user would expect.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val queued by viewModel.queuedUris.collectAsStateWithLifecycle()

    BackHandler(enabled = state.selected.isNotEmpty() || !state.atRoot) {
        if (state.selected.isNotEmpty()) viewModel.clearSelection() else viewModel.navigateUp()
    }

    Column(modifier = modifier.fillMaxSize()) {
        ConnectionBanner(state = connection, label = "MPD")

        LibraryTopBar(
            breadcrumb = state.breadcrumb,
            atRoot = state.atRoot,
            selectedCount = state.selected.size,
            onNavigateUp = { viewModel.navigateUp() },
            onClearSelection = viewModel::clearSelection,
            onEnqueueSelected = viewModel::enqueueSelected,
        )

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.entries.isEmpty() && !state.loading) {
            EmptyState(
                message = if (connection.isConnected) {
                    "This folder is empty."
                } else {
                    "Waiting for MPD…"
                },
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.entries, key = { it.path }) { entry ->
                LibraryRow(
                    entry = entry,
                    isSelected = entry.path in state.selected,
                    selectionMode = state.selected.isNotEmpty(),
                    alreadyQueued = !entry.isDir && entry.path in queued,
                    onTap = {
                        when {
                            state.selected.isNotEmpty() -> viewModel.toggleSelection(entry)
                            entry.isDir -> viewModel.open(entry)
                            else -> viewModel.enqueue(entry)
                        }
                    },
                    onLongPress = { viewModel.toggleSelection(entry) },
                    onEnqueue = { viewModel.enqueue(entry) },
                )
            }
        }
    }
}

@Composable
private fun LibraryTopBar(
    breadcrumb: String,
    atRoot: Boolean,
    selectedCount: Int,
    onNavigateUp: () -> Unit,
    onClearSelection: () -> Unit,
    onEnqueueSelected: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEnqueueSelected) {
                    Icon(Icons.Filled.Add, contentDescription = "Add selected to queue")
                }
            } else {
                if (!atRoot) {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Parent folder")
                    }
                }
                Text(
                    text = breadcrumb,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    // The tail of a deep path identifies the folder; the root
                    // prefix is the redundant part, so ellipsise on the left.
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (atRoot) 12.dp else 0.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    entry: DirEntry,
    isSelected: Boolean,
    selectionMode: Boolean,
    alreadyQueued: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onEnqueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onLongPress() })
        } else {
            Icon(
                imageVector = if (entry.isDir) Icons.Filled.Folder else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (entry.isDir) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = if (entry.isDir) entry.name else entry.song.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDir && entry.song.artist.isNotBlank()) {
                Text(
                    text = entry.song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (alreadyQueued) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Already in queue",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (!selectionMode) {
            IconButton(onClick = onEnqueue) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add ${entry.name} to queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
