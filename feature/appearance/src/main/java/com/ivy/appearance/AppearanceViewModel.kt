package com.ivy.appearance

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.domain.theming.AppTheme
import com.ivy.domain.theming.ColorHex
import com.ivy.domain.theming.Palette
import com.ivy.domain.theming.PalettePresets
import com.ivy.domain.theming.ThemeRepository
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ComposeViewModel<AppearanceState, AppearanceEvent>() {

    private var shareCode by mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): AppearanceState {
        val theme by themeRepository.theme.collectAsState(AppTheme.Default)
        return AppearanceState(
            theme = theme,
            presets = PalettePresets.all.toImmutableList(),
            supportsWallpaperColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            shareCode = shareCode,
        )
    }

    override fun onEvent(event: AppearanceEvent) {
        when (event) {
            is AppearanceEvent.SetMode -> update { it.copy(mode = event.mode) }
            is AppearanceEvent.SetEmphasis -> update { it.copy(emphasis = event.emphasis) }
            is AppearanceEvent.SetPreset -> update { it.copy(palette = event.palette) }
            is AppearanceEvent.SetWallpaperColors ->
                update { it.copy(useWallpaperColors = event.enabled) }

            is AppearanceEvent.SetPrimary -> updatePalette { it.copy(primary = ColorHex(event.argb)) }
            is AppearanceEvent.SetSecondary ->
                updatePalette { it.copy(secondary = ColorHex(event.argb)) }

            is AppearanceEvent.SetIncome -> updatePalette { it.copy(income = ColorHex(event.argb)) }
            is AppearanceEvent.SetExpense ->
                updatePalette { it.copy(expense = ColorHex(event.argb)) }

            is AppearanceEvent.ImportPalette -> importPalette(event.code)
            AppearanceEvent.ShowShareCode -> showShareCode()
            AppearanceEvent.DismissShareCode -> shareCode = null
            AppearanceEvent.Reset -> viewModelScope.launch {
                themeRepository.set(AppTheme.Default)
            }
        }
    }

    private fun update(transform: (AppTheme) -> AppTheme) {
        viewModelScope.launch { themeRepository.update(transform) }
    }

    /**
     * Any individual colour change turns the palette into a "custom" one, so a
     * preset name never lies about what is actually on screen.
     */
    private fun updatePalette(transform: (Palette) -> Palette) {
        update { theme ->
            val edited = transform(theme.palette)
            theme.copy(
                palette = edited.copy(id = CUSTOM_ID, name = CUSTOM_NAME),
            )
        }
    }

    private fun showShareCode() {
        viewModelScope.launch {
            shareCode = themeRepository.exportPalette(themeRepository.current().palette)
        }
    }

    private fun importPalette(code: String) {
        viewModelScope.launch {
            themeRepository.importPalette(code)?.let { imported ->
                themeRepository.update { it.copy(palette = imported) }
            }
        }
    }

    private companion object {
        const val CUSTOM_ID = "custom"
        const val CUSTOM_NAME = "Custom"
    }
}
