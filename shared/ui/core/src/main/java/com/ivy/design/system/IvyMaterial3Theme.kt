package com.ivy.design.system

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ivy.design.system.colors.PaletteResolver
import com.ivy.design.system.colors.ResolvedColors
import com.ivy.design.system.colors.onColorFor
import com.ivy.domain.theming.AppTheme
import com.ivy.domain.theming.ThemeMode
import android.os.Build

/**
 * Applies the user's [AppTheme] to the Material3 layer.
 *
 * The same [ResolvedColors] is also bridged into the legacy `UI.colors` system
 * by `IvyTheme`, so both halves of the app stay in sync.
 */
@Composable
fun IvyMaterial3Theme(
    theme: AppTheme,
    colors: ResolvedColors = PaletteResolver.resolve(theme, isSystemInDarkTheme()),
    content: @Composable () -> Unit,
) {
    val dynamic = theme.useWallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val scheme = when {
        dynamic && colors.isLight -> dynamicLightColorScheme(context)
        dynamic -> dynamicDarkColorScheme(context)
        else -> colors.toColorScheme()
    }

    CompositionLocalProvider(
        LocalGlowSpec provides GlowSpec.of(theme.emphasis),
        LocalEmphasis provides theme.emphasis,
        LocalResolvedColors provides colors,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Kept for the legacy call path and for Paparazzi previews that only have a
 * light/dark flag to hand.
 */
@Composable
fun IvyMaterial3Theme(
    isTrueBlack: Boolean,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val mode = when {
        isTrueBlack -> ThemeMode.Amoled
        dark -> ThemeMode.Dark
        else -> ThemeMode.Light
    }
    IvyMaterial3Theme(theme = AppTheme.Default.copy(mode = mode), content = content)
}

fun ResolvedColors.toColorScheme(): ColorScheme = ColorScheme(
    primary = primary.primary,
    onPrimary = onPrimary,
    primaryContainer = primary.light,
    onPrimaryContainer = onColorFor(primary.light),
    inversePrimary = primary.dark,

    secondary = secondary.primary,
    onSecondary = onSecondary,
    secondaryContainer = secondary.light,
    onSecondaryContainer = onColorFor(secondary.light),

    tertiary = income.primary,
    onTertiary = onIncome,
    tertiaryContainer = income.light,
    onTertiaryContainer = onColorFor(income.light),

    error = expense.primary,
    onError = onExpense,
    errorContainer = expense.light,
    onErrorContainer = onColorFor(expense.light),

    background = surface,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = primary.primary,
    inverseSurface = onSurface,
    inverseOnSurface = surface,

    outline = outline,
    outlineVariant = outline,
    scrim = Color(0xCC000000),
)
