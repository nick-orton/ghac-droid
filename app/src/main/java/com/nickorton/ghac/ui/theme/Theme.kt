package com.nickorton.ghac.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ghac's "default" theme, ported from `internal/ui/themes.toml`.
 *
 * The TUI expressed colours as xterm-256 indices so they would adapt to the
 * user's terminal palette; on Android they become fixed sRGB values, using the
 * hex equivalents recorded in that file's comments. The remaining seven themes
 * are left for a future picker.
 */
private object Palette {
    val BarBackground = Color(0xFF3A3A3A) // xterm 237
    val BarForeground = Color(0xFFEEEEEE) // xterm 255
    val Accent = Color(0xFF00CDCD) // ANSI cyan
    val ProgressEmpty = Color(0xFF585858) // xterm 240
    val Secondary = Color(0xFF8A8A8A) // xterm 245
    val VolumeUnmuted = Color(0xFF00CD00) // ANSI green
    val VolumeMuted = Color(0xFFCD0000) // ANSI red

    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1C1C1C)
}

/**
 * Roles that have no Material equivalent. Material's scheme has no notion of
 * "a muted speaker" or "the unfilled part of a progress bar", so these ride
 * alongside it rather than being forced into `primary`/`secondary`.
 */
data class GhacExtraColors(
    val volumeUnmuted: Color,
    val volumeMuted: Color,
    val progressEmpty: Color,
    val nowPlayingBackground: Color,
)

val LocalGhacColors = staticCompositionLocalOf {
    GhacExtraColors(
        volumeUnmuted = Palette.VolumeUnmuted,
        volumeMuted = Palette.VolumeMuted,
        progressEmpty = Palette.ProgressEmpty,
        nowPlayingBackground = Palette.BarBackground,
    )
}

private val GhacDarkColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.Black,
    secondary = Palette.Secondary,
    onSecondary = Color.Black,
    background = Palette.Background,
    onBackground = Palette.BarForeground,
    surface = Palette.Surface,
    onSurface = Palette.BarForeground,
    surfaceVariant = Palette.BarBackground,
    onSurfaceVariant = Palette.Secondary,
    error = Palette.VolumeMuted,
)

/**
 * ghac is always dark, regardless of the system setting.
 *
 * Every one of the eight themes in `themes.toml` is built for a dark terminal —
 * the palette assumes light text on a dark ground, and the now-playing bar's
 * `bar_bg` is a dark grey with no light-mode counterpart. Deriving a light
 * scheme would mean inventing colours the original never had, and the result
 * looked wrong in practice: a dark now-playing bar stranded on a white page.
 */
@Composable
fun GhacTheme(
    content: @Composable () -> Unit,
) {
    val extras = GhacExtraColors(
        volumeUnmuted = Palette.VolumeUnmuted,
        volumeMuted = Palette.VolumeMuted,
        progressEmpty = Palette.ProgressEmpty,
        nowPlayingBackground = Palette.BarBackground,
    )

    CompositionLocalProvider(LocalGhacColors provides extras) {
        MaterialTheme(
            colorScheme = GhacDarkColors,
            content = content,
        )
    }
}
