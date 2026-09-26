package com.nickorton.ghac.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickorton.ghac.LocalNetworkPermission
import com.nickorton.ghac.data.ConnectionState
import com.nickorton.ghac.ui.theme.GhacThemeChoice

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
    val theme by viewModel.theme.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LocalNetworkWarning()

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
            val status = saveStatus(mpdConnection, snapConnection)
            Text(
                text = status.message,
                color = if (status == SaveStatus.Retrying) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Save and connect")
        }

        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        ThemePicker(selected = theme, onSelected = viewModel::onThemeSelected)
    }
}

@Composable
private fun ThemePicker(
    selected: GhacThemeChoice,
    onSelected: (GhacThemeChoice) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        GhacThemeChoice.entries.forEach { choice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = choice == selected,
                        onClick = { onSelected(choice) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = choice == selected, onClick = null)
                Text(
                    text = choice.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                ThemeSwatches(choice)
            }
        }
    }
}

/**
 * A preview of each theme drawn in its own colours, so the list can be judged
 * without applying every entry in turn: its ground, accent and muted colour.
 */
@Composable
private fun ThemeSwatches(choice: GhacThemeChoice) {
    val palette = choice.palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.barBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Swatch(palette.surface)
        Swatch(palette.accent)
        Swatch(palette.volumeMuted)
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * Shown when Android is withholding local network access.
 *
 * Worth calling out explicitly because the symptom is so misleading: denied
 * connections to a LAN address time out rather than being refused, so the app
 * looks like it cannot find the server and the obvious response is to go
 * hunting for a wrong IP address that is in fact correct.
 */
@Composable
private fun LocalNetworkWarning() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalNetworkPermission.isGranted(context)) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    if (granted) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Local network access is blocked",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Android is blocking connections to devices on your " +
                    "Wi-Fi network, so MPD and SnapCast will appear unreachable " +
                    "even with the correct address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = { request.launch(LocalNetworkPermission.NAME) }) {
                Text("Grant access")
            }
        }
    }
}

internal enum class SaveStatus(val message: String) {
    Connecting("Saved. Connecting…"),
    Connected("Saved. Connected."),
    Retrying("Saved, but a server is unreachable. Retrying…"),
}

/**
 * The confirmation under the Save button, derived from the live connection
 * states rather than fixed at save time — otherwise it would keep saying
 * "Connecting…" long after both servers had answered.
 *
 * Any failure outranks success, so one unreachable server is not hidden
 * behind the other connecting fine.
 */
internal fun saveStatus(mpd: ConnectionState, snap: ConnectionState): SaveStatus = when {
    mpd is ConnectionState.Reconnecting || snap is ConnectionState.Reconnecting -> SaveStatus.Retrying
    mpd.isConnected && snap.isConnected -> SaveStatus.Connected
    else -> SaveStatus.Connecting
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
