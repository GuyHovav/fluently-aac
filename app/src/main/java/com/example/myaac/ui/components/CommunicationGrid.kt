package com.example.myaac.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myaac.model.AacButton
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun CommunicationGrid(
    buttons: List<AacButton>,
    columns: Int,
    onButtonClick: (AacButton) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(buttons) { button ->
            AacButtonView(
                button = button,
                onClick = { onButtonClick(button) }
            )
        }
    }
}

@Composable
fun AacButtonView(
    button: AacButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "buttonScale")

    // TODO: Use better color logic, maybe Fitzgerald Key
    val baseColor = if (button.backgroundColor != 0xFFFFFFFF) Color(button.backgroundColor) else MaterialTheme.colorScheme.primaryContainer
    
    // High-end look: Gradient or glossy effect could be compliant here, but sticking to flat+shadow for readability
    Box(
        modifier = modifier
            .aspectRatio(1f) // Square buttons
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(baseColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                 if (button.iconPath != null) {
                     AsyncImage(
                         model = button.iconPath,
                         contentDescription = null,
                         contentScale = ContentScale.Crop,
                         modifier = Modifier.fillMaxSize()
                     )
                 } else {
                    // Placeholder Text for Icon
                     Text(
                         text = button.label.firstOrNull()?.toString() ?: "",
                         fontSize = 40.sp,
                         fontWeight = FontWeight.Bold,
                         color = Color.Black.copy(alpha = 0.2f)
                     )
                 }
            }
            
            Text(
                text = button.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = if (baseColor.luminance() > 0.5f) Color.Black else Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
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
