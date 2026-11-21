package com.ivy.domain.theming

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivy.data.datastore.IvyDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the theme as JSON in DataStore rather than as Room columns.
 *
 * That keeps the whole palette in one place, makes it shareable as a short
 * string, and means a malformed theme can fall back to the default without ever
 * putting the user's financial data at risk.
 */
@Singleton
class ThemeRepository @Inject constructor(
    private val dataStore: IvyDataStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val theme: Flow<AppTheme> = dataStore.data.map { prefs ->
        prefs[KEY]?.let(::decode) ?: AppTheme.Default
    }

    /**
     * The theme as a hot, process-lifetime [StateFlow].
     *
     * This deliberately does not live on a ViewModel. An earlier version
     * exposed it via `stateIn(viewModelScope)` on the root ViewModel, and when
     * that scope was cancelled the flow silently stopped producing: the palette
     * still saved and still applied on next launch, but nothing repainted while
     * the app was running. The theme is process-wide state, so it is scoped to
     * the process.
     */
    val themeState: StateFlow<AppTheme> = theme.stateIn(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        started = SharingStarted.Eagerly,
        initialValue = AppTheme.Default,
    )

    suspend fun current(): AppTheme = theme.first()

    suspend fun update(transform: (AppTheme) -> AppTheme) {
        val updated = transform(current())
        dataStore.edit { it[KEY] = json.encodeToString(AppTheme.serializer(), updated) }
    }

    suspend fun set(theme: AppTheme) {
        dataStore.edit { it[KEY] = json.encodeToString(AppTheme.serializer(), theme) }
    }

    /** Serializes the palette only, for the "share theme" code. */
    fun exportPalette(palette: Palette): String =
        json.encodeToString(Palette.serializer(), palette)

    fun importPalette(raw: String): Palette? = try {
        json.decodeFromString(Palette.serializer(), raw.trim())
    } catch (e: SerializationException) {
        Timber.w(e, "Could not parse shared palette")
        null
    }

    private fun decode(raw: String): AppTheme? = try {
        json.decodeFromString(AppTheme.serializer(), raw)
    } catch (e: SerializationException) {
        // A theme is cosmetic - never let a bad one block app start.
        Timber.w(e, "Stored theme was unreadable, falling back to default")
        null
    }

    companion object {
        private val KEY = stringPreferencesKey("app_theme_v1")
    }
}
