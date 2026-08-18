package com.nickorton.ghac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nickorton.ghac.data.mpd.PlayState
import com.nickorton.ghac.data.mpd.PlayerState
import com.nickorton.ghac.ui.theme.LocalGhacColors
import kotlin.time.Duration

/**
 * The persistent now-playing bar, equivalent to the TUI's top bar.
 *
 * It sits directly above the navigation bar so playback control is reachable
 * from every screen, the same way `p` and `z` were global keys in the TUI.
 */
@Composable
fun NowPlayingBar(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onToggleRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalGhacColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(extras.nowPlayingBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.song.displayTitle.ifBlank { "Nothing playing" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOf(state.song.artist, state.song.album)
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

            IconButton(onClick = onToggleRandom) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = if (state.random) "Shuffle on" else "Shuffle off",
                    tint = if (state.random) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (state.state == PlayState.PLAY) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (state.state == PlayState.PLAY) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.elapsed.formatClock(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { state.progressFraction() },
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = extras.progressEmpty,
            )
            Text(
                text = state.total.formatClock(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun PlayerState.progressFraction(): Float {
    val totalSeconds = total.inWholeSeconds
    if (totalSeconds <= 0) return 0f
    return (elapsed.inWholeSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
}

/** Renders a duration as `m:ss`, or `h:mm:ss` for anything over an hour. */
fun Duration.formatClock(): String {
    val totalSeconds = inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
