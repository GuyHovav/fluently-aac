package com.example.myaac.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myaac.MyAacApplication
import com.example.myaac.R

/**
 * Screen for managing custom pronunciation corrections.
 * Accessible from settings, allows caregivers to add/edit/remove custom pronunciations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronunciationManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyAacApplication
    val pronunciationRepo = app.pronunciationRepository
    
    var showAddDialog by remember { mutableStateOf(false) }
    var customPronunciations by remember { 
        mutableStateOf(pronunciationRepo.dictionary.getCustomPronunciations())
    }
    val builtInPronunciations = pronunciationRepo.dictionary.getBuiltInPronunciations()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pronunciation Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add custom pronunciation")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Custom Pronunciations Section
            item {
                Text(
                    text = "Custom Pronunciations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (customPronunciations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No custom pronunciations yet. Tap + to add one.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(customPronunciations.toList()) { (original, corrected) ->
                    PronunciationItem(
                        original = original,
                        corrected = corrected,
                        isCustom = true,
                        onDelete = {
                            pronunciationRepo.removeCustomPronunciation(original)
                            customPronunciations = pronunciationRepo.dictionary.getCustomPronunciations()
                        }
                    )
                }
            }
            
            // Built-in Pronunciations Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Built-in Pronunciations",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "These are pre-configured common words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            items(builtInPronunciations.toList()) { (original, corrected) ->
                PronunciationItem(
                    original = original,
                    corrected = corrected,
                    isCustom = false,
                    onDelete = null
                )
            }
        }
    }
    
    if (showAddDialog) {
        AddPronunciationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { original, corrected ->
                pronunciationRepo.addCustomPronunciation(original, corrected)
                customPronunciations = pronunciationRepo.dictionary.getCustomPronunciations()
                showAddDialog = false
            }
        )
    }
}

@Composable
fun PronunciationItem(
    original: String,
    corrected: String,
    isCustom: Boolean,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Original: $original",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Corrected: $corrected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (isCustom && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddPronunciationDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var originalWord by remember { mutableStateOf("") }
    var correctedWord by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Pronunciation") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add a word that is mispronounced and its correct form with nikud (vowel points).",
                    style = MaterialTheme.typography.bodySmall
                )
                
                OutlinedTextField(
                    value = originalWord,
                    onValueChange = { originalWord = it },
                    label = { Text("Original Word (without nikud)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = correctedWord,
                    onValueChange = { correctedWord = it },
                    label = { Text("Corrected Word (with nikud)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Example: רעב → רָעֵב",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (originalWord.isNotBlank() && correctedWord.isNotBlank()) {
                        onAdd(originalWord.trim(), correctedWord.trim())
                    }
                },
                enabled = originalWord.isNotBlank() && correctedWord.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
