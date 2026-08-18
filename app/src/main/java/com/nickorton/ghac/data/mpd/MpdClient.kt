package com.nickorton.ghac.data.mpd

import com.nickorton.ghac.data.mpd.MpdProtocol.value
import java.io.Closeable

/**
 * Command surface for MPD, mirroring the Go client in `internal/mpd/client.go`.
 *
 * Two connections are held, for the same reason the original does: MPD's `idle`
 * command occupies its connection for as long as it is parked, so commands need
 * a second socket to travel on.
 */
class MpdClient(
    private val commands: MpdConnection,
    private val idleConn: MpdConnection,
) : Closeable {

    val serverVersion: String get() = commands.serverVersion

    val isConnected: Boolean
        get() = commands.isConnected && idleConn.isConnected

    // ── Queries ──────────────────────────────────────────────────────────────

    suspend fun status(): Map<String, String> =
        commands.command("status").associate { it.key to it.value }

    suspend fun currentSong(): Song =
        MpdProtocol.songFrom(commands.command("currentsong"))

    /** Assembles full player state from `status` plus `currentsong`. */
    suspend fun playerState(): PlayerState {
        val status = status()
        return PlayerState(
            state = PlayState.fromMpd(status["state"]),
            song = currentSong(),
            elapsed = MpdProtocol.parseDuration(status["elapsed"]),
            total = MpdProtocol.parseDuration(status["duration"]),
            // Absent `song` means nothing is playing.
            songPos = status["song"]?.toIntOrNull() ?: PlayerState.NO_SONG,
            random = status["random"] == "1",
        )
    }

    suspend fun playlistInfo(): List<PlaylistEntry> =
        MpdProtocol.splitEntries(commands.command("playlistinfo"), MpdProtocol.SONG_START_KEYS)
            .mapIndexed { index, chunk ->
                PlaylistEntry(
                    song = MpdProtocol.songFrom(chunk),
                    pos = chunk.value("Pos")?.toIntOrNull() ?: index,
                )
            }

    /**
     * Lists a library directory. Pass an empty [path] for the music root.
     * Stored-playlist entries in the reply are skipped.
     */
    suspend fun lsInfo(path: String): List<DirEntry> {
        val pairs = commands.command(
            MpdProtocol.buildCommand("lsinfo", path.ifEmpty { null }),
        )
        return MpdProtocol.splitEntries(pairs, MpdProtocol.LISTING_START_KEYS)
            .mapNotNull { chunk ->
                val head = chunk.first()
                when (head.key) {
                    "directory" -> DirEntry(
                        name = MpdProtocol.basename(head.value),
                        path = head.value,
                        isDir = true,
                    )

                    "file" -> DirEntry(
                        name = MpdProtocol.basename(head.value),
                        path = head.value,
                        isDir = false,
                        song = MpdProtocol.songFrom(chunk),
                    )

                    else -> null // stored playlists are not browsable here
                }
            }
    }

    // ── Playback ─────────────────────────────────────────────────────────────

    /** Resumes playback, or starts at 0-indexed [pos] when given. */
    suspend fun play(pos: Int = RESUME) {
        commands.command(
            if (pos == RESUME) "play" else MpdProtocol.buildCommand("play", pos),
        )
    }

    suspend fun pause() {
        commands.command(MpdProtocol.buildCommand("pause", true))
    }

    suspend fun setRandom(on: Boolean) {
        commands.command(MpdProtocol.buildCommand("random", on))
    }

    // ── Queue editing ────────────────────────────────────────────────────────

    suspend fun delete(pos: Int) {
        commands.command(MpdProtocol.buildCommand("delete", pos))
    }

    /**
     * Removes several queue positions in one pass.
     *
     * Deletion is applied in descending order because each removal shifts every
     * later position down by one — deleting ascending would make each index
     * after the first refer to the wrong song. The Go original does the same at
     * `internal/ui/playlist.go:209`; keeping it here means callers cannot get
     * the ordering wrong.
     */
    suspend fun delete(positions: Collection<Int>) {
        positions.distinct().sortedDescending().forEach { delete(it) }
    }

    suspend fun clear() {
        commands.command("clear")
    }

    suspend fun move(from: Int, to: Int) {
        commands.command(MpdProtocol.buildCommand("move", from, to))
    }

    /** Appends a file, or a directory's contents recursively, to the queue. */
    suspend fun add(uri: String) {
        commands.command(MpdProtocol.buildCommand("add", uri))
    }

    /** Rescans the library. An empty [uri] triggers a full rescan. */
    suspend fun update(uri: String = "") {
        commands.command(MpdProtocol.buildCommand("update", uri.ifEmpty { null }))
    }

    suspend fun ping() {
        commands.command("ping")
    }

    // ── Change notification ──────────────────────────────────────────────────

    /**
     * Parks until MPD reports a change, returning the changed subsystem names.
     * Call repeatedly to keep watching — the re-subscribe pattern the Bubble
     * Tea version used for its idle listener.
     */
    suspend fun waitForChanges(): List<String> = idleConn.idle(IDLE_SUBSYSTEMS)

    override fun close() {
        idleConn.close()
        commands.close()
    }

    companion object {
        /** Passed to [play] to resume wherever playback left off. */
        const val RESUME = -1

        val IDLE_SUBSYSTEMS = listOf("player", "playlist", "options")

        suspend fun connect(host: String, port: Int = MpdConnection.DEFAULT_PORT): MpdClient {
            val commands = MpdConnection.connect(host, port)
            val idle = try {
                MpdConnection.connect(host, port, readTimeoutMs = MpdConnection.NO_READ_TIMEOUT)
            } catch (e: Throwable) {
                commands.close()
                throw e
            }
            return MpdClient(commands, idle)
        }
    }
}
