package com.nickorton.ghac.data.snapcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapcastProtocolTest {

    @Test
    fun `flattens clients across every group`() {
        // ghac shows one flat list of speakers, but SnapCast nests them under
        // groups — a parser that only read the first group would silently hide
        // rooms, which is the bug this guards against.
        val clients = SnapcastProtocol.parseServerStatus(
            """
            {"server":{"groups":[
              {"clients":[
                {"id":"aa","connected":true,
                 "config":{"name":"Kitchen","volume":{"muted":false,"percent":42}},
                 "host":{"name":"pi-kitchen"}}
              ]},
              {"clients":[
                {"id":"bb","connected":true,
                 "config":{"name":"Study","volume":{"muted":true,"percent":80}},
                 "host":{"name":"pi-study"}}
              ]}
            ]}}
            """.trimIndent(),
        )

        assertEquals(2, clients.size)
        assertEquals(listOf("Kitchen", "Study"), clients.map { it.name })
        assertEquals(42, clients[0].volume)
        assertFalse(clients[0].muted)
        assertTrue(clients[1].muted)
    }

    @Test
    fun `falls back to the host name when no name is configured`() {
        val clients = SnapcastProtocol.parseServerStatus(
            """
            {"server":{"groups":[{"clients":[
              {"id":"aa","connected":true,
               "config":{"name":"","volume":{"muted":false,"percent":10}},
               "host":{"name":"pi-porch"}}
            ]}]}}
            """.trimIndent(),
        )
        assertEquals("pi-porch", clients.single().name)
    }

    @Test
    fun `ignores the many status fields ghac does not use`() {
        // SnapCast's real status document is much larger than this model; the
        // parser must not fail on fields it does not know about.
        val clients = SnapcastProtocol.parseServerStatus(
            """
            {"server":{
              "server":{"host":{"arch":"x86_64"},"snapserver":{"version":"0.27.0"}},
              "streams":[{"id":"default","status":"playing"}],
              "groups":[{"id":"g1","muted":false,"stream_id":"default","clients":[
                {"id":"aa","connected":true,
                 "config":{"instance":1,"latency":0,"name":"Deck",
                           "volume":{"muted":false,"percent":55}},
                 "host":{"arch":"aarch64","name":"pi-deck","os":"Raspbian"},
                 "snapclient":{"version":"0.27.0"},
                 "lastSeen":{"sec":1700000000,"usec":0}}
              ]}]
            }}
            """.trimIndent(),
        )
        val client = clients.single()
        assertEquals("Deck", client.name)
        assertEquals(55, client.volume)
        assertTrue(client.connected)
    }

    @Test
    fun `reports disconnected clients rather than dropping them`() {
        val clients = SnapcastProtocol.parseServerStatus(
            """
            {"server":{"groups":[{"clients":[
              {"id":"aa","connected":false,
               "config":{"name":"Garage","volume":{"muted":false,"percent":30}},
               "host":{"name":"pi-garage"}}
            ]}]}}
            """.trimIndent(),
        )
        assertFalse(clients.single().connected)
    }

    @Test
    fun `returns an empty list when the server has no groups`() {
        assertTrue(SnapcastProtocol.parseServerStatus("""{"server":{"groups":[]}}""").isEmpty())
    }

    @Test
    fun `returns an empty list when a group has no clients`() {
        assertTrue(
            SnapcastProtocol.parseServerStatus("""{"server":{"groups":[{"clients":[]}]}}""").isEmpty(),
        )
    }
}
