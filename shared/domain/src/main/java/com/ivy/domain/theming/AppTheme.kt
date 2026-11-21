package com.ivy.domain.theming

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * An ARGB color stored as a Long so the serialized palette stays human-readable
 * and easy to share as text.
 */
@JvmInline
@Serializable
value class ColorHex(val argb: Long) {
    companion object {
        fun of(argb: Long) = ColorHex(argb)
    }
}

@Serializable
enum class ThemeMode {
    Light,
    Dark,
    Amoled,
    SystemAuto,
}

/**
 * How much visual "lift" the UI uses: coloured glows, gradients and elevation.
 *
 * Accent hues are identical across all three levels - only the intensity of the
 * decoration changes.
 */
@Serializable
enum class Emphasis {
    /** No coloured shadows, flat fills. */
    Flat,

    /** Noticeably softened glow and a compressed gradient ramp. */
    Subtle,

    /** The original Ivy Wallet look. */
    Vivid,
}

/**
 * The user-editable colour set. Every colour in the app is derived from these
 * few seeds, so a palette stays small enough to share as a short code.
 */
@Serializable
@Immutable
data class Palette(
    val id: String,
    val name: String,
    val primary: ColorHex,
    val secondary: ColorHex,
    val income: ColorHex,
    val expense: ColorHex,
    val warning: ColorHex,
) {
    companion object {
        /**
         * The default accents. `0xFF6B4DFF` is the purple the legacy screens
         * have always used; the Material3 layer used a slightly different one,
         * and this unifies both on the colour users actually saw.
         */
        val Default = Palette(
            id = "ivy",
            name = "Default",
            primary = ColorHex(0xFF6B4DFF),
            secondary = ColorHex(0xFF14CC9E),
            income = ColorHex(0xFF14CC9E),
            expense = ColorHex(0xFFFF4060),
            warning = ColorHex(0xFFF29F30),
        )
    }
}

@Serializable
@Immutable
data class AppTheme(
    val mode: ThemeMode = ThemeMode.SystemAuto,
    val palette: Palette = Palette.Default,
    val useWallpaperColors: Boolean = false,
    val emphasis: Emphasis = Emphasis.Subtle,
) {
    companion object {
        val Default = AppTheme()
    }
}
