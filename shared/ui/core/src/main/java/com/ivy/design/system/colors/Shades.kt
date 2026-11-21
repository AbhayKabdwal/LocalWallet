package com.ivy.design.system.colors

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colour maths for the theming engine.
 *
 * Deliberately pure Kotlin - no `android.graphics` and no `androidx.core`
 * ColorUtils - so the palette and contrast rules can be unit-tested on the JVM.
 * Getting these wrong means unreadable text for anyone who picks an awkward
 * accent, so they need to be cheap to test.
 */

/**
 * Derives the 7-stop ramp the app paints with from a single seed colour.
 *
 * The seed is placed on [ColorShades.primary] unchanged, which is what makes
 * "the accent I chose" appear exactly as chosen.
 */
fun shadesFrom(seed: Color): ColorShades {
    val (h, s, l) = seed.toHsl()
    fun at(lightness: Float, satScale: Float = 1f) = hslColor(
        hue = h,
        saturation = (s * satScale).coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0.04f, 0.96f),
    )

    return ColorShades(
        extraLight = at(l + (1f - l) * 0.78f, 0.80f),
        light = at(l + (1f - l) * 0.58f, 0.88f),
        kindaLight = at(l + (1f - l) * 0.30f, 0.95f),
        primary = seed,
        kindaDark = at(l * 0.80f),
        dark = at(l * 0.58f),
        extraDark = at(l * 0.36f),
    )
}

data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue

    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val lightness = (maxC + minC) / 2f

    if (delta == 0f) return Hsl(hue = 0f, saturation = 0f, lightness = lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (maxC) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Hsl(
        hue = (hue + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness,
    )
}

fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hPrime = ((hue % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs((hPrime % 2f) - 1f))
    val m = lightness - c / 2f

    val (r, g, b) = when (hPrime.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/** The packed ARGB integer for this colour. */
fun Color.toArgbInt(): Int {
    fun channel(value: Float) = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(alpha) shl 24) or
        (channel(red) shl 16) or
        (channel(green) shl 8) or
        channel(blue)
}

/** WCAG relative luminance. */
fun Color.relativeLuminance(): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/** WCAG contrast ratio between two opaque colours; always >= 1.0. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/**
 * Picks black or white for text on [background], whichever reads better.
 *
 * Replaces the hardcoded `onPrimary = White` the app used before, which is what
 * broke as soon as a user chose a pale accent.
 */
fun onColorFor(
    background: Color,
    light: Color = Color(0xFFFFFFFF),
    dark: Color = Color(0xFF0A0A0B),
): Color = if (contrastRatio(background, light) >= contrastRatio(background, dark)) light else dark

/**
 * Moves [color] away from [against] until it reaches [minRatio], so a
 * user-chosen colour can never disappear into the background it sits on.
 */
fun ensureContrast(
    color: Color,
    against: Color,
    minRatio: Double = 4.5,
    maxSteps: Int = 24,
): Color {
    if (contrastRatio(color, against) >= minRatio) return color

    val hsl = color.toHsl()
    val goDarker = against.relativeLuminance() > 0.5
    var best = color
    var bestRatio = contrastRatio(color, against)

    for (step in 1..maxSteps) {
        val delta = step * 0.04f
        val lightness = if (goDarker) hsl.lightness - delta else hsl.lightness + delta
        if (lightness < 0f || lightness > 1f) break

        val candidate = hslColor(hsl.hue, hsl.saturation, lightness)
        val ratio = contrastRatio(candidate, against)
        if (ratio > bestRatio) {
            best = candidate
            bestRatio = ratio
        }
        if (ratio >= minRatio) return candidate
    }
    return best
}
