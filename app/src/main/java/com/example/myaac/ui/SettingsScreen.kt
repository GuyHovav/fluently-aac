package com.example.myaac.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myaac.R
import com.example.myaac.data.repository.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onLanguageChange: (String) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onTtsRateChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription="Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Language Selection
            SettingSection(title = stringResource(R.string.language)) {
                LanguageSelector(
                    selectedLanguage = settings.languageCode,
                    onLanguageSelected = onLanguageChange
                )
            }
            
            Divider()
            
            // Text Size
            SettingSection(title = stringResource(R.string.text_size)) {
                SliderWithLabels(
                    value = settings.textScale,
                    onValueChange = onTextScaleChange,
                    valueRange = 0.8f..1.5f,
                    steps = 6,
                    startLabel = stringResource(R.string.small),
                    endLabel = stringResource(R.string.large)
                )
            }
            
            Divider()
            
            // TTS Speed
            SettingSection(title = stringResource(R.string.tts_speed)) {
                SliderWithLabels(
                    value = settings.ttsRate,
                    onValueChange = onTtsRateChange,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    startLabel = stringResource(R.string.slow),
                    endLabel = stringResource(R.string.fast)
                )
            }
        }
    }
}

@Composable
fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilterChip(
            selected = selectedLanguage == "en",
            onClick = { onLanguageSelected("en") },
            label = { Text(stringResource(R.string.english)) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = selectedLanguage == "iw",
            onClick = { onLanguageSelected("iw") },
            label = { Text(stringResource(R.string.hebrew)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SliderWithLabels(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    startLabel: String,
    endLabel: String
) {
    Column {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = startLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = endLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
