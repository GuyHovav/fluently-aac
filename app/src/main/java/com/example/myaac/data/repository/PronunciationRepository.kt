package com.example.myaac.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.myaac.data.pronunciation.PronunciationDictionary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Repository for managing custom pronunciation preferences.
 * Handles persistence of user-defined pronunciation corrections.
 */
class PronunciationRepository(context: Context) {
    private val preferences: SharedPreferences = 
        context.getSharedPreferences("pronunciation_settings", Context.MODE_PRIVATE)
    
    val dictionary = PronunciationDictionary()
    
    init {
        loadCustomPronunciations()
    }
    
    /**
     * Load custom pronunciations from SharedPreferences.
     */
    private fun loadCustomPronunciations() {
        val jsonString = preferences.getString(KEY_CUSTOM_PRONUNCIATIONS, null)
        if (jsonString != null) {
            try {
                val jsonObject = JSONObject(jsonString)
                val customPronunciations = mutableMapOf<String, String>()
                
                jsonObject.keys().forEach { key ->
                    customPronunciations[key] = jsonObject.getString(key)
                }
                
                dictionary.loadCustomPronunciations(customPronunciations)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Save custom pronunciations to SharedPreferences.
     */
    private fun saveCustomPronunciations() {
        val jsonObject = JSONObject()
        dictionary.getCustomPronunciations().forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        
        preferences.edit()
            .putString(KEY_CUSTOM_PRONUNCIATIONS, jsonObject.toString())
            .apply()
    }
    
    /**
     * Add a custom pronunciation and save it.
     */
    fun addCustomPronunciation(original: String, corrected: String) {
        dictionary.addCustomPronunciation(original, corrected)
        saveCustomPronunciations()
    }
    
    /**
     * Remove a custom pronunciation and save the change.
     */
    fun removeCustomPronunciation(original: String) {
        dictionary.removeCustomPronunciation(original)
        saveCustomPronunciations()
    }
    
    /**
     * Clear all custom pronunciations and save.
     */
    fun clearCustomPronunciations() {
        dictionary.clearCustomPronunciations()
        saveCustomPronunciations()
    }
    
    companion object {
        private const val KEY_CUSTOM_PRONUNCIATIONS = "custom_pronunciations"
    }
}
