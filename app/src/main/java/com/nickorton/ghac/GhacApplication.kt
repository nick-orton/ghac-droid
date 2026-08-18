package com.nickorton.ghac

import android.app.Application
import android.content.Context
import com.nickorton.ghac.data.settings.ServerSettings
import com.nickorton.ghac.data.settings.SettingsStore
import com.nickorton.ghac.repo.MpdRepository
import com.nickorton.ghac.repo.SnapcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hand-rolled service locator.
 *
 * The app has exactly three long-lived objects and no build-time code
 * generation to justify, so a container built in [GhacApplication] is enough —
 * a DI framework would be more machinery than this needs.
 */
class GhacContainer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob())

    val settingsStore = SettingsStore(context)
    val mpd = MpdRepository(scope)
    val snapcast = SnapcastRepository(scope)

    private var connectionJob: Job? = null

    /**
     * Opens both connections and keeps them in step with the saved settings.
     *
     * Called when the UI comes to the foreground. Sockets are deliberately not
     * held while the app is backgrounded: ghac only sends commands, so there is
     * nothing to keep alive for, and holding idle TCP connections across a
     * screen-off would burn battery and get killed by the platform anyway.
     */
    fun start() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            settingsStore.settings
                .distinctUntilChanged()
                .collect { settings ->
                    if (settings.isConfigured) {
                        mpd.connect(settings.mpdHost, settings.mpdPort)
                        snapcast.connect(settings.snapHost, settings.snapPort)
                    } else {
                        mpd.disconnect()
                        snapcast.disconnect()
                    }
                }
        }
    }

    /** Drops both connections when the UI goes to the background. */
    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        mpd.disconnect()
        snapcast.disconnect()
    }

    suspend fun currentSettings(): ServerSettings = settingsStore.settings.first()
}

class GhacApplication : Application() {

    lateinit var container: GhacContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = GhacContainer(this)
    }
}
