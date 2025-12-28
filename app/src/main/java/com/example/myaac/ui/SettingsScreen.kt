package com.example.myaac.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
    onHorizontalNavigationChange: (Boolean) -> Unit,
    onDisabilityTypeChange: (DisabilityType) -> Unit,
    onShowSymbolsInSentenceChange: (Boolean) -> Unit,
    onItemsToGenerateChange: (Int) -> Unit,
    onMaxBoardCapacityChange: (Int) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onManagePronunciations: () -> Unit = {},
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
            // General Section
            SettingSection(title = "General") {
                /*DisabilitySelector(
                    selected = settings.disabilityType,
                    onSelect = onDisabilityTypeChange
                )*/
                
                Divider()
                
                LanguageSelector(
                    selectedLanguage = settings.languageCode,
                    onLanguageSelected = onLanguageChange
                )
            }
            
            Divider()
            
            // Appearance Section
            SettingSection(title = "Appearance") {
                // Text Size
                Text(
                    text = stringResource(R.string.text_size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SliderWithLabels(
                    value = settings.textScale,
                    onValueChange = onTextScaleChange,
                    valueRange = 0.8f..1.5f,
                    steps = 6,
                    startLabel = stringResource(R.string.small),
                    endLabel = stringResource(R.string.large)
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Font Family
                FontSelector(
                    selectedFont = settings.fontFamily,
                    onFontSelected = onFontFamilyChange
                )
            }
            
            Divider()
            
            // Speech Section
            SettingSection(title = "Speech") {
                // TTS Speed
                Text(
                    text = stringResource(R.string.tts_speed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SliderWithLabels(
                    value = settings.ttsRate,
                    onValueChange = onTtsRateChange,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    startLabel = stringResource(R.string.slow),
                    endLabel = stringResource(R.string.fast)
                )

                // Pronunciation Management
                if (settings.languageCode == "iw" || settings.languageCode == "he") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManagePronunciations() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manage Custom Pronunciations",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Add nikud to fix mispronounced words",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Manage",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Divider()

            // Navigation & Interaction Section
            SettingSection(title = "Navigation & Interaction") {
                // Location Suggestions
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
                
                // Horizontal Board Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.enable_horizontal_board_navigation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )
                    Switch(
                        checked = settings.showHorizontalNavigation,
                        onCheckedChange = onHorizontalNavigationChange
                    )
                }
                
                // Sentence Field Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.show_symbols_in_sentence_bar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )
                    Switch(
                        checked = settings.showSymbolsInSentenceBar,
                        onCheckedChange = onShowSymbolsInSentenceChange
                    )
                }
            }
            
            Divider()

            // Content Generation Section
            SettingSection(title = "Content Generation") {
                // Items to Auto-Generate
                Column {
                    Text(
                        text = "${stringResource(R.string.items_to_generate_description)}: ${settings.itemsToGenerate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.itemsToGenerate.toFloat(),
                        onValueChange = { onItemsToGenerateChange(it.toInt()) },
                        valueRange = 1f..50f,
                        steps = 48,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "50",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Max Board Capacity
                Column {
                    Text(
                        text = "${stringResource(R.string.max_board_capacity_description)}: ${settings.maxBoardCapacity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = settings.maxBoardCapacity.toFloat(),
                        onValueChange = { onMaxBoardCapacityChange(it.toInt()) },
                        valueRange = 50f..1000f,
                        steps = 94,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "50",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "1000",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

@Composable
fun FontSelector(
    selectedFont: String,
    onFontSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val fonts = listOf("System", "OpenDyslexic", "Atkinson Hyperlegible", "Andika", "Serif", "SansSerif", "Monospace", "Cursive")

    Column {
        Text(
            text = "Font Family",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = selectedFont)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                fonts.forEach { font ->
                    DropdownMenuItem(
                        text = { Text(font) },
                        onClick = {
                            onFontSelected(font)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
