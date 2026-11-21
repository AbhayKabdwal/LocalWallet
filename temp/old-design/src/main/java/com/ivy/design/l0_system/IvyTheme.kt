package com.ivy.design.l0_system

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ivy.design.api.IvyDesign
import com.ivy.design.system.IvyMaterial3Theme
import com.ivy.design.system.ThemeAccents
import com.ivy.design.system.colors.PaletteResolver
import com.ivy.domain.theming.AppTheme

@Deprecated("Old design system. Use `:ivy-design` and Material3")
val LocalIvyColors = compositionLocalOf<IvyColors> { error("No IvyColors") }

@Deprecated("Old design system. Use `:ivy-design` and Material3")
val LocalIvyTypography = compositionLocalOf<IvyTypography> { error("No IvyTypography") }

@Deprecated("Old design system. Use `:ivy-design` and Material3")
val LocalIvyShapes = compositionLocalOf<IvyShapes> { error("No IvyShapes") }

@Deprecated("Old design system. Use `:ivy-design` and Material3")
object UI {
    val colors: IvyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIvyColors.current

    val typo: IvyTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalIvyTypography.current

    val shapes: IvyShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalIvyShapes.current
}

/**
 * Provides both colour systems from a single [AppTheme].
 *
 * The legacy `UI.colors` and the Material3 scheme are derived from the same
 * resolved palette, which is what keeps a user-chosen accent consistent across
 * old and new screens.
 */
@Composable
fun IvyTheme(
    appTheme: AppTheme,
    design: IvyDesign,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val resolved = remember(appTheme, isDarkTheme) {
        PaletteResolver.resolve(appTheme, isDarkTheme)
    }
    val colors = remember(resolved) { resolved.asLegacyIvyColors() }

    // Legacy screens read top-level colour constants directly rather than
    // UI.colors; this is what makes those follow the user's palette too.
    //
    // It has to be a SideEffect, not a write during composition: updating
    // snapshot state mid-composition is a backwards write, so readers that had
    // already composed kept the old colours until the app was restarted.
    // Running it after composition settles invalidates them properly.
    SideEffect {
        ThemeAccents.update(resolved)
    }
    val typography = design.typography()
    val shapes = design.shapes()

    CompositionLocalProvider(
        LocalIvyColors provides colors,
        LocalIvyTypography provides typography,
        LocalIvyShapes provides shapes
    ) {
        val view = LocalView.current
        if (!view.isInEditMode && view.context is Activity) {
            SideEffect {
                val window = (view.context as Activity).window
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    colors.isLight
            }
        }

        IvyMaterial3Theme(
            theme = appTheme,
            colors = resolved,
            content = content,
        )
    }
}
