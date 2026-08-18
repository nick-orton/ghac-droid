package com.nickorton.ghac.data.mpd

import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

/** A `key: value` line from an MPD response, kept in the order the server sent it. */
data class MpdPair(val key: String, val value: String)

/**
 * Thrown when MPD answers a command with an `ACK` line, e.g.
 * `ACK [50@0] {play} No such song`.
 */
class MpdAckException(
    val code: Int,
    val commandIndex: Int,
    val command: String,
    val detail: String,
) : IOException("MPD error $code from '$command': $detail")

/**
 * Pure parsing and serialisation for the MPD text protocol. Everything here is
 * a pure function so it can be unit-tested without a socket; [MpdConnection]
 * supplies the I/O.
 *
 * The protocol is line-based: a command is one newline-terminated line, and the
 * reply is a run of `key: value` lines terminated by either `OK` or an `ACK`
 * error line.
 */
internal object MpdProtocol {

    const val OK = "OK"
    const val GREETING_PREFIX = "OK MPD "

    // Every bracket and brace is escaped, including the closing ones. The JVM's
    // regex engine accepts a bare `]` or `}` as a literal, but Android's ICU
    // engine rejects them outright — and because this is a field initialiser,
    // that failure surfaced as the whole object failing to initialise.
    private val ACK_REGEX = Regex("""^ACK\s*\[(\d+)@(\d+)\]\s*\{([^}]*)\}\s*(.*)$""")

    /** Keys that begin a new object in a multi-object response. */
    val SONG_START_KEYS = setOf("file")
    val LISTING_START_KEYS = setOf("file", "directory", "playlist")

    /**
     * Quotes an argument for transmission. MPD splits arguments on whitespace
     * unless they are quoted, and inside a quoted argument a backslash escapes
     * the next character.
     *
     * Music paths routinely contain spaces, apostrophes and the occasional
     * quote, so every string argument is quoted unconditionally — MPD accepts
     * quoting even when it is not strictly required, and unconditional quoting
     * removes a whole class of "works until someone has a folder with a space
     * in it" bugs.
     */
    fun quote(arg: String): String {
        val escaped = arg
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }

    /**
     * Builds a command line. String arguments are quoted; numbers and booleans
     * are emitted bare, as MPD expects for positional and flag arguments.
     */
    fun buildCommand(name: String, vararg args: Any?): String {
        if (args.isEmpty()) return name
        val rendered = args.filterNotNull().joinToString(" ") { arg ->
            when (arg) {
                is String -> quote(arg)
                is Boolean -> if (arg) "1" else "0"
                else -> arg.toString()
            }
        }
        return if (rendered.isEmpty()) name else "$name $rendered"
    }

    /** Returns the exception described by an `ACK` line, or null if not an ACK. */
    fun parseAck(line: String): MpdAckException? {
        val m = ACK_REGEX.find(line) ?: return null
        val (code, index, command, detail) = m.destructured
        return MpdAckException(
            code = code.toIntOrNull() ?: -1,
            commandIndex = index.toIntOrNull() ?: -1,
            command = command,
            detail = detail,
        )
    }

    /**
     * Splits a `key: value` line. Returns null for lines that carry no colon,
     * which the caller skips rather than treating as an error — MPD emits a
     * few informational lines that do not follow the pattern.
     */
    fun parseLine(line: String): MpdPair? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        return MpdPair(
            key = line.substring(0, idx),
            // Values are separated by ": ", but tolerate a missing space.
            value = line.substring(idx + 1).removePrefix(" "),
        )
    }

    /**
     * Groups a flat pair list into per-object chunks. MPD returns multi-object
     * responses (a queue, a directory listing) as one undelimited stream of
     * pairs, where a new object begins each time one of [startKeys] reappears.
     */
    fun splitEntries(pairs: List<MpdPair>, startKeys: Set<String>): List<List<MpdPair>> {
        val out = mutableListOf<List<MpdPair>>()
        var current = mutableListOf<MpdPair>()
        for (pair in pairs) {
            if (pair.key in startKeys && current.isNotEmpty()) {
                out += current
                current = mutableListOf()
            }
            current += pair
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    /** Convenience: the first value for [key], or null. */
    fun List<MpdPair>.value(key: String): String? =
        firstOrNull { it.key == key }?.value

    /**
     * Parses an MPD time value. MPD reports these as fractional seconds
     * ("123.456"), not milliseconds. Returns [ZERO] on absent or malformed
     * input, matching the TUI's forgiving behaviour.
     */
    fun parseDuration(raw: String?): Duration {
        val secs = raw?.toDoubleOrNull() ?: return ZERO
        if (secs.isNaN() || secs < 0) return ZERO
        return secs.seconds
    }

    /**
     * Builds a [Song] from a response chunk. MPD's own capitalisation is used
     * throughout (`Title`/`Artist`/`Album`, lowercase `file`).
     *
     * Note: the Go original had to special-case `lsinfo`, because the gompd
     * library lowercased keys for that one command. That was a library
     * artifact, not a protocol one — parsing the wire format directly means
     * one consistent mapping for every command.
     */
    fun songFrom(chunk: List<MpdPair>): Song = Song(
        title = chunk.value("Title").orEmpty(),
        artist = chunk.value("Artist").orEmpty(),
        album = chunk.value("Album").orEmpty(),
        file = chunk.value("file").orEmpty(),
    )

    /** Final path segment of an MPD URI. MPD always uses forward slashes. */
    fun basename(path: String): String = path.substringAfterLast('/')
}
