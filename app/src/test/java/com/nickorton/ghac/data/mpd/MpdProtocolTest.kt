package com.nickorton.ghac.data.mpd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MpdProtocolTest {

    // ── Argument quoting ─────────────────────────────────────────────────────
    // Music libraries are full of spaces and apostrophes, so this is the
    // failure mode most likely to bite in real use.

    @Test
    fun `quotes a plain argument`() {
        assertEquals("\"Neil Young\"", MpdProtocol.quote("Neil Young"))
    }

    @Test
    fun `escapes embedded double quotes`() {
        assertEquals("""
            "Say \"Hello\""
        """.trimIndent(), MpdProtocol.quote("""Say "Hello""""))
    }

    @Test
    fun `escapes backslashes before quotes so the escape is not ambiguous`() {
        // A lone backslash must become two, otherwise it would escape the
        // closing quote and desynchronise the parser.
        assertEquals("""
            "AC\\DC"
        """.trimIndent(), MpdProtocol.quote("""AC\DC"""))
    }

    @Test
    fun `quotes an empty argument rather than emitting nothing`() {
        assertEquals("\"\"", MpdProtocol.quote(""))
    }

    // ── Command building ─────────────────────────────────────────────────────

    @Test
    fun `builds a bare command when there are no arguments`() {
        assertEquals("status", MpdProtocol.buildCommand("status"))
    }

    @Test
    fun `quotes string arguments but leaves numbers bare`() {
        assertEquals("move 3 7", MpdProtocol.buildCommand("move", 3, 7))
        assertEquals("""add "Pink Floyd/Animals"""", MpdProtocol.buildCommand("add", "Pink Floyd/Animals"))
    }

    @Test
    fun `renders booleans as MPD's 1 and 0`() {
        assertEquals("random 1", MpdProtocol.buildCommand("random", true))
        assertEquals("random 0", MpdProtocol.buildCommand("random", false))
    }

    @Test
    fun `drops null arguments so optional parameters can be omitted`() {
        // This is how `lsinfo` addresses the library root.
        assertEquals("lsinfo", MpdProtocol.buildCommand("lsinfo", null))
    }

    // ── Response framing ─────────────────────────────────────────────────────

    @Test
    fun `parses a key value line`() {
        assertEquals(MpdPair("Artist", "Neil Young"), MpdProtocol.parseLine("Artist: Neil Young"))
    }

    @Test
    fun `keeps colons that appear inside the value`() {
        assertEquals(
            MpdPair("file", "http://stream.example.com:8000/live"),
            MpdProtocol.parseLine("file: http://stream.example.com:8000/live"),
        )
    }

    @Test
    fun `tolerates a missing space after the colon`() {
        assertEquals(MpdPair("Pos", "4"), MpdProtocol.parseLine("Pos:4"))
    }

    @Test
    fun `returns null for a line with no colon`() {
        assertNull(MpdProtocol.parseLine("something unexpected"))
    }

    @Test
    fun `parses an ACK error line`() {
        val ack = MpdProtocol.parseAck("ACK [50@0] {play} No such song")
        requireNotNull(ack)
        assertEquals(50, ack.code)
        assertEquals(0, ack.commandIndex)
        assertEquals("play", ack.command)
        assertEquals("No such song", ack.detail)
    }

    @Test
    fun `does not mistake a normal line for an ACK`() {
        assertNull(MpdProtocol.parseAck("Artist: ACK"))
        assertNull(MpdProtocol.parseAck("OK"))
    }

    // ── Multi-object responses ───────────────────────────────────────────────

    @Test
    fun `splits a queue response into one chunk per song`() {
        val pairs = listOf(
            MpdPair("file", "a.flac"), MpdPair("Title", "A"), MpdPair("Pos", "0"),
            MpdPair("file", "b.flac"), MpdPair("Title", "B"), MpdPair("Pos", "1"),
        )
        val chunks = MpdProtocol.splitEntries(pairs, MpdProtocol.SONG_START_KEYS)
        assertEquals(2, chunks.size)
        assertEquals(3, chunks[0].size)
        assertEquals("b.flac", chunks[1].first().value)
    }

    @Test
    fun `returns no chunks for an empty response`() {
        assertTrue(MpdProtocol.splitEntries(emptyList(), MpdProtocol.SONG_START_KEYS).isEmpty())
    }

    @Test
    fun `starts a new chunk on any listing key so files and directories interleave`() {
        val pairs = listOf(
            MpdPair("directory", "Rock"),
            MpdPair("file", "intro.flac"), MpdPair("Title", "Intro"),
            MpdPair("directory", "Jazz"),
        )
        val chunks = MpdProtocol.splitEntries(pairs, MpdProtocol.LISTING_START_KEYS)
        assertEquals(3, chunks.size)
    }

    // ── Durations ────────────────────────────────────────────────────────────

    @Test
    fun `parses fractional seconds`() {
        // MPD reports seconds, not milliseconds — reading this as millis would
        // make every track appear about a thousand times too short.
        assertEquals(123.456.seconds, MpdProtocol.parseDuration("123.456"))
        assertEquals(2.seconds + 500.milliseconds, MpdProtocol.parseDuration("2.5"))
    }

    @Test
    fun `falls back to zero for absent or malformed durations`() {
        assertEquals(ZERO, MpdProtocol.parseDuration(null))
        assertEquals(ZERO, MpdProtocol.parseDuration(""))
        assertEquals(ZERO, MpdProtocol.parseDuration("not-a-number"))
        assertEquals(ZERO, MpdProtocol.parseDuration("-5"))
    }

    // ── Song mapping ─────────────────────────────────────────────────────────

    @Test
    fun `maps MPD's own capitalisation onto a Song`() {
        val song = MpdProtocol.songFrom(
            listOf(
                MpdPair("file", "Neil Young/Harvest/01 Out on the Weekend.flac"),
                MpdPair("Title", "Out on the Weekend"),
                MpdPair("Artist", "Neil Young"),
                MpdPair("Album", "Harvest"),
            ),
        )
        assertEquals("Out on the Weekend", song.title)
        assertEquals("Neil Young", song.artist)
        assertEquals("Harvest", song.album)
    }

    @Test
    fun `falls back to the filename when a track has no title tag`() {
        val song = MpdProtocol.songFrom(listOf(MpdPair("file", "unsorted/track01.mp3")))
        assertEquals("track01.mp3", song.displayTitle)
    }

    @Test
    fun `takes the last path segment as the basename`() {
        assertEquals("Harvest", MpdProtocol.basename("Neil Young/Harvest"))
        assertEquals("Rock", MpdProtocol.basename("Rock"))
    }
}
