package com.example.myaac.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myaac.model.AacButton

@Composable
fun SmartStrip(
    predictions: List<AacButton>,
    onButtonClick: (AacButton) -> Unit
) {
    if (predictions.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp), // Adjust height as needed
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(predictions) { button ->
            // Re-use AacButtonView but maybe slightly smaller or highlighted
            // Use a temporary simplified version to fit the strip
            AacButtonView(
                button = button,
                onClick = { onButtonClick(button) },
                modifier = Modifier.height(64.dp) // Fixed height for strip items
            )
        }
    }
}
