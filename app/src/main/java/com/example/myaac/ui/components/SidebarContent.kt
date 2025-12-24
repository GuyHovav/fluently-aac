package com.example.myaac.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myaac.model.Board

@Composable
fun SidebarContent(
    boards: List<Board>,
    currentBoardId: String,
    isCaregiverMode: Boolean,
    onBoardSelect: (Board) -> Unit,
    onCreateBoard: (String, String) -> Unit,
    onDeleteBoard: (Board) -> Unit,
    onUnlock: (String) -> Boolean,
    onLock: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var boardToDelete by remember { mutableStateOf<Board?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateBoardDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, topic ->
                onCreateBoard(name, topic)
                showCreateDialog = false
            }
        )
    }
    
    if (boardToDelete != null) {
        AlertDialog(
            onDismissRequest = { boardToDelete = null },
            title = { Text("Delete Board?") },
            text = { Text("Are you sure you want to delete '${boardToDelete?.name}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { 
                        boardToDelete?.let { onDeleteBoard(it) }
                        boardToDelete = null 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                     Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { boardToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Enter Admin PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { 
                            pin = it
                            error = false
                        },
                        label = { Text("PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = error
                    )
                    if (error) {
                        Text(
                            "Incorrect PIN",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    if (onUnlock(pin)) {
                        showPinDialog = false
                    } else {
                        error = true
                    }
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Boards", style = MaterialTheme.typography.titleLarge)
            if (isCaregiverMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("New Board")
                }
            }
        }
        Divider()
        LazyColumn {
            items(boards) { board ->
                NavigationDrawerItem(
                    label = { Text(board.name) },
                    selected = board.id == currentBoardId,
                    onClick = { onBoardSelect(board) },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    badge = {
                        // Don't allow deleting home board (assuming id "home" is fixed)
                        if (isCaregiverMode && board.id != "home") {
                            androidx.compose.material3.IconButton(onClick = { boardToDelete = board }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Divider()
        Column(modifier = Modifier.padding(16.dp)) {
            if (isCaregiverMode) {
                 Button(
                    onClick = { onLock() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Exit Admin Mode")
                }
            } else {
                TextButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enter Admin Mode")
                }
            }
        }
    }
}

@Composable
fun CreateBoardDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Board") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Board Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic (Optional - Magic!)") },
                    placeholder = { Text("e.g. Zoo, Beach, Kitchen") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (topic.isNotBlank()) {
                     Text(
                         text = "✨ Magic Board will be generated using AI",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.primary,
                         modifier = Modifier.padding(top = 4.dp)
                     )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name, topic) }) {
                Text(if (topic.isNotBlank()) " ✨ Magic Create" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

object NavigationDrawerItemDefaults {
    val ItemPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
}
