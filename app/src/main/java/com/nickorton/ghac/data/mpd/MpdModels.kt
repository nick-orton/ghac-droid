package com.nickorton.ghac.data.mpd

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/** Metadata for a single track. */
data class Song(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val file: String = "",
) {
    /**
     * What to show in a list row. MPD leaves tags empty for untagged files, so
     * fall back to the basename of the URI the way the TUI's `File` field did.
     */
    val displayTitle: String
        get() = title.ifBlank { file.substringAfterLast('/') }

    val isEmpty: Boolean
        get() = title.isBlank() && artist.isBlank() && album.isBlank() && file.isBlank()
}

/** One song in the MPD playback queue. */
data class PlaylistEntry(
    val song: Song,
    /** 0-indexed position in the queue. */
    val pos: Int,
)

/** One item in an MPD directory listing. */
data class DirEntry(
    /** Basename, for display. */
    val name: String,
    /** Full MPD URI, relative to the music root. */
    val path: String,
    val isDir: Boolean,
    /** Populated for files; empty for directories. */
    val song: Song = Song(),
)

enum class PlayState {
    PLAY, PAUSE, STOP;

    companion object {
        fun fromMpd(s: String?): PlayState = when (s) {
            "play" -> PLAY
            "pause" -> PAUSE
            else -> STOP
        }
    }
}

/** Complete player state, assembled from MPD's `status` and `currentsong`. */
data class PlayerState(
    val state: PlayState = PlayState.STOP,
    val song: Song = Song(),
    val elapsed: Duration = ZERO,
    val total: Duration = ZERO,
    /** 0-indexed queue position of the current song; [NO_SONG] when nothing is playing. */
    val songPos: Int = NO_SONG,
    val random: Boolean = false,
) {
    val isPlaying: Boolean get() = state == PlayState.PLAY

    companion object {
        /**
         * Sentinel for "nothing playing". MPD omits `song` from its status
         * response when the queue is empty or it is stopped with no position.
         */
        const val NO_SONG = -1
    }
}
