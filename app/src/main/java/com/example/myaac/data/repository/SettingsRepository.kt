package com.example.myaac.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val languageCode: String = "en",
    val textScale: Float = 1.0f,
    val ttsRate: Float = 1.0f
)

class SettingsRepository(context: Context) {
    private val preferences: SharedPreferences = 
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        return AppSettings(
            languageCode = preferences.getString(KEY_LANGUAGE, "en") ?: "en",
            textScale = preferences.getFloat(KEY_TEXT_SCALE, 1.0f),
            ttsRate = preferences.getFloat(KEY_TTS_RATE, 1.0f)
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
    
    companion object {
        private const val KEY_LANGUAGE = "language_code"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_TTS_RATE = "tts_rate"
    }
}
