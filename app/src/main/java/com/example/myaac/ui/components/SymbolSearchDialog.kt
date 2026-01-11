package com.example.myaac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
// Import new service classes
import com.example.myaac.data.remote.SymbolResult
import com.example.myaac.data.remote.SymbolService
import com.example.myaac.data.remote.ArasaacService
import com.example.myaac.data.remote.GlobalSymbolsService
import com.example.myaac.data.remote.CompositeSymbolService
import com.example.myaac.data.remote.GoogleImageService
import kotlinx.coroutines.launch

@Composable
fun SymbolSearchDialog(
    initialQuery: String = "",
    defaultLanguage: String = "en",
    symbolLibrary: String = "ARASAAC",
    onDismiss: () -> Unit,
    onSymbolSelected: (url: String, label: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue(initialQuery)) }
    var results by remember { mutableStateOf<List<SymbolResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Language Support
    val languages = mapOf(
        "en" to "English",
        "es" to "Spanish",
        "he" to "Hebrew",
        "de" to "German",
        "fr" to "French",
        "it" to "Italian",
        "pt" to "Portuguese"
    )
    
    // Normalize default language to supported list or fallback to english
    // Handle legacy Hebrew code "iw" -> "he"
    val inputLanguage = if (defaultLanguage == "iw") "he" else defaultLanguage
    val normalizedDefault = if (languages.containsKey(inputLanguage)) inputLanguage else "en"
    var selectedLanguage by remember { mutableStateOf(normalizedDefault) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Create composite service with fallback
    // Primary service is based on symbolLibrary preference, secondary is the other one
    val symbolService: SymbolService = remember(symbolLibrary) {
        val services = if (symbolLibrary == "MULBERRY") {
            listOf(
                GlobalSymbolsService("mulberry"),
                ArasaacService(),
                GoogleImageService()
            )
        } else {
            // ARASAAC: Try direct, then via Global Symbols, then fallback to Mulberry, then Google
            listOf(
                ArasaacService(),
                GlobalSymbolsService("arasaac"), 
                GlobalSymbolsService("mulberry"),
                GoogleImageService()
            )
        }
        
        // Create composite service with fallback
        CompositeSymbolService(
            services = services,
            combineResults = false,
            minRequiredResults = 2
        )
    }

    fun performSearch() {
        if (searchQuery.text.isBlank()) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val list = symbolService.search(searchQuery.text, selectedLanguage)
                if (list.isEmpty()) {
                    errorMessage = "No symbols found."
                }
                results = list
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Only auto-search if query is present
        if (initialQuery.isNotBlank() && initialQuery.length > 2) { // minimal length check
            performSearch()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Search Symbols (${if(symbolLibrary=="MULBERRY") "Mulberry → Arasaac" else "Arasaac → Mulberry"})", style = MaterialTheme.typography.titleLarge)
                    
                    // Language Selector
                    Box {
                        TextButton(onClick = { showLanguageMenu = true }) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(languages[selectedLanguage] ?: selectedLanguage)
                        }
                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false }
                        ) {
                            languages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedLanguage = code
                                        showLanguageMenu = false
                                        // Optional: Re-trigger search if query exists
                                        if (searchQuery.text.isNotBlank()) {
                                            performSearch()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search (e.g. Dog, Eat)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { performSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Results
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(results) { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSymbolSelected(item.url, item.label) } // Use SymbolResult properties
                                        .padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = item.url, // Use SymbolResult properties
                                        contentDescription = item.label,
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

