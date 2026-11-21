package com.ivy.appearance

import androidx.compose.runtime.Immutable
import com.ivy.domain.theming.AppTheme
import com.ivy.domain.theming.Emphasis
import com.ivy.domain.theming.Palette
import com.ivy.domain.theming.ThemeMode
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class AppearanceState(
    val theme: AppTheme,
    val presets: ImmutableList<Palette>,
    val supportsWallpaperColors: Boolean,
    val shareCode: String?,
)

sealed interface AppearanceEvent {
    data class SetMode(val mode: ThemeMode) : AppearanceEvent
    data class SetEmphasis(val emphasis: Emphasis) : AppearanceEvent
    data class SetPreset(val palette: Palette) : AppearanceEvent
    data class SetPrimary(val argb: Long) : AppearanceEvent
    data class SetSecondary(val argb: Long) : AppearanceEvent
    data class SetIncome(val argb: Long) : AppearanceEvent
    data class SetExpense(val argb: Long) : AppearanceEvent
    data class SetWallpaperColors(val enabled: Boolean) : AppearanceEvent
    data class ImportPalette(val code: String) : AppearanceEvent
    data object ShowShareCode : AppearanceEvent
    data object DismissShareCode : AppearanceEvent
    data object Reset : AppearanceEvent
}
