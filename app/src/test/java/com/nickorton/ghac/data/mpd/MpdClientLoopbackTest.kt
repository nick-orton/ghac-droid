package com.nickorton.ghac.data.mpd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class MpdClientLoopbackTest {

    private fun withClient(
        greeting: String = "OK MPD 0.23.5",
        handler: (String) -> List<String>,
        block: suspend (MpdClient, FakeMpdServer) -> Unit,
    ) {
        FakeMpdServer(greeting, handler).use { server ->
            runBlocking {
                MpdClient.connect("127.0.0.1", server.port).use { client ->
                    block(client, server)
                }
            }
        }
    }

    @Test
    fun `reads the server version from the greeting`() {
        withClient(handler = { emptyList() }) { client, _ ->
            assertEquals("0.23.5", client.serverVersion)
        }
    }

    @Test
    fun `refuses to talk to something that is not MPD`() {
        FakeMpdServer(greeting = "220 ProFTPD Server ready") { emptyList() }.use { server ->
            val error = assertThrows(IOException::class.java) {
                runBlocking { MpdClient.connect("127.0.0.1", server.port) }
            }
            assertTrue(error.message!!.contains("not an MPD server"))
        }
    }

    @Test
    fun `assembles player state from status and currentsong`() {
        withClient(
            handler = { command ->
                when (command) {
                    "status" -> listOf(
                        "state: play",
                        "random: 1",
                        "song: 3",
                        "elapsed: 61.500",
                        "duration: 245.000",
                    )

                    "currentsong" -> listOf(
                        "file: Neil Young/Harvest/02 Harvest.flac",
                        "Title: Harvest",
                        "Artist: Neil Young",
                        "Album: Harvest",
                    )

                    else -> emptyList()
                }
            },
        ) { client, _ ->
            val state = client.playerState()
            assertEquals(PlayState.PLAY, state.state)
            assertTrue(state.random)
            assertEquals(3, state.songPos)
            assertEquals(61.5.seconds, state.elapsed)
            assertEquals(245.seconds, state.total)
            assertEquals("Harvest", state.song.title)
            assertEquals("Neil Young", state.song.artist)
        }
    }

    @Test
    fun `treats a missing song field as nothing playing`() {
        withClient(
            handler = { command ->
                when (command) {
                    "status" -> listOf("state: stop", "random: 0")
                    else -> emptyList()
                }
            },
        ) { client, _ ->
            val state = client.playerState()
            assertEquals(PlayerState.NO_SONG, state.songPos)
            assertEquals(PlayState.STOP, state.state)
        }
    }

    @Test
    fun `parses a queue into positioned entries`() {
        withClient(
            handler = { command ->
                if (command == "playlistinfo") {
                    listOf(
                        "file: a.flac", "Title: Alpha", "Pos: 0", "Id: 10",
                        "file: b.flac", "Title: Beta", "Pos: 1", "Id: 11",
                    )
                } else {
                    emptyList()
                }
            },
        ) { client, _ ->
            val queue = client.playlistInfo()
            assertEquals(2, queue.size)
            assertEquals("Alpha", queue[0].song.title)
            assertEquals(0, queue[0].pos)
            assertEquals("Beta", queue[1].song.title)
            assertEquals(1, queue[1].pos)
        }
    }

    @Test
    fun `separates directories from files and skips stored playlists`() {
        withClient(
            handler = { command ->
                if (command.startsWith("lsinfo")) {
                    listOf(
                        "directory: Rock",
                        "directory: Jazz",
                        "file: intro.flac", "Title: Intro", "Artist: Someone",
                        "playlist: my-mix.m3u",
                    )
                } else {
                    emptyList()
                }
            },
        ) { client, _ ->
            val entries = client.lsInfo("")

            // The stored playlist is dropped, as in the TUI's navigator.
            assertEquals(3, entries.size)
            assertEquals(listOf("Rock", "Jazz"), entries.filter { it.isDir }.map { it.name })

            val file = entries.single { !it.isDir }
            assertEquals("intro.flac", file.name)
            assertEquals("Intro", file.song.title)
        }
    }

    @Test
    fun `addresses the library root with a bare lsinfo`() {
        withClient(handler = { emptyList() }) { client, server ->
            client.lsInfo("")
            assertTrue(server.received.contains("lsinfo"))
        }
    }

    @Test
    fun `quotes library paths that contain spaces`() {
        // Without quoting, MPD would read this as three separate arguments and
        // the enqueue would fail for most real albums.
        withClient(handler = { emptyList() }) { client, server ->
            client.add("Pink Floyd/The Wall")
            assertTrue(server.received.contains("""add "Pink Floyd/The Wall""""))
        }
    }

    @Test
    fun `deletes multiple positions from the bottom up`() {
        // Each delete shifts later positions down by one, so ascending order
        // would remove the wrong songs after the first.
        withClient(handler = { emptyList() }) { client, server ->
            client.delete(listOf(1, 5, 3))

            val deletes = server.received.filter { it.startsWith("delete") }
            assertEquals(listOf("delete 5", "delete 3", "delete 1"), deletes)
        }
    }

    @Test
    fun `surfaces an ACK as an exception`() {
        withClient(
            handler = { command ->
                if (command.startsWith("play")) {
                    listOf("ACK [50@0] {play} No such song")
                } else {
                    emptyList()
                }
            },
        ) { client, _ ->
            val error = assertThrows(MpdAckException::class.java) {
                runBlocking { client.play(99) }
            }
            assertEquals(50, error.code)
            assertEquals("play", error.command)
        }
    }

    @Test
    fun `reports which subsystems changed when idle returns`() {
        withClient(
            handler = { command ->
                if (command.startsWith("idle")) listOf("changed: playlist") else emptyList()
            },
        ) { client, server ->
            assertEquals(listOf("playlist"), client.waitForChanges())
            assertTrue(server.received.any { it == "idle player playlist options" })
        }
    }

    @Test
    fun `resumes with a bare play but seeks with a position`() {
        withClient(handler = { emptyList() }) { client, server ->
            client.play()
            client.play(4)
            assertTrue(server.received.contains("play"))
            assertTrue(server.received.contains("play 4"))
        }
    }
}
