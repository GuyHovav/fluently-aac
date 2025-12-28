package com.example.myaac.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myaac.model.AacButton
import com.example.myaac.model.Board

@Composable
fun VisualSceneGrid(
    board: Board,
    isCaregiverMode: Boolean,
    onButtonClick: (AacButton) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight

        // 1. Background Image
        if (!board.backgroundImagePath.isNullOrEmpty()) {
            AsyncImage(
                model = board.backgroundImagePath,
                contentDescription = "Visual Scene Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // 2. Hotspots
        board.buttons.forEach { button ->
            val box = button.boundingBox
            if (box != null && box.size == 4) {
                // box is [ymin, xmin, ymax, xmax] as percentages (0-1)
                val top = box[0] * height
                val left = box[1] * width
                val bottom = box[2] * height
                val right = box[3] * width

                val boxWidth = (right - left).coerceAtLeast(1f)
                val boxHeight = (bottom - top).coerceAtLeast(1f)

                // Render the interactive area
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(androidx.compose.ui.platform.LocalDensity.current) { left.toDp() },
                            y = with(androidx.compose.ui.platform.LocalDensity.current) { top.toDp() }
                        )
                        .size(
                            width = with(androidx.compose.ui.platform.LocalDensity.current) { boxWidth.toDp() },
                            height = with(androidx.compose.ui.platform.LocalDensity.current) { boxHeight.toDp() }
                        )
                        .then(
                            if (isCaregiverMode) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.5f))
                                        .background(Color.White.copy(alpha = 0.2f))
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onButtonClick(button) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCaregiverMode || button.label.isNotEmpty()) {
                        Text(
                            text = button.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
