package com.example.myaac

import android.app.Application
import com.example.myaac.data.local.AppDatabase
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.repository.BoardRepository
import com.example.myaac.data.repository.SettingsRepository

class MyAacApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { BoardRepository(database.boardDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    
    // TODO: Move API Key to local.properties or secrets gradle plugin for security
    // For prototype, we unfortunately need a key. 
    // Using a placeholder that the user must replace
    val geminiService: GeminiService? by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotEmpty() && apiKey != "TODO_ENTER_KEY_IN_LOCAL_PROPERTIES") {
            GeminiService(apiKey)
        } else {
            android.util.Log.w("MyAacApplication", "Gemini API key not configured. AI features disabled.")
            null
        }
    }
}
