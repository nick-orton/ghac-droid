package com.nickorton.ghac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickorton.ghac.ui.GhacApp
import com.nickorton.ghac.ui.theme.GhacTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: GhacContainer

    /**
     * The repositories retry on a fixed backoff, so a grant is picked up by the
     * next attempt without anything needing to be re-triggered here.
     */
    private val localNetworkRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as GhacApplication).container

        // Ask before the first connection attempt: without this, on Android 17
        // and later every connection to a LAN address hangs until it times out.
        if (LocalNetworkPermission.isRequired(this)) {
            localNetworkRequest.launch(LocalNetworkPermission.NAME)
        }

        setContent {
            // Null until DataStore answers, so someone who picked a theme does
            // not see a frame of the default first. The read takes a few ms.
            val theme by container.settingsStore.theme
                .collectAsStateWithLifecycle(initialValue = null)
            theme?.let { choice ->
                GhacTheme(choice) {
                    GhacApp(container)
                }
            }
        }
    }

    /**
     * Connections follow the visible lifecycle rather than the process.
     *
     * ghac only issues commands — it plays no audio — so there is nothing worth
     * holding a socket open for while the app is in the background, and a
     * parked MPD `idle` connection would just be woken by the platform or
     * silently dropped by the router.
     */
    override fun onStart() {
        super.onStart()
        container.start()
    }

    override fun onStop() {
        super.onStop()
        container.stop()
    }
}
