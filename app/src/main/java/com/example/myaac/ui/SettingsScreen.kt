package com.example.myaac.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
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
import com.example.myaac.data.model.DisabilityType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onLanguageChange: (String) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onTtsRateChange: (Float) -> Unit,
    onLocationSuggestionsChange: (Boolean) -> Unit,
    onDisabilityTypeChange: (DisabilityType) -> Unit,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // User Profile
            SettingSection(title = stringResource(R.string.user_profile)) {
                DisabilitySelector(
                    selected = settings.disabilityType,
                    onSelect = onDisabilityTypeChange
                )
            }
            
            Divider()

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


            Divider()

            // Location Suggestions
            SettingSection(title = stringResource(R.string.location_suggestions)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.enable_location_suggestions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )
                    Switch(
                        checked = settings.locationSuggestionsEnabled,
                        onCheckedChange = onLocationSuggestionsChange
                    )
                }
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

@Composable
fun DisabilitySelector(
    selected: DisabilityType,
    onSelect: (DisabilityType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf(
            DisabilityType.NONE to R.string.disability_none,
            DisabilityType.MOTOR_IMPAIRMENT to R.string.disability_motor,
            DisabilityType.VISUAL_IMPAIRMENT to R.string.disability_visual,
            DisabilityType.COGNITIVE_IMPAIRMENT to R.string.disability_cognitive,
            DisabilityType.APHASIA to R.string.disability_aphasia
        )

        options.forEach { (type, stringRes) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(type) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = (selected == type),
                    onClick = { onSelect(type) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(stringRes),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
