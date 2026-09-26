package com.nickorton.ghac.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Every colour a theme needs, named by role rather than by where it is used.
 *
 * The volume colours are chosen per theme rather than shared: stock ANSI green
 * and red clashed with the accent of every palette they were placed beside. A
 * theme's unmuted colour is its own accent, so a live speaker reads as part of
 * the app, and its muted colour is the theme's own warm "off" tone.
 */
data class GhacPalette(
    val background: Color,
    val surface: Color,
    val barBackground: Color,
    val foreground: Color,
    val secondary: Color,
    val accent: Color,
    val progressEmpty: Color,
    val volumeMuted: Color,
    val onAccent: Color = Color.Black,
    val volumeUnmuted: Color = accent,
)

/**
 * The selectable themes, persisted by [name] — renaming an entry resets anyone
 * who had picked it to [Default].
 *
 * All are dark, for the reason given on [GhacTheme]. Apart from Turquoise
 * (ghac's original palette) and Monochrome, each follows the published
 * palette of the editor theme it is named after.
 */
enum class GhacThemeChoice(val displayName: String, val palette: GhacPalette) {
    Turquoise(
        "Turquoise",
        GhacPalette(
            background = Color(0xFF121212),
            surface = Color(0xFF1C1C1C),
            barBackground = Color(0xFF3A3A3A), // xterm 237
            foreground = Color(0xFFEEEEEE), // xterm 255
            secondary = Color(0xFF8A8A8A), // xterm 245
            accent = Color(0xFF00CDCD), // ANSI cyan
            progressEmpty = Color(0xFF585858), // xterm 240
            volumeMuted = Color(0xFFE5786D), // soft coral, cyan's complement
        ),
    ),
    Nord(
        "Nord",
        GhacPalette(
            background = Color(0xFF2E3440),
            surface = Color(0xFF3B4252),
            barBackground = Color(0xFF434C5E),
            foreground = Color(0xFFECEFF4),
            secondary = Color(0xFFA3ABBA),
            accent = Color(0xFF88C0D0),
            progressEmpty = Color(0xFF4C566A),
            volumeMuted = Color(0xFFBF616A),
        ),
    ),
    Gruvbox(
        "Gruvbox",
        GhacPalette(
            background = Color(0xFF1D2021),
            surface = Color(0xFF282828),
            barBackground = Color(0xFF3C3836),
            foreground = Color(0xFFEBDBB2),
            secondary = Color(0xFFA89984),
            accent = Color(0xFFFABD2F),
            progressEmpty = Color(0xFF504945),
            volumeMuted = Color(0xFFFB4934),
        ),
    ),
    Dracula(
        "Dracula",
        GhacPalette(
            background = Color(0xFF21222C),
            surface = Color(0xFF282A36),
            barBackground = Color(0xFF44475A),
            foreground = Color(0xFFF8F8F2),
            secondary = Color(0xFFA4A9C9),
            accent = Color(0xFFBD93F9),
            progressEmpty = Color(0xFF565A73),
            volumeMuted = Color(0xFFFF6E6E),
        ),
    ),
    Solarized(
        "Solarized",
        GhacPalette(
            background = Color(0xFF002B36),
            surface = Color(0xFF073642),
            barBackground = Color(0xFF0B4452),
            foreground = Color(0xFFEEE8D5),
            secondary = Color(0xFF93A1A1),
            accent = Color(0xFF268BD2),
            progressEmpty = Color(0xFF3E5F68),
            volumeMuted = Color(0xFFDC322F),
            onAccent = Color(0xFFFDF6E3),
        ),
    ),
    Catppuccin(
        "Catppuccin",
        GhacPalette(
            background = Color(0xFF11111B),
            surface = Color(0xFF1E1E2E),
            barBackground = Color(0xFF313244),
            foreground = Color(0xFFCDD6F4),
            secondary = Color(0xFFA6ADC8),
            accent = Color(0xFFA6E3A1),
            progressEmpty = Color(0xFF45475A),
            volumeMuted = Color(0xFFF38BA8),
        ),
    ),
    Monochrome(
        "Monochrome",
        GhacPalette(
            background = Color(0xFF0E0E0E),
            surface = Color(0xFF1A1A1A),
            barBackground = Color(0xFF2E2E2E),
            foreground = Color(0xFFF0F0F0),
            secondary = Color(0xFF9A9A9A),
            accent = Color(0xFFE0E0E0),
            progressEmpty = Color(0xFF444444),
            // Muted is shown by dimming rather than by hue: any colour at all
            // would be the only one on screen and shout louder than "on".
            volumeMuted = Color(0xFF777777),
        ),
    );

    companion object {
        val Default = Turquoise

        fun fromName(name: String?): GhacThemeChoice =
            entries.firstOrNull { it.name == name } ?: Default
    }
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

private fun GhacPalette.extras() = GhacExtraColors(
    volumeUnmuted = volumeUnmuted,
    volumeMuted = volumeMuted,
    progressEmpty = progressEmpty,
    nowPlayingBackground = barBackground,
)

val LocalGhacColors = staticCompositionLocalOf { GhacThemeChoice.Default.palette.extras() }

/**
 * Fills in every role Material components read, including the surface
 * containers: left unset, cards and the navigation bar fall back to Material's
 * baseline purple-tinted greys, which match none of these palettes.
 */
private fun GhacPalette.colorScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = lerp(surface, accent, 0.35f),
    onPrimaryContainer = foreground,
    secondary = secondary,
    onSecondary = Color.Black,
    secondaryContainer = lerp(barBackground, accent, 0.25f),
    onSecondaryContainer = foreground,
    background = background,
    onBackground = foreground,
    surface = surface,
    onSurface = foreground,
    surfaceVariant = barBackground,
    onSurfaceVariant = secondary,
    surfaceContainerLowest = background,
    surfaceContainerLow = lerp(background, surface, 0.5f),
    surfaceContainer = surface,
    surfaceContainerHigh = lerp(surface, barBackground, 0.5f),
    surfaceContainerHighest = barBackground,
    outline = progressEmpty,
    outlineVariant = barBackground,
    error = volumeMuted,
    onError = Color.Black,
)

/**
 * ghac is always dark, regardless of the system setting.
 *
 * Every theme is built for a dark ground — the palettes assume light text, and
 * the now-playing bar's `bar_bg` is a dark grey with no light-mode counterpart.
 * Deriving a light scheme would mean inventing colours the original never had,
 * and the result looked wrong in practice: a dark now-playing bar stranded on a
 * white page.
 */
@Composable
fun GhacTheme(
    choice: GhacThemeChoice = GhacThemeChoice.Default,
    content: @Composable () -> Unit,
) {
    val palette = choice.palette
    CompositionLocalProvider(LocalGhacColors provides palette.extras()) {
        MaterialTheme(
            colorScheme = palette.colorScheme(),
            content = content,
        )
    }
}
