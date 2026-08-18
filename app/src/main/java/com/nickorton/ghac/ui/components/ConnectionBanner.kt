package com.nickorton.ghac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nickorton.ghac.data.ConnectionState

/**
 * Inline banner shown while a backend is unavailable.
 *
 * The TUI printed the error and quit; a phone loses Wi-Fi routinely, so here a
 * dropped backend is reported in place while the repository keeps retrying, and
 * the banner disappears by itself once the connection is back.
 */
@Composable
fun ConnectionBanner(
    state: ConnectionState,
    label: String,
    modifier: Modifier = Modifier,
) {
    if (state is ConnectionState.Connected) return

    val (message, showSpinner) = when (state) {
        is ConnectionState.Connecting -> "Connecting to $label…" to true
        is ConnectionState.Reconnecting -> "$label unavailable: ${state.message}. Retrying…" to true
        is ConnectionState.NotConfigured -> "No $label server configured. Open Settings." to false
        is ConnectionState.Connected -> return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
