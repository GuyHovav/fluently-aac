package com.example.myaac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myaac.model.Board
import com.example.myaac.R

@Composable
fun SidebarContent(
    boards: List<Board>,
    currentBoardId: String,
    isCaregiverMode: Boolean,
    onBoardSelect: (Board) -> Unit,
    onCreateBoard: (String, String) -> Unit,
    onDeleteBoard: (Board) -> Unit,
    onUpdateBoard: (Board) -> Unit,
    onUnlock: (String) -> Boolean,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var boardToEdit by remember { mutableStateOf<Board?>(null) }
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
    
    if (boardToEdit != null) {
        EditBoardDialog(
            board = boardToEdit!!,
            onDismiss = { boardToEdit = null },
            onSave = { updatedBoard ->
                onUpdateBoard(updatedBoard)
                boardToEdit = null
            },
            onDelete = {
                onDeleteBoard(boardToEdit!!)
                boardToEdit = null
            }
        )
    }

    if (showPinDialog) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.enter_admin_pin)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { 
                            pin = it
                            error = false
                        },
                        label = { Text(stringResource(R.string.pin)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = error
                    )
                    if (error) {
                        Text(
                            stringResource(R.string.incorrect_pin),
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
                    Text(stringResource(R.string.unlock))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.boards), style = MaterialTheme.typography.titleLarge)
            if (isCaregiverMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.new_board))
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
                if (isCaregiverMode) {
                    androidx.compose.material3.IconButton(onClick = { boardToEdit = board }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}
        
        Spacer(modifier = Modifier.weight(1f))
        Divider()
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isCaregiverMode) {
                // Settings button
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings))
                }
            }
            
            if (isCaregiverMode) {
                 Button(
                    onClick = { onLock() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.exit_admin_mode))
                }
            } else {
                TextButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.enter_admin_mode))
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
        title = { Text(stringResource(R.string.create_new_board)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.board_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(stringResource(R.string.topic_optional)) },
                    placeholder = { Text(stringResource(R.string.topic_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (topic.isNotBlank()) {
                     Text(
                         text = stringResource(R.string.magic_board_will_be_generated),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.primary,
                         modifier = Modifier.padding(top = 4.dp)
                     )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name, topic) }) {
                Text(if (topic.isNotBlank()) stringResource(R.string.magic_create) else stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun EditBoardDialog(
    board: Board,
    onDismiss: () -> Unit,
    onSave: (Board) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(board.name) }
    var iconPath by remember { mutableStateOf(board.iconPath) }
    var showIconSearch by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showIconSearch) {
        SymbolSearchDialog(
            initialQuery = name,
            onDismiss = { showIconSearch = false },
            onSymbolSelected = { url, _ ->
                iconPath = url
                showIconSearch = false
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_board_title)) },
            text = { Text(stringResource(R.string.delete_board_message, board.name)) },
            confirmButton = {
                Button(
                    onClick = { 
                        onDelete()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                     Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_board_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Icon Preview / Changer
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showIconSearch = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (iconPath != null) {
                        coil.compose.AsyncImage(
                            model = iconPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(48.dp))
                    }
                    
                    // Edit Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.change_icon_desc),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.board_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Delete Button (Only on regular boards, not home)
                if (board.id != "home") {
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_board_button))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(board.copy(name = name, iconPath = iconPath))
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

object NavigationDrawerItemDefaults {
    val ItemPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
}
