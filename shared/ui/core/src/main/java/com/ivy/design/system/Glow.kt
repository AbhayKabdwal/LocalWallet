package com.ivy.design.system

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.ivy.domain.theming.Emphasis

/**
 * Scale factors applied to the coloured-shadow ("glow") effect.
 *
 * Reducing the glow is done here, in one place, rather than by editing the ~18
 * call sites of `drawColoredShadow`. That keeps the change reversible and lets
 * the user choose the level.
 */
@Immutable
data class GlowSpec(
    val alphaScale: Float,
    val radiusScale: Float,
    val offsetScale: Float,
) {
    /** True when the glow should not be drawn at all. */
    val isNone: Boolean get() = alphaScale <= 0f

    companion object {
        fun of(emphasis: Emphasis): GlowSpec = when (emphasis) {
            // Skipped entirely - no blur drawn, no cost.
            Emphasis.Flat -> GlowSpec(alphaScale = 0f, radiusScale = 0f, offsetScale = 0f)
            // alpha .15 -> .06, radius 16dp -> 8dp, offset 8dp -> 4dp
            Emphasis.Subtle -> GlowSpec(alphaScale = 0.4f, radiusScale = 0.5f, offsetScale = 0.5f)
            // The original look.
            Emphasis.Vivid -> GlowSpec(alphaScale = 1f, radiusScale = 1f, offsetScale = 1f)
        }
    }
}

val LocalGlowSpec = compositionLocalOf { GlowSpec.of(Emphasis.Subtle) }

val LocalEmphasis = compositionLocalOf { Emphasis.Subtle }

/**
 * Turns a two-stop gradient into the brush appropriate for the current
 * emphasis: a flat fill, a compressed ramp, or the full gradient.
 *
 * Gradients are the second-largest contributor to the "very modern" look after
 * the glow, so they follow the same single lever.
 */
fun gradientBrush(
    start: Color,
    end: Color,
    emphasis: Emphasis,
): Brush = when (emphasis) {
    Emphasis.Flat -> SolidColor(start)
    Emphasis.Subtle -> Brush.horizontalGradient(
        listOf(start, lerp(start, end, 0.4f)),
    )
    Emphasis.Vivid -> Brush.horizontalGradient(listOf(start, end))
}

/**
 * The resolved palette, exposed so components outside the Material3 colour
 * scheme (income/expense semantics, charts) can use the user's colours.
 */
val LocalResolvedColors = androidx.compose.runtime.compositionLocalOf<
    com.ivy.design.system.colors.ResolvedColors
    > { error("No ResolvedColors provided") }
