package com.nickorton.ghac.ui.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickorton.ghac.data.mpd.PlaylistEntry
import com.nickorton.ghac.ui.components.ConnectionBanner
import com.nickorton.ghac.ui.components.EmptyState

/**
 * Playlist Control — the MPD play queue.
 *
 * Tap plays, long-press starts a selection. The TUI's `Ctrl-J`/`Ctrl-K` reorder
 * becomes explicit move up/down actions on a single selected row, which maps
 * more closely to the original than free-form dragging would and avoids
 * fighting the list's own scroll gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.playlist.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Leaving selection mode is what back should do first.
    BackHandler(enabled = selected.isNotEmpty()) { viewModel.clearSelection() }

    // Follow the playing song so the queue does not drift out of view.
    LaunchedEffect(playerState.songPos, entries.size) {
        val pos = playerState.songPos
        if (pos >= 0 && pos < entries.size && selected.isEmpty()) {
            listState.animateScrollToItem(pos)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ConnectionBanner(state = connection, label = "MPD")

        SelectionBar(
            selectedCount = selected.size,
            canMove = selected.size == 1,
            onClearSelection = viewModel::clearSelection,
            onRemoveSelected = viewModel::removeSelected,
            onClearQueue = { confirmClear = true },
            onMoveUp = {
                selected.singleOrNull()?.let { pos ->
                    if (pos > 0) viewModel.move(pos, pos - 1)
                }
            },
            onMoveDown = {
                selected.singleOrNull()?.let { pos ->
                    if (pos < entries.lastIndex) viewModel.move(pos, pos + 1)
                }
            },
        )

        if (entries.isEmpty()) {
            EmptyState(
                message = if (connection.isConnected) {
                    "The queue is empty. Add music from the Library tab."
                } else {
                    "Waiting for MPD…"
                },
            )
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.pos }) { entry ->
                PlaylistRow(
                    entry = entry,
                    isPlaying = entry.pos == playerState.songPos,
                    isSelected = entry.pos in selected,
                    selectionMode = selected.isNotEmpty(),
                    onTap = {
                        if (selected.isNotEmpty()) {
                            viewModel.toggleSelection(entry.pos)
                        } else {
                            viewModel.play(entry.pos)
                        }
                    },
                    onLongPress = { viewModel.toggleSelection(entry.pos) },
                    onRemove = { viewModel.remove(entry.pos) },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the queue?") },
            text = { Text("This removes all ${entries.size} songs and stops playback.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearQueue()
                        confirmClear = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

/** Contextual bar shown while rows are selected. */
@Composable
private fun SelectionBar(
    selectedCount: Int,
    canMove: Boolean,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onClearQueue: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
                IconButton(onClick = onMoveUp, enabled = canMove) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMove) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onRemoveSelected) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove selected")
                }
            } else {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                IconButton(onClick = onClearQueue) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear queue")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    entry: PlaylistEntry,
    isPlaying: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.background
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onLongPress() })
        } else {
            Text(
                text = "${entry.pos + 1}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.song.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOf(entry.song.artist, entry.song.album)
                .filter { it.isNotBlank() }
                .joinToString(" — ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isPlaying) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Now playing",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else if (!selectionMode) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${entry.song.displayTitle}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
