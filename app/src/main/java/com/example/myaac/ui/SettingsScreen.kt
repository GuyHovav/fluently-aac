package com.example.myaac.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myaac.data.repository.AppSettings
import com.example.myaac.data.repository.SettingsRepository
import com.example.myaac.data.model.DisabilityType
import com.example.myaac.model.AppShortcut
import com.example.myaac.ui.components.AppPickerDialog
import com.example.myaac.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    authRepository: AuthRepository,
    onNavigateBack: () -> Unit,
    onBackup: (String, (String) -> Unit) -> Unit,
    onRestore: (String, (String) -> Unit) -> Unit
) {
    val settings by repository.settings.collectAsState()
    val user by authRepository.authState.collectAsState(initial = authRepository.currentUser)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Sign In Launcher
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                // Firebase Auth with Google
                scope.launch {
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    try {
                        val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                        authResult.user?.let { firebaseUser ->
                            authRepository.createUserProfileIfNew(firebaseUser)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Show error toast
                    }
                }
            }
        } catch (e: ApiException) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {

            SettingsSection("Account") {
                if (user == null) {
                    Button(
                        onClick = { 
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(com.example.myaac.R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            val googleSignInClient = GoogleSignIn.getClient(context, gso)
                            signInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign In with Google")
                    }
                    Text(
                        "Sign in to backup your boards and share them across devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user?.photoUrl != null) {
                            coil.compose.AsyncImage(
                                model = user!!.photoUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Column {
                            Text(user!!.displayName ?: "User", style = MaterialTheme.typography.titleMedium)
                            Text(user!!.email ?: "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { authRepository.signOut() }
                    ) {
                        Text("Sign Out")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Cloud Sync", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                user?.uid?.let { uid ->
                                    val toast = android.widget.Toast.makeText(context, "Backing up...", android.widget.Toast.LENGTH_SHORT)
                                    toast.show()
                                    onBackup(uid) { result ->
                                        // Show result (we need to trigger this on UI thread if callback is background, but standard Toast is fine usually or use LaunchedEffect if state changed)
                                        // Since result is string, just show toast
                                        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Backup")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                user?.uid?.let { uid ->
                                    val toast = android.widget.Toast.makeText(context, "Restoring...", android.widget.Toast.LENGTH_SHORT)
                                    toast.show()
                                    onRestore(uid) { result ->
                                        android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restore")
                        }
                    }
                }
            }

            SettingsSection("Display") {
                 // Symbol Library
                Text("Symbol Library", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.symbolLibrary == "ARASAAC",
                        onClick = { repository.setSymbolLibrary("ARASAAC") }
                    )
                    Text("Arasaac (Standard)")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = settings.symbolLibrary == "MULBERRY",
                        onClick = { repository.setSymbolLibrary("MULBERRY") }
                    )
                    Text("Mulberry (Modern)")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Font Family
                Text("Font Family", style = MaterialTheme.typography.titleSmall)
                // Simplified dropdown or selection for font family
                val fonts = listOf("System", "OpenDyslexic", "Atkinson Hyperlegible", "Andika")
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(settings.fontFamily)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        fonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font) },
                                onClick = {
                                    repository.setFontFamily(font)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                 // Text Scale
                Text("Text Scale: ${(settings.textScale * 100).toInt()}%")
                Slider(
                    value = settings.textScale,
                    onValueChange = { repository.setTextScale(it) },
                    valueRange = 0.5f..2.0f,
                    steps = 15,
                    modifier = Modifier.testTag("text_scale_slider")
                )
            
                // Display Scale
                 if (!settings.useSystemSettings) {
                    Text("Display Scale: ${(settings.displayScale * 100).toInt()}%")
                    Slider(
                        value = settings.displayScale,
                        onValueChange = { repository.setDisplayScale(it) },
                        valueRange = 0.8f..1.5f,
                        steps = 7
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.useSystemSettings,
                        onCheckedChange = { repository.setUseSystemSettings(it) }
                    )
                    Text("Use System Display Settings")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                 Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.showHorizontalNavigation,
                        onCheckedChange = { repository.setHorizontalNavigationEnabled(it) },
                        modifier = Modifier.testTag("horizontal_nav_checkbox")
                    )
                    Text("Show Horizontal Navigation")
                }
                
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Text("Show Symbols in Sentence Bar")
                }

                 Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.landscapeBigSentence,
                        onCheckedChange = { repository.setLandscapeBigSentence(it) }
                    )
                    Column {
                        Text("Show Big Sentence in Landscape")
                        Text(
                            "Automatically expand the sentence when rotating to landscape mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }

            SettingsSection("Speech") {
                Text("TTS Rate: ${(settings.ttsRate * 100).toInt()}%")
                Slider(
                    value = settings.ttsRate,
                    onValueChange = { repository.setTtsRate(it) },
                    valueRange = 0.5f..2.0f
                )
            }

            SettingsSection("App Shortcuts") {
                val appShortcuts = remember(settings.appShortcutsJson) {
                    repository.getAppShortcuts()
                }
                var showAppPicker by remember { mutableStateOf(false) }
                
                Text("Configure which apps appear on the Apps board", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (appShortcuts.isEmpty()) {
                    Text("No apps configured yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    appShortcuts.forEach { shortcut ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(shortcut.appName)
                            TextButton(onClick = {
                                val updated = appShortcuts.filter { it.packageName != shortcut.packageName }
                                repository.setAppShortcuts(updated)
                            }) {
                                Text("Remove")
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { showAppPicker = true }) {
                    Text("Add App")
                }
                
                if (showAppPicker) {
                    AppPickerDialog(
                        onDismiss = { showAppPicker = false },
                        onAppSelected = { packageName, appName ->
                            val updated = appShortcuts + AppShortcut(packageName, appName)
                            repository.setAppShortcuts(updated)
                            showAppPicker = false
                        }
                    )
                }
            }

            SettingsSection("Word Prediction") {
                // Enable/Disable Predictions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = settings.predictionEnabled,
                        onCheckedChange = { repository.setPredictionEnabled(it) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Enable Word Prediction", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Show predicted words below the sentence bar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Only show other options if predictions are enabled
                if (settings.predictionEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // AI Predictions Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = settings.aiPredictionEnabled,
                            onCheckedChange = { repository.setAiPredictionEnabled(it) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("AI-Powered Predictions", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Use Gemini AI for smarter, contextual predictions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Show Symbols Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = settings.showSymbolsInPredictions,
                            onCheckedChange = { repository.setShowSymbolsInPredictions(it) }
                        )
                        Text("Show symbols in predictions")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Prediction Count Slider
                    Text("Number of Predictions: ${settings.predictionCount}")
                    Slider(
                        value = settings.predictionCount.toFloat(),
                        onValueChange = { repository.setPredictionCount(it.toInt()) },
                        valueRange = 3f..8f,
                        steps = 4
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Learn from Usage Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = settings.learnFromUsage,
                            onCheckedChange = { repository.setLearnFromUsage(it) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Learn from Usage", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Improve predictions based on your vocabulary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Automatic Grammar Check Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = settings.autoGrammarCheck,
                            onCheckedChange = { repository.setAutoGrammarCheck(it) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Automatic Grammar Check", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Apply grammar corrections immediately. If disabled, a magic wand button will appear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Clear Learned Vocabulary Button
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val scope = rememberCoroutineScope()
                    var showClearDialog by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Learned Vocabulary")
                    }
                    
                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear Learned Vocabulary?") },
                            text = { 
                                Text("This will reset all learned word patterns and frequencies. Predictions will start fresh.")
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val database = com.example.myaac.data.local.AppDatabase.getDatabase(context)
                                            database.wordFrequencyDao().clearAllLearnedData()
                                            showClearDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Clear")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            SettingsSection("Grid Generation") {
                 Text("Items to Generate: ${settings.itemsToGenerate}")
                 Slider(
                    value = settings.itemsToGenerate.toFloat(),
                     onValueChange = { repository.setItemsToGenerate(it.toInt()) },
                     valueRange = 1f..50f,
                     steps = 49
                 )
                 
                 Text("Max Board Capacity: ${settings.maxBoardCapacity}")
                 Slider(
                    value = settings.maxBoardCapacity.toFloat(),
                     onValueChange = { repository.setMaxBoardCapacity(it.toInt()) },
                     valueRange = 50f..1000f,
                     steps = 19
                 )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))
    }
}
