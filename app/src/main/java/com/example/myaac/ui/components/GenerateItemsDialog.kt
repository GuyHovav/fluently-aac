package com.example.myaac.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myaac.R

@Composable
fun GenerateItemsDialog(
    onDismiss: () -> Unit,
    onGenerate: (Int) -> Unit
) {
    var itemCount by remember { mutableStateOf(20) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generate_items_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.generate_items_dialog_message))
                Spacer(modifier = Modifier.height(16.dp))
                Text("${stringResource(R.string.items_to_generate)}: $itemCount", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = itemCount.toFloat(),
                    onValueChange = { itemCount = it.toInt() },
                    valueRange = 1f..50f,
                    steps = 48
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1", style = MaterialTheme.typography.bodySmall)
                    Text("50", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onGenerate(itemCount)
            }) {
                Text(stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
