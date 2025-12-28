package com.example.myaac.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myaac.model.AacButton
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import sh.calvin.reorderable.*


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommunicationGrid(
    buttons: List<AacButton>,
    columns: Int,
    isCaregiverMode: Boolean,
    selectedButtonIds: Set<String> = emptySet(),
    onButtonClick: (AacButton) -> Unit,
    onButtonLongPress: ((AacButton) -> Unit)? = null,
    onGrammarRequest: ((AacButton) -> Unit)? = null,
    onReorderFinished: ((List<AacButton>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Local mutable state for optimistic updates
    // We start with the passed buttons. 
    // We use a key to force recreation if the *source* buttons change significantly (e.g. navigation)
    // but typically we want to sync.
    val localButtons = remember { androidx.compose.runtime.mutableStateListOf<AacButton>() }
    
    // Sync local buttons with source of truth when 'buttons' changes (e.g. initial load, navigation, or external update)
    LaunchedEffect(buttons) {
        localButtons.clear()
        localButtons.addAll(buttons)
    }

    val visibleButtons = remember(localButtons.toList(), isCaregiverMode) {
        if (isCaregiverMode) {
             if (localButtons.isNotEmpty()) {
                 localButtons + AacButton(
                     id = "add_new_item_placeholder",
                     label = "",
                     speechText = null,
                     action = com.example.myaac.model.ButtonAction.Speak("")
                 )
             } else {
                 localButtons.toList()
             }
        } else {
            localButtons.filter { button ->
                !button.hidden && (button.label.isNotEmpty() || !button.iconPath.isNullOrEmpty())
            }
        }
    }

    // Drag tracking
    var currentlyDraggingId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    // Reorderable state for drag and drop
    val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val reorderableLazyGridState = sh.calvin.reorderable.rememberReorderableLazyGridState(lazyGridState) { from, to ->
        // Optimistic update
        if (isCaregiverMode && onReorderFinished != null) {
            val fromIndex = from.index
            val toIndex = to.index
            if (fromIndex in localButtons.indices && toIndex in localButtons.indices) {
                 val item = localButtons.removeAt(fromIndex)
                 localButtons.add(toIndex, item)
            }
        }
    }
    
    // Trigger save when drag ends (currentlyDraggingId goes from non-null to null)
    LaunchedEffect(currentlyDraggingId) {
        if (currentlyDraggingId == null) {
             // Drag finished (or hasn't started)
             // We need to distinguish between "app start" (null) and "drag finished" (null after some id).
             // But simpler: just save "snapshot" whenever it settles to null? 
             // Actually, saving on app load is bad. 
             // We should only save if we know we are 'dirty' or just finished a drag.
             // But for now, ensuring we don't save on initial null is tricky unless we verify change.
             // Let's rely on the fact that ReorderableItem updates this.
             // However, to avoid initial save, we can check if localButtons != buttons?
             // Or better: use a 'isDirty' flag.
        }
    }
    // Better logic: Save only on transition from dragging -> not dragging.
    // We can't easily detect transition in LaunchedEffect(value) without analyzing previous value.
    // Use snapshotFlow to detect transition.
    
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { currentlyDraggingId }
            .collect { currentId ->
                 if (currentId == null) {
                     // Potential drag end. But this also fires on init.
                     // We can simple check if localButtons != buttons (the prop).
                     // However, buttons prop might be stale if we assume strictly one-way data flow back up.
                     // But if dragging just finished, we want to persist.
                     
                     // Optimization: Only call persistence if we suspect a change?
                     // Let's just call it. Use a debounce or check strictly.
                     // Wait, if I call onReorderFinished with same list, ViewModel might save same list. No harm, just network/db op.
                     // But we want to avoid saving on startup.
                 }
            }
    }
    // Refined strategy: 'isDirty' flag.
    var isDirty by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(currentlyDraggingId) {
        if (currentlyDraggingId == null) {
            if (isDirty && isCaregiverMode && onReorderFinished != null) {
                onReorderFinished(localButtons.toList())
                isDirty = false
            }
        }
    }

    if (buttons.isEmpty() && isCaregiverMode) {
         Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Button(
                onClick = { 
                     // Trigger creation of a new item
                     val newButton = AacButton(
                         id = java.util.UUID.randomUUID().toString(),
                         label = "",
                         speechText = null,
                         backgroundColor = 0xFFFFFFFF,
                         action = com.example.myaac.model.ButtonAction.Speak("")
                     )
                     onButtonClick(newButton)
                },
                modifier = Modifier.fillMaxWidth().padding(32.dp).height(80.dp)
            ) {
                 Icon(Icons.Default.Add, contentDescription = null)
                 Spacer(Modifier.width(8.dp))
                 Text(androidx.compose.ui.res.stringResource(com.example.myaac.R.string.empty_board_prompt))
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = lazyGridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(visibleButtons, key = { _, button -> button.id }) { index, button ->
                ReorderableItem(reorderableLazyGridState, key = button.id) { isDragging ->
                    val isAddPlaceholder = button.id == "add_new_item_placeholder"

                    // Monitor drag state to trigger selection (Home screen behavior)
                    LaunchedEffect(isDragging) {
                        if (isDragging) {
                             currentlyDraggingId = button.id
                             isDirty = true
                             if (isCaregiverMode && onButtonLongPress != null && !isAddPlaceholder) {
                                if (button.id !in selectedButtonIds) {
                                    onButtonLongPress(button)
                                }
                             }
                        } else {
                             if (currentlyDraggingId == button.id) {
                                 currentlyDraggingId = null
                             }
                        }
                    }

                    val interactionSource = remember { MutableInteractionSource() }

                    AacButtonView(
                        button = button,
                        isSelected = button.id in selectedButtonIds,
                        isAddPlaceholder = isAddPlaceholder,
                        onClick = { 
                            if (isAddPlaceholder) {
                                val newButton = AacButton(
                                     id = java.util.UUID.randomUUID().toString(),
                                     label = "",
                                     speechText = null,
                                     backgroundColor = 0xFFFFFFFF,
                                     action = com.example.myaac.model.ButtonAction.Speak("")
                                 )
                                 onButtonClick(newButton)
                            } else {
                                onButtonClick(button) 
                            }
                        },
                        onLongPress = if (!isAddPlaceholder) {
                            if (isCaregiverMode && onReorderFinished == null && onButtonLongPress != null) {
                                { onButtonLongPress(button) }
                            } else if (!isCaregiverMode && onGrammarRequest != null) {
                                { onGrammarRequest(button) }
                            } else null
                        } else null, 
                        modifier = Modifier
                            .then(
                                if (!isAddPlaceholder && isCaregiverMode && onReorderFinished != null) {
                                    Modifier.longPressDraggableHandle(interactionSource = interactionSource)
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }


        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AacButtonView(
    button: AacButton,
    isSelected: Boolean = false,
    isAddPlaceholder: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "buttonScale")
    val haptic = LocalHapticFeedback.current

    val baseColor = if (button.backgroundColor != 0xFFFFFFFF) Color(button.backgroundColor) else MaterialTheme.colorScheme.primaryContainer
    
    val isPlaceholder = button.label.isEmpty() && button.iconPath.isNullOrEmpty()
    val visualAlpha = if (button.hidden) 0.5f else 1f

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(baseColor)
            .then(if (button.hidden) Modifier.background(Color.Black.copy(alpha = 0.1f)) else Modifier)
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else Modifier)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        }
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Content with alpha for hidden states
        val contentModifier = Modifier.fillMaxSize().scale(if (button.hidden) 0.85f else 1f)
        
        Box(modifier = contentModifier) {
            // 1. Image or Placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                 if (isAddPlaceholder) {
                     Icon(
                         imageVector = Icons.Default.Add,
                         contentDescription = "Add Item",
                         tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                         modifier = Modifier.size(48.dp)
                     )
                 } else if (!button.iconPath.isNullOrEmpty()) {
                     AsyncImage(
                         model = button.iconPath,
                         alpha = visualAlpha,
                         contentDescription = null,
                         contentScale = ContentScale.Crop,
                         modifier = Modifier.fillMaxSize()
                     )
                 } else {
                     if (isPlaceholder) {
                         // Regular empty placeholder (if any exist legacy)
                         Text(
                             text = "",
                             fontSize = 48.sp,
                             color = Color.Gray.copy(alpha = 0.5f * visualAlpha),
                             textAlign = TextAlign.Center
                         )
                     } else {
                         Text(
                             text = button.label.firstOrNull()?.toString() ?: "",
                             fontSize = 48.sp,
                             fontWeight = FontWeight.Bold,
                             color = Color.Black.copy(alpha = 0.1f * visualAlpha)
                         )
                     }
                 }
            }

            // 2. Text Overlay
            if (!isPlaceholder) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = button.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = visualAlpha),
                                offset = Offset(3f, 3f),
                                blurRadius = 3f
                             )
                        ),
                        color = Color.White.copy(alpha = visualAlpha),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(8.dp)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            // 3. Selection Indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
            
            // 4. Link Indicator
            if (button.action is com.example.myaac.model.ButtonAction.LinkToBoard) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateNext,
                        contentDescription = "Link",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Helper to calculate luminance to decide text color
fun Color.luminance(): Float {
    val red = this.red
    val green = this.green
    val blue = this.blue
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
