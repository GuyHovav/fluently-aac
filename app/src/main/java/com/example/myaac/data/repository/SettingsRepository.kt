package com.example.myaac.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val languageCode: String = "en",
    val textScale: Float = 1.0f,
    val ttsRate: Float = 1.0f,
    val disabilityType: com.example.myaac.data.model.DisabilityType = com.example.myaac.data.model.DisabilityType.NONE,
    val locationSuggestionsEnabled: Boolean = true
)

class SettingsRepository(context: Context) {
    private val preferences: SharedPreferences = 
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        val disabilityTypeString = preferences.getString(KEY_DISABILITY_TYPE, "NONE") ?: "NONE"
        val disabilityType = try {
            com.example.myaac.data.model.DisabilityType.valueOf(disabilityTypeString)
        } catch (e: IllegalArgumentException) {
            com.example.myaac.data.model.DisabilityType.NONE
        }

        return AppSettings(
            languageCode = preferences.getString(KEY_LANGUAGE, "en") ?: "en",
            textScale = preferences.getFloat(KEY_TEXT_SCALE, 1.0f),
            ttsRate = preferences.getFloat(KEY_TTS_RATE, 1.0f),
            disabilityType = disabilityType,
            locationSuggestionsEnabled = preferences.getBoolean(KEY_LOCATION_SUGGESTIONS, true)
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
    
    fun setTtsRate(rate: Float) {
        preferences.edit().putFloat(KEY_TTS_RATE, rate).apply()
        _settings.value = _settings.value.copy(ttsRate = rate)
    }

    fun setLocationSuggestionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_LOCATION_SUGGESTIONS, enabled).apply()
        _settings.value = _settings.value.copy(locationSuggestionsEnabled = enabled)
    }

    fun setDisabilityType(type: com.example.myaac.data.model.DisabilityType) {
        preferences.edit().putString(KEY_DISABILITY_TYPE, type.name).apply()
        _settings.value = _settings.value.copy(disabilityType = type)
        applyPersonalization(type)
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
    }
}
