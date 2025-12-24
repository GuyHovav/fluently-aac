package com.example.myaac.model

import androidx.compose.ui.graphics.Color

sealed interface ButtonAction {
    data class Speak(val text: String) : ButtonAction
    data class LinkToBoard(val boardId: String) : ButtonAction
    data object ClearSentence : ButtonAction // Example function
    data object DeleteLastWord : ButtonAction
}

data class AacButton(
    val id: String,
    val label: String,
    val speechText: String?, // If null, uses label
    val iconPath: String? = null, // Path to local asset or URL
    val backgroundColor: Long = 0xFFFFFFFF, // Storing as Long for easy serialization, or could use Color
    val action: ButtonAction = ButtonAction.Speak(label),
    val hidden: Boolean = false
) {
    // Helper to get actual text to speak
    val textToSpeak: String
        get() = speechText ?: label
}
