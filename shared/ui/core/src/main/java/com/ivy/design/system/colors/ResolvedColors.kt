package com.ivy.design.system.colors

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ivy.domain.theming.AppTheme
import com.ivy.domain.theming.ColorHex
import com.ivy.domain.theming.Emphasis
import com.ivy.domain.theming.ThemeMode

/**
 * Every colour the app draws with, derived once from an [AppTheme].
 *
 * Both colour systems in this codebase read from this one object: the Material3
 * [androidx.compose.material3.ColorScheme] and the legacy `UI.colors`. That is
 * what keeps a user's palette consistent across new and old screens.
 */
@Immutable
data class ResolvedColors(
    val isLight: Boolean,
    val emphasis: Emphasis,

    val primary: ColorShades,
    val secondary: ColorShades,
    val income: ColorShades,
    val expense: ColorShades,
    val warning: ColorShades,

    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val gray: Color,
) {
    val onPrimary: Color get() = onColorFor(primary.primary)
    val onSecondary: Color get() = onColorFor(secondary.primary)
    val onIncome: Color get() = onColorFor(income.primary)
    val onExpense: Color get() = onColorFor(expense.primary)
}

private fun ColorHex.toColor() = Color(argb.toInt())

object PaletteResolver {

    /** Neutral surfaces. Kept close to the stock values so the app still feels familiar. */
    private val LightSurface = Color(0xFFFAFAFC)
    private val LightSurfaceVariant = Color(0xFFEBEBF0)
    private val LightOn = Color(0xFF09090A)

    private val DarkSurface = Color(0xFF09090A)
    private val DarkSurfaceVariant = Color(0xFF1C1C1F)
    private val DarkOn = Color(0xFFFAFAFC)

    private val AmoledSurface = Color(0xFF000000)
    private val AmoledSurfaceVariant = Color(0xFF121214)

    private val Gray = Color(0xFF74747A)

    fun resolve(theme: AppTheme, systemInDark: Boolean): ResolvedColors {
        val dark = when (theme.mode) {
            ThemeMode.Light -> false
            ThemeMode.Dark, ThemeMode.Amoled -> true
            ThemeMode.SystemAuto -> systemInDark
        }
        val amoled = theme.mode == ThemeMode.Amoled

        val surface = when {
            amoled -> AmoledSurface
            dark -> DarkSurface
            else -> LightSurface
        }
        val surfaceVariant = when {
            amoled -> AmoledSurfaceVariant
            dark -> DarkSurfaceVariant
            else -> LightSurfaceVariant
        }
        val onSurface = if (dark) DarkOn else LightOn

        val p = theme.palette

        // Each accent is contrast-checked against the surface it will sit on, so
        // a very pale or very dark user colour still produces readable text.
        fun accent(hex: ColorHex): ColorShades {
            val seed = hex.toColor()
            val safe = ensureContrast(seed, against = surface, minRatio = 3.0)
            return shadesFrom(safe)
        }

        return ResolvedColors(
            isLight = !dark,
            emphasis = theme.emphasis,
            primary = accent(p.primary),
            secondary = accent(p.secondary),
            income = accent(p.income),
            expense = accent(p.expense),
            warning = accent(p.warning),
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurface,
            outline = if (dark) Color(0xFF3A3A3F) else Color(0xFFCBCBD6),
            gray = Gray,
        )
    }
}
