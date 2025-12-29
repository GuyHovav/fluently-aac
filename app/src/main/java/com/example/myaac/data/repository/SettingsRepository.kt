package com.example.myaac.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.myaac.model.AppShortcut
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AppSettings(
    val languageCode: String = "en",
    val textScale: Float = 1.0f,
    val ttsRate: Float = 1.0f,
    val disabilityType: com.example.myaac.data.model.DisabilityType = com.example.myaac.data.model.DisabilityType.NONE,
    val locationSuggestionsEnabled: Boolean = true,
    val showHorizontalNavigation: Boolean = false,
    val showSymbolsInSentenceBar: Boolean = false,
    val itemsToGenerate: Int = 20,
    val maxBoardCapacity: Int = 500,
    val fontFamily: String = "System",
    val displayScale: Float = 1.0f,
    val useSystemSettings: Boolean = true,
    val symbolLibrary: String = "ARASAAC",
    val showHomeOnStartup: Boolean = true,
    val homeButtonsJson: String = "",
    val appShortcutsJson: String = "",
    // Prediction settings
    val predictionEnabled: Boolean = true,
    val aiPredictionEnabled: Boolean = true,
    val predictionCount: Int = 5,
    val showSymbolsInPredictions: Boolean = true,
    val learnFromUsage: Boolean = true,
    // Grammar settings
    val autoGrammarCheck: Boolean = false
)

class SettingsRepository(context: Context) {
    private val preferences: SharedPreferences = 
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        val disabilityTypeString = preferences.getString(KEY_DISABILITY_TYPE, "NONE") ?: "NONE"
        val disabilityType = try {
            com.example.myaac.data.model.DisabilityType.valueOf(disabilityTypeString)
        } catch (e: IllegalArgumentException) {
            com.example.myaac.data.model.DisabilityType.NONE
        }

        // Backward compatibility: if old maxBoardItems exists, use it for both new settings
        val legacyMaxItems = preferences.getInt("max_board_items", -1)
        val defaultGenerate = if (legacyMaxItems != -1) legacyMaxItems else 20
        val defaultCapacity = if (legacyMaxItems != -1) (legacyMaxItems * 5).coerceAtMost(500) else 500

        return AppSettings(
            languageCode = preferences.getString(KEY_LANGUAGE, "en") ?: "en",
            textScale = preferences.getFloat(KEY_TEXT_SCALE, 1.0f),
            ttsRate = preferences.getFloat(KEY_TTS_RATE, 1.0f),
            disabilityType = disabilityType,
            locationSuggestionsEnabled = preferences.getBoolean(KEY_LOCATION_SUGGESTIONS, true),
            showHorizontalNavigation = preferences.getBoolean(KEY_HORIZONTAL_NAVIGATION, false),
            showSymbolsInSentenceBar = preferences.getBoolean(KEY_SHOW_SYMBOLS_IN_SENTENCE, false),
            itemsToGenerate = preferences.getInt(KEY_ITEMS_TO_GENERATE, defaultGenerate).coerceIn(1, 50),
            maxBoardCapacity = preferences.getInt(KEY_MAX_BOARD_CAPACITY, defaultCapacity).coerceIn(50, 1000),
            fontFamily = preferences.getString(KEY_FONT_FAMILY, "System") ?: "System",
            displayScale = preferences.getFloat(KEY_DISPLAY_SCALE, 1.0f),
            useSystemSettings = preferences.getBoolean(KEY_USE_SYSTEM_SETTINGS, true),
            symbolLibrary = preferences.getString(KEY_SYMBOL_LIBRARY, "ARASAAC") ?: "ARASAAC",
            showHomeOnStartup = preferences.getBoolean(KEY_SHOW_HOME_ON_STARTUP, true),
            homeButtonsJson = preferences.getString(KEY_HOME_BUTTONS, "") ?: "",
            appShortcutsJson = preferences.getString(KEY_APP_SHORTCUTS, "") ?: "",
            // Prediction settings
            predictionEnabled = preferences.getBoolean(KEY_PREDICTION_ENABLED, true),
            aiPredictionEnabled = preferences.getBoolean(KEY_AI_PREDICTION_ENABLED, true),
            predictionCount = preferences.getInt(KEY_PREDICTION_COUNT, 5).coerceIn(3, 8),
            showSymbolsInPredictions = preferences.getBoolean(KEY_SHOW_SYMBOLS_IN_PREDICTIONS, true),
            learnFromUsage = preferences.getBoolean(KEY_LEARN_FROM_USAGE, true),
            autoGrammarCheck = preferences.getBoolean(KEY_AUTO_GRAMMAR_CHECK, false)
        )
    }
    
    fun setLanguage(code: String) {
        preferences.edit().putString(KEY_LANGUAGE, code).apply()
        _settings.value = _settings.value.copy(languageCode = code)
    }
    
    fun setTextScale(scale: Float) {
        preferences.edit().putFloat(KEY_TEXT_SCALE, scale).apply()
        _settings.value = _settings.value.copy(textScale = scale)
    }

    fun setFontFamily(family: String) {
        preferences.edit().putString(KEY_FONT_FAMILY, family).apply()
        _settings.value = _settings.value.copy(fontFamily = family)
    }

    fun setDisplayScale(scale: Float) {
        preferences.edit().putFloat(KEY_DISPLAY_SCALE, scale).apply()
        _settings.value = _settings.value.copy(displayScale = scale)
    }

    fun setUseSystemSettings(useSystem: Boolean) {
        preferences.edit().putBoolean(KEY_USE_SYSTEM_SETTINGS, useSystem).apply()
        _settings.value = _settings.value.copy(useSystemSettings = useSystem)
    }
    
    fun setTtsRate(rate: Float) {
        preferences.edit().putFloat(KEY_TTS_RATE, rate).apply()
        _settings.value = _settings.value.copy(ttsRate = rate)
    }

    fun setLocationSuggestionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_LOCATION_SUGGESTIONS, enabled).apply()
        _settings.value = _settings.value.copy(locationSuggestionsEnabled = enabled)
    }

    fun setHorizontalNavigationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HORIZONTAL_NAVIGATION, enabled).apply()
        _settings.value = _settings.value.copy(showHorizontalNavigation = enabled)
    }

    fun setShowSymbolsInSentenceBar(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_SYMBOLS_IN_SENTENCE, enabled).apply()
        _settings.value = _settings.value.copy(showSymbolsInSentenceBar = enabled)
    }

    fun setItemsToGenerate(count: Int) {
        val validCount = count.coerceIn(1, 50)
        preferences.edit().putInt(KEY_ITEMS_TO_GENERATE, validCount).apply()
        _settings.value = _settings.value.copy(itemsToGenerate = validCount)
    }

    fun setMaxBoardCapacity(count: Int) {
        val validCount = count.coerceIn(50, 1000)
        preferences.edit().putInt(KEY_MAX_BOARD_CAPACITY, validCount).apply()
        _settings.value = _settings.value.copy(maxBoardCapacity = validCount)
    }

    fun setDisabilityType(type: com.example.myaac.data.model.DisabilityType) {
        preferences.edit().putString(KEY_DISABILITY_TYPE, type.name).apply()
        _settings.value = _settings.value.copy(disabilityType = type)
        applyPersonalization(type)
    }

    fun setSymbolLibrary(library: String) {
        preferences.edit().putString(KEY_SYMBOL_LIBRARY, library).apply()
        _settings.value = _settings.value.copy(symbolLibrary = library)
    }

    fun setShowHomeOnStartup(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_HOME_ON_STARTUP, show).apply()
        _settings.value = _settings.value.copy(showHomeOnStartup = show)
    }

    fun setHomeButtons(buttonsJson: String) {
        preferences.edit().putString(KEY_HOME_BUTTONS, buttonsJson).apply()
        _settings.value = _settings.value.copy(homeButtonsJson = buttonsJson)
    }

    fun getAppShortcuts(): List<AppShortcut> {
        val json = _settings.value.appShortcutsJson
        if (json.isEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<AppShortcut>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setAppShortcuts(shortcuts: List<AppShortcut>) {
        val json = gson.toJson(shortcuts)
        preferences.edit().putString(KEY_APP_SHORTCUTS, json).apply()
        _settings.value = _settings.value.copy(appShortcutsJson = json)
    }

    // Prediction settings
    fun setPredictionEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PREDICTION_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(predictionEnabled = enabled)
    }

    fun setAiPredictionEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AI_PREDICTION_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(aiPredictionEnabled = enabled)
    }

    fun setPredictionCount(count: Int) {
        val validCount = count.coerceIn(3, 8)
        preferences.edit().putInt(KEY_PREDICTION_COUNT, validCount).apply()
        _settings.value = _settings.value.copy(predictionCount = validCount)
    }

    fun setShowSymbolsInPredictions(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_SYMBOLS_IN_PREDICTIONS, show).apply()
        _settings.value = _settings.value.copy(showSymbolsInPredictions = show)
    }

    fun setLearnFromUsage(learn: Boolean) {
        preferences.edit().putBoolean(KEY_LEARN_FROM_USAGE, learn).apply()
        _settings.value = _settings.value.copy(learnFromUsage = learn)
    }

    fun setAutoGrammarCheck(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_GRAMMAR_CHECK, enabled).apply()
        _settings.value = _settings.value.copy(autoGrammarCheck = enabled)
    }

    private fun applyPersonalization(type: com.example.myaac.data.model.DisabilityType) {
        // Simple personalization logic
        when (type) {
            com.example.myaac.data.model.DisabilityType.VISUAL_IMPAIRMENT -> {
                if (_settings.value.textScale < 1.3f) setTextScale(1.3f)
            }
            com.example.myaac.data.model.DisabilityType.MOTOR_IMPAIRMENT -> {
                // Could increase button sizes in the future, for now maybe ensure text isn't too small
                if (_settings.value.textScale < 1.1f) setTextScale(1.1f)
            }
            else -> {}
        }
    }
    
    companion object {
        private const val KEY_LANGUAGE = "language_code"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_DISABILITY_TYPE = "disability_type"
        private const val KEY_LOCATION_SUGGESTIONS = "location_suggestions_enabled"
        private const val KEY_HORIZONTAL_NAVIGATION = "horizontal_navigation_enabled"
        private const val KEY_SHOW_SYMBOLS_IN_SENTENCE = "show_symbols_in_sentence"
        private const val KEY_ITEMS_TO_GENERATE = "items_to_generate"
        private const val KEY_MAX_BOARD_CAPACITY = "max_board_capacity"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_DISPLAY_SCALE = "display_scale"
        private const val KEY_USE_SYSTEM_SETTINGS = "use_system_settings"
        private const val KEY_SYMBOL_LIBRARY = "symbol_library"
        private const val KEY_SHOW_HOME_ON_STARTUP = "show_home_on_startup"
        private const val KEY_HOME_BUTTONS = "home_buttons_json"
        private const val KEY_APP_SHORTCUTS = "app_shortcuts_json"
        // Prediction keys
        private const val KEY_PREDICTION_ENABLED = "prediction_enabled"
        private const val KEY_AI_PREDICTION_ENABLED = "ai_prediction_enabled"
        private const val KEY_PREDICTION_COUNT = "prediction_count"
        private const val KEY_SHOW_SYMBOLS_IN_PREDICTIONS = "show_symbols_in_predictions"
        private const val KEY_LEARN_FROM_USAGE = "learn_from_usage"
        private const val KEY_AUTO_GRAMMAR_CHECK = "auto_grammar_check"
    }
}
