package com.example.myaac

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import com.example.myaac.ui.components.GenerateItemsDialog
import com.example.myaac.ui.theme.MyAACTheme
import com.example.myaac.viewmodel.BoardViewModel
import com.example.myaac.ui.SettingsScreen
import com.example.myaac.ui.PronunciationManagementScreen
import com.example.myaac.data.repository.AppSettings
import java.util.Locale
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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
        
        // Handle Splash Screen Logic
        // Handle Splash Screen Logic
        val splashScreen = installSplashScreen()
        
        var isDataLoaded = false
        
        // We need to peek into the ViewModel to know when data is ready
        // Since we can't easily collect flow in onCreate before setContent without lifecycle scope complexity,
        // we'll rely on a side-effect or just checking the repo state if possible?
        // Better: Launch a small scope to collect until ready.
        
        // Actually, we can get the ViewModel instance here using ViewModelProvider
        val factory = BoardViewModel.Factory
        val tempViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[BoardViewModel::class.java]
        
        // Keep splash screen on until we have a current board and not loading
        splashScreen.setKeepOnScreenCondition {
            val state = tempViewModel.uiState.value
            // Wait until we have a board and we are not in initial loading state
            // Note: isLoading might trigger for other things, so strict check:
             state.currentBoard == null
        }

        setContent {
                val settings by settingsRepository.settings.collectAsState()
                
                // Calculate Density Override
                val currentDensity = androidx.compose.ui.platform.LocalDensity.current
                val finalDensity = remember(settings.useSystemSettings, settings.displayScale, settings.textScale, currentDensity) {
                    if (settings.useSystemSettings) {
                        currentDensity
                    } else {
                        androidx.compose.ui.unit.Density(
                            density = currentDensity.density * settings.displayScale,
                            fontScale = settings.textScale
                        )
                    }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides finalDensity
                ) {
                    MyAACTheme(
                        fontFamily = settings.fontFamily
                    ) {
                    var showSettings by remember { mutableStateOf(false) }
                var showPronunciationManagement by remember { mutableStateOf(false) }
                
                val scope = rememberCoroutineScope()
                val context = androidx.compose.ui.platform.LocalContext.current
                val app = context.applicationContext as com.example.myaac.MyAacApplication
                val geminiService = app.geminiService

                when {
                    showPronunciationManagement -> {
                        PronunciationManagementScreen(
                            onBack = { showPronunciationManagement = false }
                        )
                    }
                    showSettings -> {
                        SettingsScreen(
                            repository = settingsRepository,
                            onNavigateBack = { showSettings = false }
                        )
                    }
                    else -> {
                        MainScreen(
                            onSpeak = { text -> speak(text, settings.ttsRate) },
                            onOpenSettings = { showSettings = true },
                            textScale = settings.textScale,
                            languageCode = settings.languageCode,
                            showHorizontalNavigation = settings.showHorizontalNavigation,
                            showSymbolsInSentenceBar = settings.showSymbolsInSentenceBar,
                            symbolLibrary = settings.symbolLibrary
                        )
                    }
                }
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
        val languageCode = settingsRepository.settings.value.languageCode
        val app = application as MyAacApplication
        
        // Apply pronunciation corrections using the dictionary
        val textToSpeak = app.pronunciationRepository.dictionary.applyCorrections(text, languageCode)

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
    languageCode: String = "en",
    showHorizontalNavigation: Boolean = false,
    showSymbolsInSentenceBar: Boolean = true,
    symbolLibrary: String = "ARASAAC"
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
    val morphologyService = app.morphologyService
    
    // Grammar State
    var grammarVariations by remember { mutableStateOf<List<String>?>(null) }
    var grammarBaseWord by remember { mutableStateOf("") }

    // Location Suggestion Logic
    var showLocationDialog by remember { mutableStateOf(false) }
    var suggestedBoardName by remember { mutableStateOf("") }
    var isCheckingLocation by remember { mutableStateOf(false) }

    // Exit Confirmation Logic
    var showExitConfirmation by remember { mutableStateOf(false) }

    // Symbol Generation Dialog
    var showGenerateItemsDialog by remember { mutableStateOf(false) }
    
    // Back Handler
    androidx.activity.compose.BackHandler(enabled = true) {
        if (uiState.currentBoard?.id == "home" && !viewModel.canNavigateBack) {
             showExitConfirmation = true
        } else if (viewModel.canNavigateBack) {
            viewModel.navigateBack()
        } else {
             // Fallback if not on home but stack empty? Should ideally refer to home or exit
             // If we are deep somewhere but empty stack (e.g. direct load), maybe go home?
             // But for now, let's treat as exit path or go home.
             if (uiState.currentBoard?.id != "home") {
                 viewModel.navigateToBoard("home")
             } else {
                 showExitConfirmation = true
             }
        }
    }
    
    // Auto-Speak Effects
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.speechRequest.collect { text ->
             onSpeak(text)
        }
    }

    
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.exit_app_title)) },
            text = { Text(stringResource(R.string.exit_app_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    val activity = context as? android.app.Activity
                    activity?.finish()
                }) {
                    Text(stringResource(R.string.yes_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text(stringResource(R.string.no_stay))
                }
            }
        )
    }

    // Link Creation Logic
    var createdBoardIdForLink by remember { mutableStateOf<String?>(null) }
    var showLinkTargetPicker by remember { mutableStateOf(false) }

    // Dialog for "Create Link?"
    if (createdBoardIdForLink != null && !showLinkTargetPicker) {
        val newBoardName = allBoards.find { it.id == createdBoardIdForLink }?.name ?: "New Board"
        AlertDialog(
            onDismissRequest = { createdBoardIdForLink = null },
            title = { Text(stringResource(R.string.create_link_title)) },
            text = { Text(stringResource(R.string.create_link_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val linkButton = com.example.myaac.model.AacButton(
                        id = "link_${java.util.UUID.randomUUID()}",
                        label = newBoardName,
                        speechText = null,
                        action = ButtonAction.LinkToBoard(createdBoardIdForLink!!),
                        backgroundColor = 0xFFFFE0B2 // Orange-ish for folders/links
                    )
                    scope.launch {
                        viewModel.addButtonToBoard("home", linkButton)
                        createdBoardIdForLink = null
                    }
                }) {
                    Text(stringResource(R.string.yes_on_home))
                }
            },
            dismissButton = {
                Row {
                     TextButton(onClick = { showLinkTargetPicker = true }) {
                        Text(stringResource(R.string.select_target_board)) // "Select..."
                    }
                    TextButton(onClick = { createdBoardIdForLink = null }) {
                        Text(stringResource(R.string.no_thanks))
                    }
                }
            }
        )
    }

    // Picker for "Select Target Board"
    if (showLinkTargetPicker && createdBoardIdForLink != null) {
        val newBoardName = allBoards.find { it.id == createdBoardIdForLink }?.name ?: "New Board"
        AlertDialog(
            onDismissRequest = { 
                showLinkTargetPicker = false
                createdBoardIdForLink = null 
            },
            title = { Text(stringResource(R.string.select_target_board)) },
            text = {
                // Simple list of boards
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(300.dp)
                ) {
                    items(allBoards.filter { it.id != createdBoardIdForLink }) { board ->
                        ListItem(
                            headlineContent = { Text(board.name) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.clickable {
                                val linkButton = com.example.myaac.model.AacButton(
                                    id = "link_${java.util.UUID.randomUUID()}",
                                    label = newBoardName,
                                    speechText = null,
                                    action = ButtonAction.LinkToBoard(createdBoardIdForLink!!),
                                    backgroundColor = 0xFFFFE0B2
                                )
                                scope.launch {
                                    viewModel.addButtonToBoard(board.id, linkButton)
                                    showLinkTargetPicker = false
                                    createdBoardIdForLink = null
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showLinkTargetPicker = false
                    createdBoardIdForLink = null 
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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
                    scope.launch {
                        viewModel.createMagicBoard(suggestedBoardName, suggestedBoardName)
                    }
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

    if (showGenerateItemsDialog && uiState.currentBoard != null) {
        GenerateItemsDialog(
            onDismiss = { showGenerateItemsDialog = false },
            onGenerate = { count ->
                viewModel.expandBoard(uiState.currentBoard!!, count)
                showGenerateItemsDialog = false
            }
        )
    }

    if (buttonToEdit != null) {
        // ... (Edit Dialog Code remains same)
         com.example.myaac.ui.components.EditButtonDialog(
            button = buttonToEdit!!,
            allBoards = allBoards,
            defaultLanguage = languageCode,
            symbolLibrary = symbolLibrary,
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
                    scope.launch {
                        val newId = if (topic.isNotBlank()) {
                             viewModel.createMagicBoard(name, topic)
                        } else {
                             viewModel.createNewBoard(name)
                        }
                        drawerState.close()
                        // Trigger link prompt
                        createdBoardIdForLink = newId
                    }
                },
                onDeleteBoard = { board ->
                    viewModel.deleteBoard(board.id)
                },
                onUpdateBoard = { board ->
                    viewModel.updateBoard(board)
                },
                onExpandBoard = { board, itemCount ->
                     viewModel.expandBoard(board, itemCount)
                     scope.launch { drawerState.close() }
                },
                onUnlock = { pin -> viewModel.unlockCaregiverMode(pin) },
                onLock = { viewModel.lockCaregiverMode() },
                onOpenSettings = { 
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
                onCreateVisualScene = { name, bitmap, uri ->
                    scope.launch {
                        val newId = viewModel.createMagicScene(name, bitmap, uri)
                        drawerState.close()
                        if (newId.isNotEmpty()) {
                             createdBoardIdForLink = newId
                        }
                    }
                },
                onCreateQuickBoard = { name, bitmaps ->
                    scope.launch {
                        val newId = viewModel.createQuickBoard(name, bitmaps)
                        drawerState.close()
                        if (newId.isNotEmpty()) {
                             createdBoardIdForLink = newId
                        }
                    }
                },
                onSuggestBoardName = { bitmaps ->
                    viewModel.suggestBoardName(bitmaps)
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
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.loading_populating_board),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                // Board Navigation Row
                if (showHorizontalNavigation && allBoards.size > 1) { // Only show if enabled and we have other boards
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

                // Sentence Bar (only show in user mode, not in caregiver mode)
                if (!uiState.isCaregiverMode) {
                    val settings by app.settingsRepository.settings.collectAsState()
                    SentenceBar(
                        sentence = uiState.sentence,
                        onClear = { viewModel.clearSentence() },
                        onBackspace = { viewModel.onButtonPress(com.example.myaac.model.AacButton(
                            id = "bksp", label = "Backspace", speechText = null, action = ButtonAction.DeleteLastWord
                        ))}, 
                        onSpeak = { text -> onSpeak(text) },
                        languageCode = languageCode,
                        showSymbols = showSymbolsInSentenceBar,
                        showUndo = uiState.showUndoAi,
                        onUndo = viewModel::undoAiCorrection,
                        predictions = uiState.predictions,
                        predictionSymbols = uiState.predictionSymbols,
                        showPredictionSymbols = settings.showSymbolsInPredictions,
                        isPredictionLoading = uiState.isPredictionLoading,
                        onPredictionClick = { word ->
                            viewModel.onPredictionSelected(word)
                        },
                        allowPredictionStrip = true,
                        onGrammarCheck = { viewModel.applyPendingGrammarCorrection() }
                    )
                }

                // Grid with overlaid SelectionBar
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    // Grid
                    if (uiState.currentBoard != null) {
                        if (!uiState.currentBoard!!.backgroundImagePath.isNullOrEmpty()) {
                            com.example.myaac.ui.components.VisualSceneGrid(
                                board = uiState.currentBoard!!,
                                isCaregiverMode = uiState.isCaregiverMode,
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
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // For home board, use dynamic buttons if available
                            // logic: if home board but dynamic buttons not ready, show mostly empty/loading
                            // to avoid "old board" flash.
                            val isHome = uiState.currentBoard?.id == "home"
                            val displayButtons = if (isHome) {
                                uiState.homeBoardButtons ?: emptyList() // Or waiting...
                            } else {
                                uiState.currentBoard?.buttons ?: emptyList()
                            }

                            CommunicationGrid(
                                buttons = displayButtons,
                                columns = uiState.currentBoard!!.columns,
                                isCaregiverMode = uiState.isCaregiverMode,
                                selectedButtonIds = uiState.selectedButtonIds,
                                onButtonClick = { button ->
                                    if (uiState.isCaregiverMode) {
                                        // In selection mode, clicking toggles selection
                                        if (uiState.selectedButtonIds.isNotEmpty()) {
                                            viewModel.toggleButtonSelection(button.id)
                                        } else {
                                            // No items selected, open edit dialog
                                            buttonToEdit = button
                                        }
                                    } else {
                                        viewModel.onButtonPress(button)
                                        when (val action = button.action) {
                                            is ButtonAction.Speak -> onSpeak(button.textToSpeak)
                                            is ButtonAction.LaunchApp -> {
                                                // Launch external app
                                                try {
                                                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                                                    if (intent != null) {
                                                        context.startActivity(intent)
                                                    } else {
                                                        // App not found
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "App not found: ${action.packageName}",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Error launching app: ${e.message}",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                },
                                onButtonLongPress = if (uiState.isCaregiverMode) {
                                    { button -> viewModel.toggleButtonSelection(button.id) }
                                } else null,
                                onGrammarRequest = { button ->
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val word = button.textToSpeak
                                        // Try Verb and Noun forms and combining them
                                        val verbs = morphologyService.getVariations(word, "VERB")
                                        val nouns = morphologyService.getVariations(word, "NOUN")
                                        val adjectives = morphologyService.getVariations(word, "ADJECTIVE")
                                        
                                        val combined = (verbs + nouns + adjectives).distinct()
                                        
                                        // Filter out trivial cases where variation is just the word itself (if only 1 result)
                                        // But if it's just the word, we might still show it to confirm? 
                                        // Avaz shows popup even if empty-ish? No, if empty it shouldn't show.
                                        if (combined.isNotEmpty() && combined != listOf(word)) {
                                            with(androidx.compose.ui.platform.AndroidUiDispatcher.Main) { // Go back in UI thread
                                                grammarBaseWord = word
                                                grammarVariations = combined
                                            }
                                        } else {
                                             // Fallback: If no variations found, maybe speak or do nothing?
                                             // For now do nothing.
                                        }
                                    }
                                },
                                onReorderFinished = if (uiState.isCaregiverMode) {
                                    { newButtons ->
                                        viewModel.updateBoardButtons(newButtons)
                                    }
                                } else null,
                                onInteractionEnd = viewModel::onInteractionEnd,
                                onPlaceholderLongPress = {
                                    showGenerateItemsDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                        }
                    } else {
                        // Loading STATE
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }

                    // Selection Bar (overlaid on top of grid)
                    if (uiState.isCaregiverMode && uiState.selectedButtonIds.isNotEmpty()) {
                        com.example.myaac.ui.components.SelectionBar(
                            selectedCount = uiState.selectedButtonIds.size,
                            allBoards = allBoards,
                            onClearSelection = { viewModel.clearSelection() },
                            onDelete = { viewModel.deleteSelectedButtons() },
                            onToggleHide = { viewModel.toggleHideSelectedButtons() },
                            onDuplicate = { viewModel.duplicateSelectedButtons() },
                            onMoveToBoard = { boardId -> viewModel.moveSelectedButtonsToBoard(boardId) },
                            onEdit = {
                                // Get the single selected button and edit it
                                val selectedId = uiState.selectedButtonIds.first()
                                val button = uiState.currentBoard?.buttons?.find { it.id == selectedId }
                                if (button != null) {
                                    buttonToEdit = button
                                    viewModel.clearSelection()
                                }
                            },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    

                }
            }
        }
    }

    
    if (grammarVariations != null) {
        com.example.myaac.ui.components.GrammarPopup(
            baseWord = grammarBaseWord,
            variations = grammarVariations!!,
            onDismiss = { grammarVariations = null },
            onSelect = { variant ->
                grammarVariations = null
                // Add to sentence bar
                val tempButton = com.example.myaac.model.AacButton(
                    id = "grammar_${java.util.UUID.randomUUID()}",
                    label = variant,
                    speechText = variant,
                    action = ButtonAction.Speak(variant)
                )
                viewModel.onButtonPress(tempButton)
                onSpeak(variant)
            }
        )
    }
}
