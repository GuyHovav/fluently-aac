package com.example.myaac

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import com.example.myaac.model.ButtonAction
import com.example.myaac.ui.components.CommunicationGrid
import com.example.myaac.ui.components.SentenceBar
import com.example.myaac.ui.theme.MyAACTheme
import com.example.myaac.viewmodel.BoardViewModel
import com.example.myaac.ui.SettingsScreen
import com.example.myaac.data.repository.AppSettings
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private lateinit var settingsRepository: com.example.myaac.data.repository.SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as MyAacApplication
        val languageCode = app.settingsRepository.settings.value.languageCode
        val locale = when (languageCode) {
            "iw" -> Locale("iw")
            else -> Locale("en")
        }
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as MyAacApplication
        settingsRepository = app.settingsRepository
        
        // Initialize TTS
        tts = TextToSpeech(this, this)

        setContent {
            MyAACTheme {
                val settings by settingsRepository.settings.collectAsState()
                var showSettings by remember { mutableStateOf(false) }
                
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        onLanguageChange = { code ->
                            settingsRepository.setLanguage(code)
                            // Update TTS language
                            val locale = when (code) {
                                "iw" -> Locale("iw", "IL")
                                else -> Locale.US
                            }
                            tts?.language = locale
                            // Recreate activity to apply new language
                            recreate()
                        },
                        onTextScaleChange = { scale ->
                            settingsRepository.setTextScale(scale)
                        },
                        onTtsRateChange = { rate ->
                            settingsRepository.setTtsRate(rate)
                        },
                        onBack = { showSettings = false }
                    )
                } else {
                    MainScreen(
                        onSpeak = { text -> speak(text, settings.ttsRate) },
                        onOpenSettings = { showSettings = true },
                        textScale = settings.textScale
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val languageCode = settingsRepository.settings.value.languageCode
            val locale = when (languageCode) {
                "iw" -> Locale("iw", "IL")
                else -> Locale.US
            }
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language specified is not supported!")
            }
        } else {
            Log.e("TTS", "Initilization Failed!")
        }
    }

    private fun speak(text: String, rate: Float = 1.0f) {
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BoardViewModel = viewModel(factory = BoardViewModel.Factory),
    onSpeak: (String) -> Unit,
    onOpenSettings: () -> Unit,
    textScale: Float
) {
    val uiState by viewModel.uiState.collectAsState()
    val allBoards by viewModel.allBoards.collectAsState(initial = emptyList())
    
    var isEditMode by remember { mutableStateOf(false) }
    var buttonToEdit by remember { mutableStateOf<com.example.myaac.model.AacButton?>(null) }
    
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    // Retrieve Gemini Service from Application
    val app = context.applicationContext as com.example.myaac.MyAacApplication
    val geminiService = app.geminiService

    if (buttonToEdit != null) {
        // ... (Edit Dialog Code remains same)
         com.example.myaac.ui.components.EditButtonDialog(
            button = buttonToEdit!!,
            onDismiss = { buttonToEdit = null },
            onSave = { updatedButton ->
                viewModel.updateButton(updatedButton)
                buttonToEdit = null
            },
            onIdentifyItem = { bitmap ->
                if (geminiService == null) {
                    "Error: Gemini API not configured" to null
                } else {
                    try {
                        geminiService.identifyItem(bitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        "Error: ${e.localizedMessage}" to null
                    }
                }
            },
            onCheckSymbol = { query ->
                viewModel.checkSymbol(query)
            }
        )
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            com.example.myaac.ui.components.SidebarContent(
                boards = allBoards,
                currentBoardId = uiState.currentBoard?.id ?: "",
                isCaregiverMode = uiState.isCaregiverMode,
                onBoardSelect = { board -> 
                    viewModel.navigateToBoard(board.id)
                    scope.launch { drawerState.close() }
                },
                onCreateBoard = { name, topic ->
                    if (topic.isNotBlank()) {
                         viewModel.createMagicBoard(name, topic)
                    } else {
                         viewModel.createNewBoard(name)
                    }
                    scope.launch { drawerState.close() }
                },
                onDeleteBoard = { board ->
                    viewModel.deleteBoard(board.id)
                },
                onUnlock = { pin -> viewModel.unlockCaregiverMode(pin) },
                onLock = { viewModel.lockCaregiverMode() },
                onOpenSettings = { 
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                // Add a small hamburger menu if needed, or just swipe
                androidx.compose.material3.CenterAlignedTopAppBar(
                    title = { Text(uiState.currentBoard?.name ?: "MyAAC") },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (uiState.isCaregiverMode) {
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { isEditMode = !isEditMode },
                        containerColor = if (isEditMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    ) {
                         androidx.compose.material3.Icon(
                             imageVector = if (isEditMode) androidx.compose.material.icons.Icons.Default.Close else androidx.compose.material.icons.Icons.Default.Edit,
                             contentDescription = "Toggle Edit Mode"
                         )
                    }
                }
            }
        ) { innerPadding ->
            // ... Content
             Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Sentence Bar
                SentenceBar(
                    sentence = uiState.sentence,
                    onClear = { viewModel.clearSentence() },
                    onBackspace = { viewModel.onButtonPress(com.example.myaac.model.AacButton(
                        id = "bksp", label = "Backspace", speechText = null, action = ButtonAction.DeleteLastWord
                    ))}, 
                    onSpeak = { text -> onSpeak(text) }
                )

                // Smart Strip (AI Predictions)
                com.example.myaac.ui.components.SmartStrip(
                    predictions = uiState.recommendedButtons,
                    onButtonClick = { button ->
                        viewModel.onButtonPress(button)
                        if (button.action is ButtonAction.Speak) {
                            onSpeak(button.textToSpeak)
                        }
                    }
                )

                // Grid
                if (uiState.currentBoard != null) {
                    CommunicationGrid(
                        buttons = uiState.currentBoard!!.buttons,
                        columns = uiState.currentBoard!!.columns,
                        onButtonClick = { button ->
                            if (isEditMode) {
                                buttonToEdit = button
                            } else {
                                viewModel.onButtonPress(button)
                                if (button.action is ButtonAction.Speak) {
                                    onSpeak(button.textToSpeak)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                     // Loading STATE
                     androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                         androidx.compose.material3.CircularProgressIndicator()
                     }
                }
            }
        }
    }
}
