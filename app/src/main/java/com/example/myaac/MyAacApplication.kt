package com.example.myaac

import android.app.Application
import com.example.myaac.data.local.AppDatabase
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.repository.BoardRepository
import com.example.myaac.data.repository.SettingsRepository
import com.example.myaac.data.repository.PronunciationRepository
import com.example.myaac.data.repository.AuthRepository
import com.example.myaac.data.repository.CloudRepository
import com.example.myaac.data.nlp.PredictionPreloader
import com.example.myaac.data.nlp.LocalPredictionEngine

class MyAacApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { BoardRepository(database.boardDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val pronunciationRepository by lazy { PronunciationRepository(this) }
    val authRepository by lazy { AuthRepository() }
    val cloudRepository by lazy { CloudRepository() }
    val morphologyService by lazy { com.example.myaac.data.nlp.MorphologyService() }
    
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
    
    // Prediction preloader for fast startup
    private val predictionPreloader by lazy {
        val localEngine = LocalPredictionEngine(
            wordFrequencyDao = database.wordFrequencyDao(),
            languageCode = settingsRepository.settings.value.languageCode
        )
        PredictionPreloader(database.wordFrequencyDao(), localEngine)
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Pre-load predictions in background for instant first use
        predictionPreloader.preload()
        
        android.util.Log.d("MyAacApplication", "App initialized with fast prediction system")
    }
}
