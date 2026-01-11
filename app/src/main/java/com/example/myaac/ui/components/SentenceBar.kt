package com.example.myaac.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import com.example.myaac.util.isTablet
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SentenceBar(
    sentence: List<com.example.myaac.model.AacButton>,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onSpeak: (String) -> Unit,
    languageCode: String = "en",
    showSymbols: Boolean = false,
    showUndo: Boolean = false,
    onUndo: () -> Unit = {},
    predictions: List<String> = emptyList(),
    predictionSymbols: Map<String, String?> = emptyMap(),
    showPredictionSymbols: Boolean = false,
    isPredictionLoading: Boolean = false,
    onPredictionClick: (String) -> Unit = {},
    allowPredictionStrip: Boolean = true,
    onGrammarCheck: () -> Unit = {},
    windowSizeClass: com.example.myaac.util.WindowSizeClass? = null,
    landscapeBigSentence: Boolean = true,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isTablet = windowSizeClass?.isTablet() ?: false
    
    val displayedText = remember(sentence, languageCode) {
        val words = sentence.map { it.textToSpeak }
        com.example.myaac.util.GrammarEngine.fixSentence(words, languageCode)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isExpanded by remember { mutableStateOf(false) }

    if (isExpanded) {
        ExpandedMessageDialog(
            text = displayedText,
            onDismiss = { isExpanded = false }
        )
    }

    // Auto-scroll to the end when sentence changes
    androidx.compose.runtime.LaunchedEffect(sentence.size) {
        if (sentence.isNotEmpty()) {
            listState.animateScrollToItem(sentence.size - 1)
        }
    }

    // Trigger expanded view on tilt (landscape)
    androidx.compose.runtime.LaunchedEffect(isLandscape, sentence.isNotEmpty(), landscapeBigSentence) {
        if (isLandscape && sentence.isNotEmpty() && landscapeBigSentence) {
            isExpanded = true
        }
    }

    // Dismiss when returning to portrait
    androidx.compose.runtime.LaunchedEffect(isLandscape) {
        if (!isLandscape) {
            isExpanded = false
        }
    }

    // Dynamic Sizing: Shrink items if there are many.
    // Tablet: Start at 80dp, shrink to 60dp
    // Phone: Start at 48dp, shrink to 36dp
    val baseSize = if (isTablet) 80.dp else 48.dp
    val minSize = if (isTablet) 60.dp else 36.dp
    
    val itemSize = if (sentence.size > 6) {
        maxOf(minSize, baseSize - ((sentence.size - 6) * 3).dp)
    } else {
        baseSize
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main sentence row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .padding(4.dp)
                    .heightIn(min = 56.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Items Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp)
                        .animateContentSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (sentence.isEmpty()) {
                        Text(
                            text = "Build your sentence...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                        )
                    } else {
                        if (showSymbols) {
                            // Vertical layout for sentence buttons
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sentence) { button ->
                                    SentenceBarItem(
                                        button = button,
                                        size = itemSize
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = displayedText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Loading Indicator (New "different indicator")
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPredictionLoading,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut()
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Grammar Check Button (Magic Wand)
                    IconButton(
                        onClick = onGrammarCheck,
                        enabled = sentence.isNotEmpty(),
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Fix Grammar",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // Undo AI Button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showUndo,
                        enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                    ) {
                        IconButton(
                            onClick = onUndo,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Undo AI",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    
                    // Backspace Button with Long Press to Clear
                    val backspaceInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .then(
                                if (sentence.isNotEmpty()) {
                                    Modifier.combinedClickable(
                                        interactionSource = backspaceInteractionSource,
                                        indication = androidx.compose.material.ripple.rememberRipple(bounded = true, radius = 24.dp),
                                        onClick = onBackspace,
                                        onLongClick = onClear
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Backspace (Long press to clear)",
                            modifier = Modifier.size(18.dp),
                            tint = if (sentence.isNotEmpty()) MaterialTheme.colorScheme.onSurface else Color.LightGray
                        )
                    }

                    IconButton(
                        onClick = { onSpeak(displayedText) },
                        enabled = sentence.isNotEmpty(),
                        modifier = Modifier
                            .background(
                                color = if (sentence.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Predictions row (integrated within the same surface)
            androidx.compose.animation.AnimatedVisibility(
                visible = allowPredictionStrip && predictions.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(predictions) { word ->
                            PredictionChip(
                                word = word,
                                symbolUrl = if (showPredictionSymbols) predictionSymbols[word] else null,
                                onClick = { onPredictionClick(word) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SentenceBarItem(
    button: com.example.myaac.model.AacButton,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val baseColor = if (button.backgroundColor != 0xFFFFFFFF) Color(button.backgroundColor) else MaterialTheme.colorScheme.primaryContainer

    // Adjust font size based on item size
    // 60dp -> 10sp, 40dp -> 8sp
    val fontSize = if (size < 50.dp) 8.sp else 10.sp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(size) 
            .clip(RoundedCornerShape(8.dp))
            .background(baseColor)
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background Image
        if (!button.iconPath.isNullOrEmpty()) {
            coil.compose.AsyncImage(
                model = button.iconPath,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Text Overlay
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = button.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black,
                        offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                        blurRadius = 2f
                    )
                ),
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}

@Composable
private fun PredictionChip(
    word: String,
    symbolUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Symbol icon (if available and enabled)
            if (symbolUrl != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = symbolUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            // Word text
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
