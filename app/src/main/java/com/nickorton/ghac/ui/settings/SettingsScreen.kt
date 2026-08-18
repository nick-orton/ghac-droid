package com.nickorton.ghac.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickorton.ghac.data.ConnectionState

/**
 * Settings — where the two servers live.
 *
 * This is the Android replacement for `~/.config/.ghacrc`. Saving is what
 * triggers a reconnect: the container watches the settings flow and redials
 * both backends whenever it changes.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    mpdConnection: ConnectionState,
    snapConnection: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Music Player Daemon", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.mpdHost,
                onValueChange = viewModel::onMpdHostChanged,
                label = { Text("Host") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = form.mpdPort,
                onValueChange = viewModel::onMpdPortChanged,
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        StatusLine(label = "MPD", state = mpdConnection)

        Text(
            "SnapCast",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.snapHost,
                onValueChange = viewModel::onSnapHostChanged,
                label = { Text("Host") },
                placeholder = { Text("192.168.1.10") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = form.snapPort,
                onValueChange = viewModel::onSnapPortChanged,
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        StatusLine(label = "SnapCast", state = snapConnection)

        form.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (form.saved) {
            Text(
                text = "Saved. Connecting…",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Save and connect")
        }
    }
}

@Composable
private fun StatusLine(label: String, state: ConnectionState) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "$label: connected" to MaterialTheme.colorScheme.primary
        is ConnectionState.Connecting -> "$label: connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionState.Reconnecting -> "$label: ${state.message}" to MaterialTheme.colorScheme.error
        is ConnectionState.NotConfigured -> "$label: not configured" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = text, color = color, style = MaterialTheme.typography.bodySmall)
}
