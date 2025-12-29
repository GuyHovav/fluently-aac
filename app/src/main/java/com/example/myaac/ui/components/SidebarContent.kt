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
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Slider
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myaac.model.Board
import com.example.myaac.R
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch

@Composable
fun SidebarContent(
    boards: List<Board>,
    currentBoardId: String,
    isCaregiverMode: Boolean,
    onBoardSelect: (Board) -> Unit,
    onCreateBoard: (String, String) -> Unit,
    onDeleteBoard: (Board) -> Unit,
    onUpdateBoard: (Board) -> Unit,
    onExpandBoard: (Board, Int) -> Unit,
    onUnlock: (String) -> Boolean,
    onLock: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreateVisualScene: (String, Bitmap, android.net.Uri) -> Unit,
    onCreateQuickBoard: (String, List<Bitmap>) -> Unit,
    onSuggestBoardName: suspend (List<Bitmap>) -> String = { _ -> "" } // Default empty logic for preview/compat
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var showVisualSceneDialog by remember { mutableStateOf(false) }
    var showQuickBoardDialog by remember { mutableStateOf(false) }
    var boardToEdit by remember { mutableStateOf<Board?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateBoardDialog(
            existingBoards = boards,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, topic ->
                onCreateBoard(name, topic)
                showCreateDialog = false
            },
            onOpenExistingBoard = { board ->
                onBoardSelect(board)
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
            },
            onExpand = { itemCount ->
                onExpandBoard(boardToEdit!!, itemCount)
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
                Box {
                    Button(
                        onClick = { showCreateMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.create_new_board))
                    }
                    DropdownMenu(
                        expanded = showCreateMenu,
                        onDismissRequest = { showCreateMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_board)) },
                            onClick = {
                                showCreateMenu = false
                                showCreateDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_visual_scene)) },
                            onClick = {
                                showCreateMenu = false
                                showVisualSceneDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.quick_board_from_photo)) },
                            onClick = {
                                showCreateMenu = false
                                showQuickBoardDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                        )
                    }
                }
            }
        }
        if (showQuickBoardDialog) {
            QuickBoardDialog(
                existingBoards = boards,
                onDismiss = { showQuickBoardDialog = false },
                onCreate = { name, bitmaps ->
                    onCreateQuickBoard(name, bitmaps)
                    showQuickBoardDialog = false
                },
                onOpenExistingBoard = { board ->
                    onBoardSelect(board)
                },
                onSuggestName = onSuggestBoardName
            )
        }
        if (showVisualSceneDialog) {
            VisualSceneDialog(
                existingBoards = boards,
                onDismiss = { showVisualSceneDialog = false },
                onCreate = { name, bitmap, uri ->
                    onCreateVisualScene(name, bitmap, uri)
                    showVisualSceneDialog = false
                },
                onOpenExistingBoard = { board ->
                    onBoardSelect(board)
                }
            )
        }
        Divider()
        val visibleBoards = remember(boards, isCaregiverMode) {
            if (isCaregiverMode) {
                boards
            } else {
                boards.filter { board ->
                    board.id == "home" || board.buttons.any { button ->
                        !button.hidden && (button.label.isNotEmpty() || !button.iconPath.isNullOrEmpty())
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(visibleBoards) { board ->
                NavigationDrawerItem(
                    label = { Text(board.name) },
                    selected = board.id == currentBoardId,
                    onClick = { onBoardSelect(board) },
                    icon = {
                        if (!board.iconPath.isNullOrEmpty()) {
                             AsyncImage(
                                model = board.iconPath,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        }
                    },
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
fun CreateBoardDialog(
    existingBoards: List<Board> = emptyList(),
    onDismiss: () -> Unit, 
    onCreate: (String, String) -> Unit,
    onOpenExistingBoard: (Board) -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateBoard by remember { mutableStateOf<Board?>(null) }
    
    // Check for duplicate board name (case-insensitive)
    val isDuplicate = remember(name, existingBoards) {
        existingBoards.any { it.name.equals(name.trim(), ignoreCase = true) }
    }
    
    // Find the duplicate board
    val findDuplicateBoard: () -> Board? = {
        existingBoards.find { it.name.equals(name.trim(), ignoreCase = true) }
    }
    
    if (showDuplicateDialog && duplicateBoard != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text(stringResource(R.string.duplicate_board_name_title)) },
            text = { Text(stringResource(R.string.duplicate_board_name_message, duplicateBoard!!.name)) },
            confirmButton = {
                Button(onClick = { 
                    showDuplicateDialog = false
                    onOpenExistingBoard(duplicateBoard!!)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.open_existing_board))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDuplicateDialog = false
                    name = "" // Clear the name field so user can enter a new name
                }) {
                    Text(stringResource(R.string.choose_different_name))
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_new_board)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.board_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = isDuplicate && name.isNotBlank(),
                    supportingText = if (isDuplicate && name.isNotBlank()) {
                        { Text(stringResource(R.string.board_name_error), color = MaterialTheme.colorScheme.error) }
                    } else null
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
            Button(onClick = { 
                if (name.isNotBlank()) {
                    if (isDuplicate) {
                        // Show duplicate dialog
                        duplicateBoard = findDuplicateBoard()
                        showDuplicateDialog = true
                    } else {
                        onCreate(name.trim(), topic)
                    }
                }
            }) {
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
    onDelete: () -> Unit,
    onExpand: (Int) -> Unit
) {
    var name by remember { mutableStateOf(board.name) }
    var iconPath by remember { mutableStateOf(board.iconPath) }
    var showIconSearch by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var itemCount by remember { mutableStateOf(20) }

    if (showGenerateDialog) {
        GenerateItemsDialog(
            onDismiss = { showGenerateDialog = false },
            onGenerate = { itemCount ->
                onExpand(itemCount)
                showGenerateDialog = false
            }
        )
    }

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

                // Expand Board Button (Only for regular boards, not visual scenes)
                if (board.backgroundImagePath.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showGenerateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.expand_board))
                    }
                }
                
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

@Composable
fun VisualSceneDialog(
    existingBoards: List<Board> = emptyList(),
    onDismiss: () -> Unit, 
    onCreate: (String, Bitmap, Uri) -> Unit,
    onOpenExistingBoard: (Board) -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateBoard by remember { mutableStateOf<Board?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Check for duplicate board name (case-insensitive)
    val isDuplicate = remember(name, existingBoards) {
        existingBoards.any { it.name.equals(name.trim(), ignoreCase = true) }
    }
    
    // Find the duplicate board
    val findDuplicateBoard: () -> Board? = {
        existingBoards.find { it.name.equals(name.trim(), ignoreCase = true) }
    }
    
    if (showDuplicateDialog && duplicateBoard != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text(stringResource(R.string.duplicate_board_name_title)) },
            text = { Text(stringResource(R.string.duplicate_board_name_message, duplicateBoard!!.name)) },
            confirmButton = {
                Button(onClick = { 
                    showDuplicateDialog = false
                    onOpenExistingBoard(duplicateBoard!!)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.open_existing_board))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDuplicateDialog = false
                    name = "" // Clear the name field so user can enter a new name
                }) {
                    Text(stringResource(R.string.choose_different_name))
                }
            }
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            // Convert to bitmap
            try {
                imageBitmap = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            showCamera = true
        }
    }

    if (showCamera) {
        Dialog(onDismissRequest = { showCamera = false }) {
            CameraScreen(
                onImageCaptured = { bitmap ->
                    imageBitmap = bitmap
                    imageUri = Uri.parse("camera_temp") // Placeholder
                    showCamera = false
                },
                onError = { /* Handle error */ },
                onClose = { showCamera = false },
                maxPhotos = 1
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_visual_scene)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.visual_scene_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        AsyncImage(
                            model = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(48.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_select_gallery))
                    }
                    OutlinedButton(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                showCamera = true
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_take_photo))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.scene_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = isDuplicate && name.isNotBlank(),
                    supportingText = if (isDuplicate && name.isNotBlank()) {
                        { Text(stringResource(R.string.board_name_error), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (name.isNotBlank() && imageBitmap != null && !isDuplicate) {
                                onCreate(name.trim(), imageBitmap!!, imageUri ?: Uri.EMPTY)
                            } else {
                                // Just hide keyboard if not ready
                                // default behavior of Done is often to hide, but let's be explicit if needed
                            }
                        }
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (name.isNotBlank() && imageBitmap != null) {
                        if (isDuplicate) {
                            // Show duplicate dialog
                            duplicateBoard = findDuplicateBoard()
                            showDuplicateDialog = true
                        } else {
                            onCreate(name.trim(), imageBitmap!!, imageUri ?: Uri.EMPTY)
                        }
                    }
                },
                enabled = name.isNotBlank() && imageBitmap != null
            ) {
                Text(stringResource(R.string.create))
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
fun QuickBoardDialog(
    existingBoards: List<Board> = emptyList(),
    onDismiss: () -> Unit, 
    onCreate: (String, List<Bitmap>) -> Unit,
    onOpenExistingBoard: (Board) -> Unit = {},
    onSuggestName: suspend (List<Bitmap>) -> String = { "" }
) {
    var name by remember { mutableStateOf("") }
    var imageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var duplicateBoard by remember { mutableStateOf<Board?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    var isSuggestingName by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current

    // Check for duplicate board name (case-insensitive)
    val isDuplicate = remember(name, existingBoards) {
        existingBoards.any { it.name.equals(name.trim(), ignoreCase = true) }
    }
    
    // Find the duplicate board
    val findDuplicateBoard: () -> Board? = {
        existingBoards.find { it.name.equals(name.trim(), ignoreCase = true) }
    }
    
    if (showDuplicateDialog && duplicateBoard != null) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text(stringResource(R.string.duplicate_board_name_title)) },
            text = { Text(stringResource(R.string.duplicate_board_name_message, duplicateBoard!!.name)) },
            confirmButton = {
                Button(onClick = { 
                    showDuplicateDialog = false
                    onOpenExistingBoard(duplicateBoard!!)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.open_existing_board))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDuplicateDialog = false
                    name = "" // Clear the name field so user can enter a new name
                }) {
                    Text(stringResource(R.string.choose_different_name))
                }
            }
        )
    }

    // Helper to scale down bitmaps to prevent memory issues
    fun scaleBitmapSafe(bitmap: Bitmap): Bitmap {
        val maxDimension = 1024
        if (bitmap.width <= maxDimension && bitmap.height <= maxDimension) return bitmap
        
        val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && imageBitmaps.size < 5) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }
                imageBitmaps = imageBitmaps + scaleBitmapSafe(bitmap)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            showCamera = true
        }
    }

    if (showCamera) {
        Dialog(onDismissRequest = { showCamera = false }) {
           CameraScreen(
                onImageCaptured = { bitmap ->
                    if (imageBitmaps.size < 5) {
                        imageBitmaps = imageBitmaps + scaleBitmapSafe(bitmap)
                        android.widget.Toast.makeText(context, "Photo added", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },

                onError = { /* Handle error */ },
                onClose = { showCamera = false },
                currentPhotoCount = imageBitmaps.size,
                maxPhotos = 5
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_board_from_photo)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.multi_photo_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                // Photo grid preview
                if (imageBitmaps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No photos yet", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(imageBitmaps.size) { index ->
                            Box {
                                AsyncImage(
                                    model = imageBitmaps[index],
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                // Remove button
                                androidx.compose.material3.IconButton(
                                    onClick = { 
                                        imageBitmaps = imageBitmaps.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${imageBitmaps.size} photo${if (imageBitmaps.size != 1) "s" else ""} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = imageBitmaps.size < 5,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (imageBitmaps.isEmpty()) stringResource(R.string.btn_add_photo) else stringResource(R.string.btn_add_another))
                    }
                    OutlinedButton(
                        onClick = {
                             if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                showCamera = true
                             } else {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                             }
                        },
                        enabled = imageBitmaps.size < 5,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_take_photo))
                    }
                }
                if (imageBitmaps.size >= 5) {
                    Text(
                        "Maximum 5 photos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.board_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = isDuplicate && name.isNotBlank(),
                    supportingText = if (isDuplicate && name.isNotBlank()) {
                        { Text(stringResource(R.string.board_name_error), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    trailingIcon = {
                        if (imageBitmaps.isNotEmpty()) {
                            androidx.compose.material3.IconButton(
                                onClick = { 
                                    scope.launch {
                                        isSuggestingName = true
                                        val suggestion = onSuggestName(imageBitmaps)
                                        if (suggestion.isNotBlank()) {
                                            name = suggestion
                                        }
                                        isSuggestingName = false
                                    }
                                },
                                enabled = !isSuggestingName
                            ) {
                                if (isSuggestingName) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AutoAwesome, 
                                        contentDescription = "Suggest Name",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (name.isNotBlank() && imageBitmaps.isNotEmpty()) {
                        if (isDuplicate) {
                            // Show duplicate dialog
                            duplicateBoard = findDuplicateBoard()
                            showDuplicateDialog = true
                        } else {
                            onCreate(name.trim(), imageBitmaps)
                        }
                    }
                },
                enabled = name.isNotBlank() && imageBitmaps.isNotEmpty()
            ) {
                Text(stringResource(R.string.create))
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
