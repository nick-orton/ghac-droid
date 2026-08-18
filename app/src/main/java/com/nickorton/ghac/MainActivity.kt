package com.nickorton.ghac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nickorton.ghac.ui.GhacApp
import com.nickorton.ghac.ui.theme.GhacTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: GhacContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as GhacApplication).container

        setContent {
            GhacTheme {
                GhacApp(container)
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
