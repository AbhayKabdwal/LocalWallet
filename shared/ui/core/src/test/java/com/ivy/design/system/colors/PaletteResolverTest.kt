package com.ivy.design.system.colors

import com.ivy.domain.theming.AppTheme
import com.ivy.domain.theming.ColorHex
import com.ivy.domain.theming.Emphasis
import com.ivy.domain.theming.Palette
import com.ivy.domain.theming.PalettePresets
import com.ivy.domain.theming.ThemeMode
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The theme lets a user pick any colour, so the resolver - not the screens - is
 * responsible for keeping text readable. These tests are the guard rail for
 * that: a pale yellow accent must not be allowed to produce white-on-yellow.
 */
class PaletteResolverTest {

    private fun theme(palette: Palette, mode: ThemeMode) = AppTheme(
        mode = mode,
        palette = palette,
        useWallpaperColors = false,
        emphasis = Emphasis.Subtle,
    )

    @Test
    fun `every preset is readable in every mode`() {
        for (preset in PalettePresets.all) {
            for (mode in ThemeMode.entries) {
                val colors = PaletteResolver.resolve(
                    theme = theme(preset, mode),
                    systemInDark = mode == ThemeMode.SystemAuto,
                )
                assertReadable(colors, "${preset.name}/$mode")
            }
        }
    }

    @Test
    fun `awkward user colours are still readable`() {
        // Deliberately bad picks: near-white, near-black, and saturated yellow.
        val awkward = listOf(
            0xFFFFFFF0,
            0xFF050505,
            0xFFFFFF00,
            0xFF00FF00,
            0xFF808080,
        )
        for (argb in awkward) {
            for (mode in ThemeMode.entries) {
                val palette = Palette.Default.copy(
                    id = "custom",
                    primary = ColorHex(argb),
                    income = ColorHex(argb),
                    expense = ColorHex(argb),
                )
                val colors = PaletteResolver.resolve(
                    theme = theme(palette, mode),
                    systemInDark = false,
                )
                assertReadable(colors, "${argb.toString(16)}/$mode")
            }
        }
    }

    @Test
    fun `body text meets WCAG AA against the surface`() {
        for (mode in ThemeMode.entries) {
            val colors = PaletteResolver.resolve(theme(Palette.Default, mode), systemInDark = false)
            val ratio = contrastRatio(colors.onSurface, colors.surface)
            (ratio >= AA_TEXT) shouldBe true
        }
    }

    @Test
    fun `the seed colour survives as the primary shade`() {
        // "Keep my accent" only means something if the chosen colour is the one
        // actually used, not an approximation of it.
        val seed = 0xFF6B4DFF
        val colors = PaletteResolver.resolve(
            theme = theme(Palette.Default.copy(primary = ColorHex(seed)), ThemeMode.Light),
            systemInDark = false,
        )
        colors.primary.primary.toArgbInt() shouldBe seed.toInt()
    }

    @Test
    fun `emphasis does not change any accent colour`() {
        val vivid = PaletteResolver.resolve(
            theme = theme(Palette.Default, ThemeMode.Light).copy(emphasis = Emphasis.Vivid),
            systemInDark = false,
        )
        val flat = PaletteResolver.resolve(
            theme = theme(Palette.Default, ThemeMode.Light).copy(emphasis = Emphasis.Flat),
            systemInDark = false,
        )
        flat.primary shouldBe vivid.primary
        flat.income shouldBe vivid.income
        flat.expense shouldBe vivid.expense
        flat.surface shouldBe vivid.surface
    }

    @Test
    fun `shades run light to dark`() {
        val shades = shadesFrom(androidx.compose.ui.graphics.Color(0xFF6B4DFF))
        val ordered = listOf(
            shades.extraLight,
            shades.light,
            shades.kindaLight,
            shades.kindaDark,
            shades.dark,
            shades.extraDark,
        ).map { it.relativeLuminance() }

        // Monotonically decreasing luminance.
        ordered.zipWithNext().all { (a, b) -> a >= b } shouldBe true
    }

    private fun assertReadable(colors: ResolvedColors, label: String) {
        val pairs = listOf(
            "onSurface" to (colors.onSurface to colors.surface),
            "onPrimary" to (colors.onPrimary to colors.primary.primary),
            "onIncome" to (colors.onIncome to colors.income.primary),
            "onExpense" to (colors.onExpense to colors.expense.primary),
            "onSecondary" to (colors.onSecondary to colors.secondary.primary),
        )
        for ((name, pair) in pairs) {
            val (fg, bg) = pair
            val ratio = contrastRatio(fg, bg)
            check(ratio >= AA_LARGE_TEXT) {
                "$label: $name contrast was $ratio, below $AA_LARGE_TEXT"
            }
        }
    }

    private companion object {
        /** WCAG AA for body text. */
        const val AA_TEXT = 4.5

        /**
         * WCAG AA for large text and UI components. Button labels on an accent
         * fill are large/bold, so this is the right bar for the `onX` pairs.
         */
        const val AA_LARGE_TEXT = 3.0
    }
}
