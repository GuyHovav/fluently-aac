package com.example.myaac.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items

@Composable
fun SentenceBar(
    sentence: List<com.example.myaac.model.AacButton>,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedText = sentence.joinToString(" ") { it.textToSpeak }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll to the end when sentence changes
    androidx.compose.runtime.LaunchedEffect(sentence.size) {
        if (sentence.isNotEmpty()) {
            listState.animateScrollToItem(sentence.size - 1)
        }
    }

    // Dynamic Sizing: Shrink items if there are many.
    // If > 6 items, scale down from 60.dp to 40.dp based on count, capping at 40.dp min.
    val itemSize = if (sentence.size > 6) {
        maxOf(40.dp, 60.dp - ((sentence.size - 6) * 4).dp)
    } else {
        60.dp
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(80.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Items Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp)
                    .animateContentSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (sentence.isEmpty()) {
                    Text(
                        text = "Build your sentence...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(sentence.size) { index ->
                            SentenceBarItem(
                                button = sentence[index], 
                                size = itemSize
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBackspace,
                    enabled = sentence.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = if (sentence.isNotEmpty()) MaterialTheme.colorScheme.onSurface else Color.LightGray
                    )
                }
                
                 IconButton(
                    onClick = { onSpeak(displayedText) },
                     enabled = sentence.isNotEmpty(),
                     modifier = Modifier
                         .background(
                             color = if (sentence.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.LightGray,
                             shape = RoundedCornerShape(12.dp)
                         )
                         .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Speak",
                        tint = Color.White
                    )
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
            .size(size) 
            .clip(RoundedCornerShape(8.dp))
            .background(baseColor)
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
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
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}
