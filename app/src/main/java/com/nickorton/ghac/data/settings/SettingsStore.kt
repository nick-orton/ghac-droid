package com.nickorton.ghac.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nickorton.ghac.data.mpd.MpdConnection
import com.nickorton.ghac.data.snapcast.SnapcastClient
import com.nickorton.ghac.ui.theme.GhacThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where the two backends live. This replaces the TUI's `~/.config/.ghacrc`;
 * a phone has no obvious place for a dotfile, so the same four values are
 * edited in-app and persisted with DataStore.
 */
data class ServerSettings(
    val mpdHost: String = "",
    val mpdPort: Int = MpdConnection.DEFAULT_PORT,
    val snapHost: String = "",
    val snapPort: Int = SnapcastClient.DEFAULT_PORT,
) {
    val isConfigured: Boolean
        get() = mpdHost.isNotBlank() && snapHost.isNotBlank()

    /**
     * Mirrors the validation in the Go `config.validate`: a host is required
     * and a port must be in range. Returns null when the settings are usable.
     */
    fun validationError(): String? = when {
        mpdHost.isBlank() -> "MPD host is required"
        mpdPort !in VALID_PORTS -> "MPD port must be between 1 and 65535"
        snapHost.isBlank() -> "SnapCast host is required"
        snapPort !in VALID_PORTS -> "SnapCast port must be between 1 and 65535"
        else -> null
    }

    companion object {
        val VALID_PORTS = 1..65535
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "ghac_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<ServerSettings> = context.settingsDataStore.data.map { prefs ->
        ServerSettings(
            mpdHost = prefs[MPD_HOST].orEmpty(),
            mpdPort = prefs[MPD_PORT] ?: MpdConnection.DEFAULT_PORT,
            snapHost = prefs[SNAP_HOST].orEmpty(),
            snapPort = prefs[SNAP_PORT] ?: SnapcastClient.DEFAULT_PORT,
        )
    }

    suspend fun save(settings: ServerSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[MPD_HOST] = settings.mpdHost.trim()
            prefs[MPD_PORT] = settings.mpdPort
            prefs[SNAP_HOST] = settings.snapHost.trim()
            prefs[SNAP_PORT] = settings.snapPort
        }
    }

    /**
     * Kept apart from [settings] on purpose: the container redials both
     * servers whenever [settings] changes, and picking a theme must not.
     */
    val theme: Flow<GhacThemeChoice> = context.settingsDataStore.data.map { prefs ->
        GhacThemeChoice.fromName(prefs[THEME])
    }

    suspend fun saveTheme(choice: GhacThemeChoice) {
        context.settingsDataStore.edit { prefs -> prefs[THEME] = choice.name }
    }

    private companion object {
        val MPD_HOST = stringPreferencesKey("mpd_host")
        val MPD_PORT = intPreferencesKey("mpd_port")
        val SNAP_HOST = stringPreferencesKey("snap_host")
        val SNAP_PORT = intPreferencesKey("snap_port")
        val THEME = stringPreferencesKey("theme")
    }
}
