package com.ivy.design.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.ivy.design.system.colors.ResolvedColors

/**
 * Live values for the legacy top-level colour constants.
 *
 * Roughly 68 files in the legacy UI import colours like `Ivy`, `Green` and
 * `Red` directly as compile-time constants instead of reading `UI.colors`.
 * Those call sites bypass the theme entirely, which is why changing the
 * palette appeared to do nothing on most screens.
 *
 * Rather than rewrite every one of them, the constants became getters onto
 * this holder. The fields are Compose state, so reading one inside a
 * composable subscribes to it and the screen recomposes when the palette
 * changes - no call site needed to change.
 *
 * Only the semantic accents live here. The numbered shades (Green2, Red3,
 * Purple1 and friends) stay fixed, because they are the swatches offered in
 * the category and account colour pickers: retinting those would silently
 * change colours the user picked by hand.
 */
object ThemeAccents {
    var primary by mutableStateOf(Color(0xFF6B4DFF))
        private set
    var primaryLight by mutableStateOf(Color(0xFFD5CCFF))
        private set
    var primaryDark by mutableStateOf(Color(0xFF352680))
        private set

    var green by mutableStateOf(Color(0xFF14CC9E))
        private set
    var greenLight by mutableStateOf(Color(0xFFAAF2E0))
        private set
    var greenDark by mutableStateOf(Color(0xFF0A664F))
        private set

    var red by mutableStateOf(Color(0xFFFF4060))
        private set
    var redLight by mutableStateOf(Color(0xFFFFCCD5))
        private set
    var redDark by mutableStateOf(Color(0xFF801919))
        private set

    var orange by mutableStateOf(Color(0xFFF29F30))
        private set
    var orangeLight by mutableStateOf(Color(0xFFFFDEB3))
        private set
    var orangeDark by mutableStateOf(Color(0xFF734B17))
        private set

    var blue by mutableStateOf(Color(0xFF4CC3FF))
        private set
    var blueLight by mutableStateOf(Color(0xFFB3E6FF))
        private set
    var blueDark by mutableStateOf(Color(0xFF266280))
        private set

    fun update(colors: ResolvedColors) {
        primary = colors.primary.primary
        primaryLight = colors.primary.extraLight
        primaryDark = colors.primary.extraDark

        green = colors.income.primary
        greenLight = colors.income.extraLight
        greenDark = colors.income.extraDark

        red = colors.expense.primary
        redLight = colors.expense.extraLight
        redDark = colors.expense.extraDark

        orange = colors.warning.primary
        orangeLight = colors.warning.extraLight
        orangeDark = colors.warning.extraDark

        blue = colors.secondary.primary
        blueLight = colors.secondary.extraLight
        blueDark = colors.secondary.extraDark
    }
}
