package com.spkup.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

val Context.spkUpDataStore: DataStore<Preferences> by preferencesDataStore(name = "spkup_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class StyleMode(val key: String, val displayName: String) {
    FORMAL("FORMAL", "Formal"),
    CASUAL("CASUAL", "Casual"),
    SUPER_CASUAL("SUPER_CASUAL", "Super Casual"),
    ORGANISE("ORGANISE", "Organise"),
    MKDOWN("MKDOWN", "Markdown");

    companion object {
        fun fromKey(key: String?): StyleMode {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: CASUAL
        }
    }
}

data class SpkUpPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isFloatingBubbleEnabled: Boolean = false,
    val bubbleOpacity: Float = 0.61f,
    val bubbleSizeDp: Int = 52,
    val bubbleShapePercent: Int = 15,
    val styleMode: StyleMode = StyleMode.CASUAL,
    val isOnboardingCompleted: Boolean = false,
    val bubbleLastX: Int = -1,
    val bubbleLastY: Int = -1
)

class SpkUpPreferences(private val context: Context) {

    private val dataStore = context.spkUpDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        private var INSTANCE: SpkUpPreferences? = null

        fun getInstance(context: Context): SpkUpPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpkUpPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Exact Keys
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_IS_FLOATING_BUBBLE_ENABLED = booleanPreferencesKey("is_floating_bubble_enabled")
        val KEY_BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val KEY_BUBBLE_SIZE_DP = intPreferencesKey("bubble_size_dp")
        val KEY_BUBBLE_SHAPE_PERCENT = intPreferencesKey("bubble_shape_percent")
        val KEY_STYLE_MODE = stringPreferencesKey("style_mode")
        val KEY_IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val KEY_BUBBLE_LAST_X = intPreferencesKey("bubble_last_x")
        val KEY_BUBBLE_LAST_Y = intPreferencesKey("bubble_last_y")

        // Exact Defaults & Ranges
        val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        const val DEFAULT_IS_BUBBLE_ENABLED = false
        const val DEFAULT_BUBBLE_OPACITY = 0.61f
        const val MIN_BUBBLE_OPACITY = 0.20f
        const val MAX_BUBBLE_OPACITY = 1.0f

        const val DEFAULT_BUBBLE_SIZE_DP = 52
        const val MIN_BUBBLE_SIZE_DP = 40
        const val MAX_BUBBLE_SIZE_DP = 80

        const val DEFAULT_BUBBLE_SHAPE_PERCENT = 15
        const val MIN_BUBBLE_SHAPE_PERCENT = 0
        const val MAX_BUBBLE_SHAPE_PERCENT = 50

        val DEFAULT_STYLE_MODE = StyleMode.CASUAL
    }

    private val safePreferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val state: StateFlow<SpkUpPreferencesState> = safePreferencesFlow.map { prefs ->
        val themeModeStr = prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE.name
        val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(DEFAULT_THEME_MODE)
        val isBubbleEnabled = prefs[KEY_IS_FLOATING_BUBBLE_ENABLED] ?: DEFAULT_IS_BUBBLE_ENABLED
        val bubbleOpacity = (prefs[KEY_BUBBLE_OPACITY] ?: DEFAULT_BUBBLE_OPACITY).coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
        val bubbleSizeDp = (prefs[KEY_BUBBLE_SIZE_DP] ?: DEFAULT_BUBBLE_SIZE_DP).coerceIn(MIN_BUBBLE_SIZE_DP, MAX_BUBBLE_SIZE_DP)
        val bubbleShapePercent = (prefs[KEY_BUBBLE_SHAPE_PERCENT] ?: DEFAULT_BUBBLE_SHAPE_PERCENT).coerceIn(MIN_BUBBLE_SHAPE_PERCENT, MAX_BUBBLE_SHAPE_PERCENT)
        val styleMode = StyleMode.fromKey(prefs[KEY_STYLE_MODE])
        val isOnboardingCompleted = prefs[KEY_IS_ONBOARDING_COMPLETED] ?: false
        val bubbleLastX = prefs[KEY_BUBBLE_LAST_X] ?: -1
        val bubbleLastY = prefs[KEY_BUBBLE_LAST_Y] ?: -1

        SpkUpPreferencesState(
            themeMode = themeMode,
            isFloatingBubbleEnabled = isBubbleEnabled,
            bubbleOpacity = bubbleOpacity,
            bubbleSizeDp = bubbleSizeDp,
            bubbleShapePercent = bubbleShapePercent,
            styleMode = styleMode,
            isOnboardingCompleted = isOnboardingCompleted,
            bubbleLastX = bubbleLastX,
            bubbleLastY = bubbleLastY
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SpkUpPreferencesState()
    )

    // Flow Accessors
    val themeModeFlow: Flow<ThemeMode> = safePreferencesFlow.map { prefs ->
        val raw = prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(DEFAULT_THEME_MODE)
    }

    val isFloatingBubbleEnabledFlow: Flow<Boolean> = safePreferencesFlow.map { prefs ->
        prefs[KEY_IS_FLOATING_BUBBLE_ENABLED] ?: DEFAULT_IS_BUBBLE_ENABLED
    }

    val bubbleOpacityFlow: Flow<Float> = safePreferencesFlow.map { prefs ->
        (prefs[KEY_BUBBLE_OPACITY] ?: DEFAULT_BUBBLE_OPACITY).coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
    }

    val bubbleSizeDpFlow: Flow<Int> = safePreferencesFlow.map { prefs ->
        (prefs[KEY_BUBBLE_SIZE_DP] ?: DEFAULT_BUBBLE_SIZE_DP).coerceIn(MIN_BUBBLE_SIZE_DP, MAX_BUBBLE_SIZE_DP)
    }

    val bubbleShapePercentFlow: Flow<Int> = safePreferencesFlow.map { prefs ->
        (prefs[KEY_BUBBLE_SHAPE_PERCENT] ?: DEFAULT_BUBBLE_SHAPE_PERCENT).coerceIn(MIN_BUBBLE_SHAPE_PERCENT, MAX_BUBBLE_SHAPE_PERCENT)
    }

    val styleModeFlow: Flow<StyleMode> = safePreferencesFlow.map { prefs ->
        StyleMode.fromKey(prefs[KEY_STYLE_MODE])
    }

    val isOnboardingCompletedFlow: Flow<Boolean> = safePreferencesFlow.map { prefs ->
        prefs[KEY_IS_ONBOARDING_COMPLETED] ?: false
    }

    // Direct Snapshot Getters
    val currentThemeMode: ThemeMode get() = state.value.themeMode
    val isFloatingBubbleEnabled: Boolean get() = state.value.isFloatingBubbleEnabled
    val currentBubbleOpacity: Float get() = state.value.bubbleOpacity
    val currentBubbleSizeDp: Int get() = state.value.bubbleSizeDp
    val currentBubbleShapePercent: Int get() = state.value.bubbleShapePercent
    val currentStyleMode: StyleMode get() = state.value.styleMode
    val isOnboardingCompleted: Boolean get() = state.value.isOnboardingCompleted
    val bubbleLastX: Int get() = state.value.bubbleLastX
    val bubbleLastY: Int get() = state.value.bubbleLastY

    // Mutators
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setFloatingBubbleEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_FLOATING_BUBBLE_ENABLED] = enabled
        }
    }

    suspend fun setBubbleOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(MIN_BUBBLE_OPACITY, MAX_BUBBLE_OPACITY)
        dataStore.edit { prefs ->
            prefs[KEY_BUBBLE_OPACITY] = clamped
        }
    }

    suspend fun setBubbleSizeDp(sizeDp: Int) {
        val clamped = sizeDp.coerceIn(MIN_BUBBLE_SIZE_DP, MAX_BUBBLE_SIZE_DP)
        dataStore.edit { prefs ->
            prefs[KEY_BUBBLE_SIZE_DP] = clamped
        }
    }

    suspend fun setBubbleShapePercent(percent: Int) {
        val clamped = percent.coerceIn(MIN_BUBBLE_SHAPE_PERCENT, MAX_BUBBLE_SHAPE_PERCENT)
        dataStore.edit { prefs ->
            prefs[KEY_BUBBLE_SHAPE_PERCENT] = clamped
        }
    }

    suspend fun setStyleMode(mode: StyleMode) {
        dataStore.edit { prefs ->
            prefs[KEY_STYLE_MODE] = mode.key
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_IS_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setBubblePosition(x: Int, y: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_BUBBLE_LAST_X] = x
            prefs[KEY_BUBBLE_LAST_Y] = y
        }
    }
}
