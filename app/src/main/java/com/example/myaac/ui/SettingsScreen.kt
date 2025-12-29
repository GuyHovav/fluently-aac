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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val settings by repository.settings.collectAsState()
    val scrollState = rememberScrollState()

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
                    steps = 15
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
                        onCheckedChange = { repository.setHorizontalNavigationEnabled(it) }
                    )
                    Text("Show Horizontal Navigation")
                }
                
                 Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.showSymbolsInSentenceBar,
                        onCheckedChange = { repository.setShowSymbolsInSentenceBar(it) }
                    )
                    Text("Show Symbols in Sentence Bar")
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
