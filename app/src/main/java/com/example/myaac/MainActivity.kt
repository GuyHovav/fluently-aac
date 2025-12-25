package com.example.myaac

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                        onLocationSuggestionsChange = { enabled ->
                            settingsRepository.setLocationSuggestionsEnabled(enabled)
                        },
                        onDisabilityTypeChange = { type ->
                            settingsRepository.setDisabilityType(type)
                        },
                        onBack = { showSettings = false }
                    )
                } else {
                    MainScreen(
                        onSpeak = { text -> speak(text, settings.ttsRate) },
                        onOpenSettings = { showSettings = true },
                        textScale = settings.textScale,
                        languageCode = settings.languageCode
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
        var textToSpeak = text
        val languageCode = settingsRepository.settings.value.languageCode
        if (languageCode == "iw" || languageCode == "he") {
            // Apply Hebrew corrections
            // רעב -> רָעֵב (hungry vs hunger)
            textToSpeak = textToSpeak.replace(Regex("(?<!\\p{L})רעב(?!\\p{L})"), "רָעֵב")
        }

        tts?.setSpeechRate(rate)
        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "")
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
    textScale: Float,
    languageCode: String = "en"
) {
    val uiState by viewModel.uiState.collectAsState()
    val allBoards by viewModel.allBoards.collectAsState(initial = emptyList())
    

    var buttonToEdit by remember { mutableStateOf<com.example.myaac.model.AacButton?>(null) }
    
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    // Retrieve Gemini Service from Application
    val app = context.applicationContext as com.example.myaac.MyAacApplication
    val geminiService = app.geminiService

    // Location Suggestion Logic
    var showLocationDialog by remember { mutableStateOf(false) }
    var suggestedBoardName by remember { mutableStateOf("") }
    var isCheckingLocation by remember { mutableStateOf(false) }

    // Effect to check location once on startup (or periodically if desired, but ONCE is safer for now)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            isCheckingLocation = true
            try {
                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        scope.launch {
                            try {
                                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                // Deprecated in API 33 but usually still works or has compat; simpler for now to use legacy sync blocking in IO dispatcher if possible, but geocoder is usually fast.
                                // Using the sync method for compatibility with older APIs if needed, or non-blocking in coroutine.
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                if (addresses != null && addresses.isNotEmpty()) {
                                    val address = addresses[0]
                                    // Try to get a meaningful name: featureName, premises, or thoroughfare
                                    // Filter for Public/Saved places: Only suggest if featureName is a specific POI (not just a street or number)
                                    val featureName = address.featureName
                                    val isSpecificPlace = !featureName.isNullOrBlank() && 
                                                          featureName != address.subThoroughfare && 
                                                          featureName != address.thoroughfare
                                    
                                    val rawName = if (isSpecificPlace) featureName else null
                                    
                                    if (rawName != null) {
                                        // Simplify with AI
                                        val simplifiedName = viewModel.simplifyLocationName(rawName)
                                        
                                        // Check if board exists (case insensitive check against allBoards)
                                        // We need to access the current list of boards. 
                                        // Note: allBoards inside launchedEffect might satisfy this if we access the latest value.
                                        val exists = allBoards.any { it.name.equals(simplifiedName, ignoreCase = true) }
                                        
                                        if (!exists && simplifiedName.isNotBlank() && simplifiedName.length < 20) { // arbitrary length check to sanity check
                                            suggestedBoardName = simplifiedName
                                            showLocationDialog = true
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    isCheckingLocation = false
                }.addOnFailureListener {
                     isCheckingLocation = false
                }
            } catch (e: Exception) {
                isCheckingLocation = false
                e.printStackTrace()
            }
        } else {
             // Request permission? For now, we only work if permission is already granted or we can request it.
             // Best practice: Request it.
             // But managing the permission launcher inside this huge composable is tricky without splitting access.
             // Let's rely on the user having granted it or manual prompt for now, OR add a simple launcher.
        }
    }
    
    // Permission Launcher
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // logic to retry location check if granted could go here
    }

    // Trigger permission request once if not granted
    androidx.compose.runtime.LaunchedEffect(Unit) {
         if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
             launcher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
         }
    }

    if (showLocationDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text(stringResource(R.string.location_detected_title)) },
            text = { Text(stringResource(R.string.location_detected_message, suggestedBoardName)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLocationDialog = false
                    viewModel.createMagicBoard(suggestedBoardName, suggestedBoardName)
                }) {
                    Text(stringResource(R.string.yes_create_board))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLocationDialog = false }) {
                    Text(stringResource(R.string.no_thanks))
                }
            }
        )
    }

    if (buttonToEdit != null) {
        // ... (Edit Dialog Code remains same)
         com.example.myaac.ui.components.EditButtonDialog(
            button = buttonToEdit!!,
            defaultLanguage = languageCode,
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
            },
            onDelete = {
                viewModel.deleteButton(buttonToEdit!!.id)
                buttonToEdit = null
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
                onUpdateBoard = { board ->
                    viewModel.updateBoard(board)
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
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background,

        ) { innerPadding ->
            // ... Content
             Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.isLoading) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Board Navigation Row
                if (allBoards.size > 1) { // Only show if we have other boards to navigate to (home + at least one other)
                     androidx.compose.foundation.lazy.LazyRow(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(8.dp),
                         horizontalArrangement = Arrangement.spacedBy(8.dp)
                     ) {
                         // Filter out current board to avoid redundancy, or keep it to show active state
                         // Requirement says: "top row should be items that link to the other boards"
                         items(allBoards.filter { it.id != (uiState.currentBoard?.id ?: "") }) { board ->
                             androidx.compose.material3.ElevatedCard(
                                 onClick = { viewModel.navigateToBoard(board.id) },
                                 modifier = Modifier.height(60.dp) // Height similar to sentence bar item
                             ) {
                                 Row(
                                     modifier = Modifier
                                         .padding(8.dp)
                                         .fillMaxHeight(),
                                     verticalAlignment = Alignment.CenterVertically,
                                     horizontalArrangement = Arrangement.Center
                                 ) {
                                     if (!board.iconPath.isNullOrEmpty()) {
                                         coil.compose.AsyncImage(
                                             model = board.iconPath,
                                             contentDescription = null,
                                             modifier = Modifier
                                                .size(40.dp)
                                                .padding(end = 8.dp)
                                         )
                                     } else {
                                          androidx.compose.material3.Icon(
                                              imageVector = androidx.compose.material.icons.Icons.Default.Folder,
                                              contentDescription = null,
                                              modifier = Modifier.padding(end = 8.dp)
                                          )
                                     }
                                     Text(
                                         text = board.name,
                                         style = MaterialTheme.typography.labelLarge
                                     )
                                 }
                             }
                         }
                     }
                }

                // Sentence Bar
                SentenceBar(
                    sentence = uiState.sentence,
                    onClear = { viewModel.clearSentence() },
                    onBackspace = { viewModel.onButtonPress(com.example.myaac.model.AacButton(
                        id = "bksp", label = "Backspace", speechText = null, action = ButtonAction.DeleteLastWord
                    ))}, 
                    onSpeak = { text -> onSpeak(text) }
                )

                // Smart Strip (AI Predictions) - REMOVED

                // Grid
                if (uiState.currentBoard != null) {
                    CommunicationGrid(
                        buttons = uiState.currentBoard!!.buttons,
                        columns = uiState.currentBoard!!.columns,
                        onButtonClick = { button ->
                            if (uiState.isCaregiverMode) {
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
