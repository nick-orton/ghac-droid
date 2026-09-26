package com.nickorton.ghac.ui.settings

import com.nickorton.ghac.data.ConnectionState.Connected
import com.nickorton.ghac.data.ConnectionState.Connecting
import com.nickorton.ghac.data.ConnectionState.NotConfigured
import com.nickorton.ghac.data.ConnectionState.Reconnecting
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveStatusTest {

    @Test
    fun `reports connected once both servers are up`() {
        assertEquals(SaveStatus.Connected, saveStatus(Connected, Connected))
    }

    @Test
    fun `keeps connecting while either server is still dialling`() {
        assertEquals(SaveStatus.Connecting, saveStatus(Connected, Connecting))
        assertEquals(SaveStatus.Connecting, saveStatus(Connecting, Connected))
        assertEquals(SaveStatus.Connecting, saveStatus(NotConfigured, NotConfigured))
    }

    @Test
    fun `a failing server outranks the other connecting`() {
        val down = Reconnecting("EHOSTUNREACH")
        assertEquals(SaveStatus.Retrying, saveStatus(Connected, down))
        assertEquals(SaveStatus.Retrying, saveStatus(down, Connecting))
    }
}
