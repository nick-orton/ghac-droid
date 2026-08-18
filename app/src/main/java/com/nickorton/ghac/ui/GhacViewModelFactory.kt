package com.nickorton.ghac.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nickorton.ghac.GhacContainer
import com.nickorton.ghac.ui.library.LibraryViewModel
import com.nickorton.ghac.ui.playlist.PlaylistViewModel
import com.nickorton.ghac.ui.settings.SettingsViewModel
import com.nickorton.ghac.ui.volume.VolumeViewModel

/**
 * Wires each ViewModel to the repositories it needs.
 *
 * With four ViewModels and no build-time codegen to justify, an explicit
 * factory is clearer than adding a DI framework.
 */
fun ghacViewModelFactory(container: GhacContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { VolumeViewModel(container.snapcast) }
    initializer { PlaylistViewModel(container.mpd) }
    initializer { LibraryViewModel(container.mpd) }
    initializer { SettingsViewModel(container.settingsStore) }
}
