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

@Composable
fun SentenceBar(
    sentence: List<String>,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedText = sentence.joinToString(" ")

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
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { onSpeak(displayedText) }
                    .padding(horizontal = 16.dp)
                    .animateContentSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (sentence.isEmpty()) {
                    Text(
                        text = "Build your sentence...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
