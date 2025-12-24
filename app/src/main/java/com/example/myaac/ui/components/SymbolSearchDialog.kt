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
import com.example.myaac.data.remote.ArasaacPictogram
import com.example.myaac.data.remote.ArasaacService
import kotlinx.coroutines.launch

@Composable
fun SymbolSearchDialog(
    onDismiss: () -> Unit,
    onSymbolSelected: (url: String, label: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<ArasaacPictogram>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val arasaacService = remember { ArasaacService() }

    fun performSearch() {
        if (searchQuery.text.isBlank()) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val list = arasaacService.searchPictograms(searchQuery.text)
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
                    Text("Search Symbols", style = MaterialTheme.typography.titleLarge)
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
                                val url = arasaacService.getImageUrl(item._id)
                                val label = item.keywords.firstOrNull()?.keyword ?: ""
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSymbolSelected(url, label) }
                                        .padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        modifier = Modifier.size(80.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
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
