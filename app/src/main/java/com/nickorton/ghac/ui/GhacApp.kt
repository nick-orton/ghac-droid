package com.nickorton.ghac.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nickorton.ghac.GhacContainer
import com.nickorton.ghac.ui.components.NowPlayingBar
import com.nickorton.ghac.ui.library.LibraryScreen
import com.nickorton.ghac.ui.library.LibraryViewModel
import com.nickorton.ghac.ui.playlist.PlaylistScreen
import com.nickorton.ghac.ui.playlist.PlaylistViewModel
import com.nickorton.ghac.ui.settings.SettingsScreen
import com.nickorton.ghac.ui.settings.SettingsViewModel
import com.nickorton.ghac.ui.volume.VolumeScreen
import com.nickorton.ghac.ui.volume.VolumeViewModel

/**
 * The four destinations. The first three are the TUI's screens, reached with
 * `1`, `2` and `3`; Settings replaces the config file the desktop version read
 * at startup.
 */
private enum class GhacTab(val title: String, val icon: ImageVector) {
    Volume("Volume", Icons.Filled.Speaker),
    Queue("Queue", Icons.AutoMirrored.Filled.QueueMusic),
    Library("Library", Icons.Filled.LibraryMusic),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun GhacApp(container: GhacContainer) {
    val factory = remember(container) { ghacViewModelFactory(container) }

    var tab by rememberSaveable { mutableStateOf(GhacTab.Volume) }

    val playerState by container.mpd.playerState.collectAsStateWithLifecycle()
    val mpdConnection by container.mpd.connection.collectAsStateWithLifecycle()
    val snapConnection by container.snapcast.connection.collectAsStateWithLifecycle()
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)

    // On a first run there is nowhere to connect to, so open Settings rather
    // than showing three empty screens. Only done once, so the user can leave.
    var routedToSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings) {
        val loaded = settings
        if (!routedToSettings && loaded != null && !loaded.isConfigured) {
            tab = GhacTab.Settings
            routedToSettings = true
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                NowPlayingBar(
                    state = playerState,
                    onPlayPause = container.mpd::togglePlayPause,
                    onToggleRandom = container.mpd::toggleRandom,
                )
                NavigationBar {
                    GhacTab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = entry.title) },
                            label = { Text(entry.title) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                GhacTab.Volume -> VolumeScreen(
                    viewModel = viewModel<VolumeViewModel>(factory = factory),
                )

                GhacTab.Queue -> PlaylistScreen(
                    viewModel = viewModel<PlaylistViewModel>(factory = factory),
                )

                GhacTab.Library -> LibraryScreen(
                    viewModel = viewModel<LibraryViewModel>(factory = factory),
                )

                GhacTab.Settings -> SettingsScreen(
                    viewModel = viewModel<SettingsViewModel>(factory = factory),
                    mpdConnection = mpdConnection,
                    snapConnection = snapConnection,
                )
            }
        }
    }
}
