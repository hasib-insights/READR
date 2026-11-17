package com.readr.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.readr.app.domain.model.AppPrefs
import com.readr.app.domain.model.AppThemeMode
import com.readr.app.domain.model.LibrarySort
import com.readr.app.domain.model.LibraryViewMode
import com.readr.app.domain.model.PaperTheme
import com.readr.app.domain.model.ReaderFont
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.TextureIntensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "readr_prefs")

/**
 * DataStore-backed preferences. Defaults encode the product requirement that
 * READR opens with Warm Cream Paper and a subtle LOW-intensity paper grain.
 */
class PreferencesRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val paperTheme = stringPreferencesKey("paper_theme")
        val textureIntensity = stringPreferencesKey("texture_intensity")
        val fontSize = intPreferencesKey("font_size")
        val font = stringPreferencesKey("font_family")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val marginDp = intPreferencesKey("margin_dp")
        val gestureEnabled = booleanPreferencesKey("gesture_enabled")
        val gestureSensitivity = floatPreferencesKey("gesture_sensitivity")
        val systemBrightness = booleanPreferencesKey("system_brightness")
        val warmFilter = booleanPreferencesKey("warm_filter")
        val customBackground = longPreferencesKey("custom_background")
        val customText = longPreferencesKey("custom_text")
        val pdfDarkFilter = booleanPreferencesKey("pdf_dark_filter")
        val pdfFitWidth = booleanPreferencesKey("pdf_fit_width")
        val appThemeMode = stringPreferencesKey("app_theme_mode")
        val libraryView = stringPreferencesKey("library_view")
        val librarySort = stringPreferencesKey("library_sort")
    }

    private inline fun <reified E : Enum<E>> Preferences.getEnum(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        fallback: E,
    ): E = this[key]?.let { name -> runCatching { enumValueOf<E>(name) }.getOrNull() } ?: fallback

    val readingPrefs: Flow<ReadingPrefs> = context.dataStore.data.map { p ->
        ReadingPrefs(
            paperTheme = p.getEnum(Keys.paperTheme, PaperTheme.WARM_CREAM),
            textureIntensity = p.getEnum(Keys.textureIntensity, TextureIntensity.LOW),
            fontSizeSp = p[Keys.fontSize] ?: 17,
            font = p.getEnum(Keys.font, ReaderFont.SERIF),
            lineSpacing = p[Keys.lineSpacing] ?: 1.4f,
            marginDp = p[Keys.marginDp] ?: 24,
            brightnessGestureEnabled = p[Keys.gestureEnabled] ?: true,
            gestureSensitivity = p[Keys.gestureSensitivity] ?: 1.0f,
            systemBrightnessMode = p[Keys.systemBrightness] ?: false,
            warmFilter = p[Keys.warmFilter] ?: false,
            customBackground = p[Keys.customBackground]?.takeIf { it != -1L },
            customTextColor = p[Keys.customText]?.takeIf { it != -1L },
            pdfDarkFilter = p[Keys.pdfDarkFilter] ?: false,
            pdfFitWidth = p[Keys.pdfFitWidth] ?: true,
        )
    }

    val appPrefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            appThemeMode = p.getEnum(Keys.appThemeMode, AppThemeMode.SYSTEM),
            libraryView = p.getEnum(Keys.libraryView, LibraryViewMode.GRID),
            librarySort = p.getEnum(Keys.librarySort, LibrarySort.LAST_OPENED),
        )
    }

    suspend fun setPaperTheme(value: PaperTheme) = edit { it[Keys.paperTheme] = value.name }

    /**
     * Selects a paper theme and clears any Eye Comfort custom colours in the
     * same atomic edit. Without this, a previously chosen custom colour would
     * silently override the tapped paper and the selection would look dead.
     */
    suspend fun applyPaperTheme(value: PaperTheme) = edit {
        it[Keys.paperTheme] = value.name
        it[Keys.customBackground] = -1L
        it[Keys.customText] = -1L
    }

    suspend fun setTextureIntensity(value: TextureIntensity) = edit { it[Keys.textureIntensity] = value.name }
    suspend fun setFontSize(value: Int) = edit { it[Keys.fontSize] = value }
    suspend fun setFont(value: ReaderFont) = edit { it[Keys.font] = value.name }
    suspend fun setLineSpacing(value: Float) = edit { it[Keys.lineSpacing] = value }
    suspend fun setMarginDp(value: Int) = edit { it[Keys.marginDp] = value }
    suspend fun setGestureEnabled(value: Boolean) = edit { it[Keys.gestureEnabled] = value }
    suspend fun setGestureSensitivity(value: Float) = edit { it[Keys.gestureSensitivity] = value }
    suspend fun setSystemBrightnessMode(value: Boolean) = edit { it[Keys.systemBrightness] = value }
    suspend fun setWarmFilter(value: Boolean) = edit { it[Keys.warmFilter] = value }
    suspend fun setCustomBackground(value: Long?) = edit { it[Keys.customBackground] = value ?: -1L }
    suspend fun setCustomTextColor(value: Long?) = edit { it[Keys.customText] = value ?: -1L }
    suspend fun setPdfDarkFilter(value: Boolean) = edit { it[Keys.pdfDarkFilter] = value }
    suspend fun setPdfFitWidth(value: Boolean) = edit { it[Keys.pdfFitWidth] = value }
    suspend fun setAppThemeMode(value: AppThemeMode) = edit { it[Keys.appThemeMode] = value.name }
    suspend fun setLibraryView(value: LibraryViewMode) = edit { it[Keys.libraryView] = value.name }
    suspend fun setLibrarySort(value: LibrarySort) = edit { it[Keys.librarySort] = value.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

private typealias Preferences = androidx.datastore.preferences.core.Preferences
