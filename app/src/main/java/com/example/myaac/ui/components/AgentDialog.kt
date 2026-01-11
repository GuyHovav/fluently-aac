package com.example.myaac.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    response: String? = null,
    isLoading: Boolean = false,
    languageCode: String = "en"
) {
    var query by remember { mutableStateOf("") }
    
    // Speech Recognition Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                query = spokenText
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fluently Assistant")
            }
        },
        text = {
            Column {
                if (response != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = response,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                if (isLoading) {
                     LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                     Text(
                         text = "Thinking...", 
                         modifier = Modifier.padding(top=8.dp),
                         style = MaterialTheme.typography.bodySmall
                     )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Ask something or create a board...") },
                        placeholder = { Text("e.g., 'Create a nightclub board without alcohol'") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                        trailingIcon = {
                             IconButton(onClick = {
                                 try {
                                     val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                         putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                         // Map 'iw' to 'he' for speech recognizer compatibility if needed, though Android usually handles both.
                                         val locale = if (languageCode == "iw") "he" else languageCode
                                         putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                                         putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                                     }
                                     speechLauncher.launch(intent)
                                 } catch (e: Exception) {
                                     // Handle case where speech recognition is not available
                                     e.printStackTrace()
                                 }
                             }) {
                                 Icon(Icons.Default.Mic, contentDescription = "Speak")
                             }
                        }
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                Button(
                    onClick = { 
                        if (query.isNotBlank()) {
                            onSubmit(query) 
                            query = "" // Clear after send? Or keep?
                            // User might want to ask follow up, but for now single turn.
                        }
                    },
                    enabled = query.isNotBlank()
                ) {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
