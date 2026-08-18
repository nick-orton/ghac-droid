package com.nickorton.ghac.data

/**
 * Connection status for one backend.
 *
 * The TUI treated a dropped connection as fatal and quit
 * (`docs/architecture.md` §4.3). That is the wrong behaviour on a phone, which
 * moves between networks constantly, so a lost connection becomes a
 * [Reconnecting] state that the UI reports in a banner while the repository
 * retries in the background.
 */
sealed interface ConnectionState {

    /** No server configured yet. */
    data object NotConfigured : ConnectionState

    data object Connecting : ConnectionState

    data object Connected : ConnectionState

    /** Disconnected and retrying; [message] explains the last failure. */
    data class Reconnecting(val message: String) : ConnectionState

    val isConnected: Boolean get() = this is Connected
}
