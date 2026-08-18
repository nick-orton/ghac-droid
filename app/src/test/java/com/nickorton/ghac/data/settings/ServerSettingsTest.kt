package com.nickorton.ghac.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the table-driven config tests in the Go project's
 * `internal/config/config_test.go`.
 */
class ServerSettingsTest {

    private val valid = ServerSettings(
        mpdHost = "192.168.1.10",
        mpdPort = 6600,
        snapHost = "192.168.1.10",
        snapPort = 1705,
    )

    @Test
    fun `accepts a fully specified pair of servers`() {
        assertNull(valid.validationError())
        assertTrue(valid.isConfigured)
    }

    @Test
    fun `requires an MPD host`() {
        assertEquals("MPD host is required", valid.copy(mpdHost = "").validationError())
    }

    @Test
    fun `requires a SnapCast host`() {
        assertEquals("SnapCast host is required", valid.copy(snapHost = "").validationError())
    }

    @Test
    fun `rejects ports outside the valid range`() {
        assertEquals(
            "MPD port must be between 1 and 65535",
            valid.copy(mpdPort = 0).validationError(),
        )
        assertEquals(
            "MPD port must be between 1 and 65535",
            valid.copy(mpdPort = 70_000).validationError(),
        )
        assertEquals(
            "SnapCast port must be between 1 and 65535",
            valid.copy(snapPort = -1).validationError(),
        )
    }

    @Test
    fun `is not configured until both hosts are set`() {
        assertFalse(ServerSettings().isConfigured)
        assertFalse(valid.copy(snapHost = "").isConfigured)
    }

    @Test
    fun `defaults to the standard MPD and SnapCast ports`() {
        val defaults = ServerSettings()
        assertEquals(6600, defaults.mpdPort)
        assertEquals(1705, defaults.snapPort)
    }
}
