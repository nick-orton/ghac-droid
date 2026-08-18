package com.nickorton.ghac.ui.volume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickorton.ghac.data.snapcast.SnapClientInfo
import com.nickorton.ghac.ui.components.ConnectionBanner
import com.nickorton.ghac.ui.components.EmptyState
import com.nickorton.ghac.ui.theme.LocalGhacColors

/**
 * Player Volume — one row per SnapCast client.
 *
 * The TUI drove this with `h`/`l` to nudge by 5% and `m` to mute; a slider and
 * a mute button express the same two operations directly, so the keyboard
 * affordances have no touch equivalent to preserve.
 */
@Composable
fun VolumeScreen(
    viewModel: VolumeViewModel,
    modifier: Modifier = Modifier,
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val pending by viewModel.pendingVolumes.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ConnectionBanner(state = connection, label = "SnapCast")

        if (clients.isEmpty()) {
            EmptyState(
                message = if (connection.isConnected) {
                    "No SnapCast clients are connected."
                } else {
                    "Waiting for SnapCast…"
                },
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(clients, key = { it.id }) { client ->
                ClientVolumeCard(
                    client = client,
                    volume = pending[client.id] ?: client.volume,
                    onVolumeChanged = { viewModel.onVolumeChanged(client.id, it) },
                    onVolumeChangeFinished = { viewModel.onVolumeChangeFinished(client.id) },
                    onToggleMute = { viewModel.toggleMute(client) },
                )
            }
        }
    }
}

@Composable
private fun ClientVolumeCard(
    client: SnapClientInfo,
    volume: Int,
    onVolumeChanged: (Int) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onToggleMute: () -> Unit,
) {
    val extras = LocalGhacColors.current
    val activeColor = if (client.muted) extras.volumeMuted else extras.volumeUnmuted

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (client.connected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        // Offline speakers stay listed but visibly inactive,
                        // so a missing room is obvious rather than silent.
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!client.connected) {
                    Text(
                        text = "offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleMute) {
                    Icon(
                        imageVector = if (client.muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (client.muted) "Unmute ${client.name}" else "Mute ${client.name}",
                        tint = activeColor,
                    )
                }

                Slider(
                    value = volume.toFloat(),
                    onValueChange = { onVolumeChanged(it.toInt()) },
                    onValueChangeFinished = onVolumeChangeFinished,
                    valueRange = 0f..100f,
                    enabled = client.connected,
                    colors = SliderDefaults.colors(
                        thumbColor = activeColor,
                        activeTrackColor = activeColor,
                        inactiveTrackColor = extras.progressEmpty,
                    ),
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = "$volume%",
                    style = MaterialTheme.typography.bodyMedium,
                    // Monospace keeps the number from shifting the slider's
                    // right edge as the width of the digits changes.
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}
