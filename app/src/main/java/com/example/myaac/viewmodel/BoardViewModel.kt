package com.example.myaac.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myaac.MyAacApplication
import com.example.myaac.data.repository.BoardRepository
import com.example.myaac.model.AacButton
import com.example.myaac.model.Board
import com.example.myaac.model.ButtonAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.myaac.data.remote.ArasaacService
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.repository.SettingsRepository

data class BoardUiState(
    val currentBoard: Board? = null,
    val sentence: List<String> = emptyList(),
    val recommendedButtons: List<AacButton> = emptyList(),
    val isCaregiverMode: Boolean = false,
    val textScale: Float = 1.0f
)

class BoardViewModel(
    private val repository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    private val geminiService: GeminiService? = null,
    private val arasaacService: ArasaacService = ArasaacService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Observe settings changes
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(textScale = settings.textScale) }
            }
        }
        
        viewModelScope.launch {
            // Check availability of Default Boards
            val homeId = "home"
            val existing = repository.getBoard(homeId)
            if (existing == null) {
                initializePreloadedBoards()
            }
            // Load the default board
            val board = repository.getBoard(homeId)
            _uiState.update { it.copy(currentBoard = board) }
        }
    }

    private suspend fun initializePreloadedBoards() {
        val foodId = "board_food"
        val learnId = "board_learn"
        val feelId = "board_feelings"
        val homeId = "home"

        // 1. Food Board
        val foodButtons = listOf(
            createButton(foodId, 0, "I want", 0xFFE0E0E0, ButtonAction.Speak("I want")),
            createButton(foodId, 1, "Water", 0xFFBBDEFB, ButtonAction.Speak("Water")),
            createButton(foodId, 2, "Milk", 0xFFBBDEFB, ButtonAction.Speak("Milk")),
            createButton(foodId, 3, "Juice", 0xFFBBDEFB, ButtonAction.Speak("Juice")),
            createButton(foodId, 4, "Apple", 0xFFFFCDD2, ButtonAction.Speak("Apple")),
            createButton(foodId, 5, "Banana", 0xFFFFF9C4, ButtonAction.Speak("Banana")),
            createButton(foodId, 6, "Sandwich", 0xFFFFE0B2, ButtonAction.Speak("Sandwich")),
            createButton(foodId, 7, "Cookie", 0xFFFFE0B2, ButtonAction.Speak("Cookie")),
            createButton(foodId, 8, "Yes", 0xFFC8E6C9, ButtonAction.Speak("Yes")),
            createButton(foodId, 9, "No", 0xFFFFCDD2, ButtonAction.Speak("No")),
            createButton(foodId, 10, "More", 0xFFE1BEE7, ButtonAction.Speak("More")),
            createButton(foodId, 11, "All Done", 0xFFCFD8DC, ButtonAction.Speak("All Done")),
            // Navigation Back
            createButton(foodId, 15, "Back Home", 0xFFFFCC80, ButtonAction.LinkToBoard(homeId))
        ).toMutableList()
        // Fill gaps
        fillGaps(foodButtons, foodId)
        repository.saveBoard(Board(foodId, "Food & Drink", buttons = foodButtons))

        // 2. Feelings Board
        val feelButtons = listOf(
            createButton(feelId, 0, "I feel", 0xFFE0E0E0, ButtonAction.Speak("I feel")),
            createButton(feelId, 1, "Happy", 0xFFFFF9C4, ButtonAction.Speak("Happy")),
            createButton(feelId, 2, "Sad", 0xFFE1BEE7, ButtonAction.Speak("Sad")),
            createButton(feelId, 3, "Mad", 0xFFFFCDD2, ButtonAction.Speak("Mad")),
            createButton(feelId, 4, "Tired", 0xFFBBDEFB, ButtonAction.Speak("Tired")),
            createButton(feelId, 5, "Scared", 0xFFCFD8DC, ButtonAction.Speak("Scared")),
            createButton(feelId, 6, "Excited", 0xFFC8E6C9, ButtonAction.Speak("Excited")),
            createButton(feelId, 7, "Sick", 0xFFB2DFDB, ButtonAction.Speak("Sick")),
            createButton(feelId, 15, "Back Home", 0xFFFFCC80, ButtonAction.LinkToBoard(homeId))
        ).toMutableList()
        fillGaps(feelButtons, feelId)
        repository.saveBoard(Board(feelId, "Feelings", buttons = feelButtons))

        // 3. Learn/School Board
        val learnButtons = listOf(
             createButton(learnId, 0, "I see", 0xFFE0E0E0, ButtonAction.Speak("I see")),
             createButton(learnId, 1, "Book", 0xFFFFF9C4, ButtonAction.Speak("Book")),
             createButton(learnId, 2, "Crayon", 0xFFFF8A65, ButtonAction.Speak("Crayon")),
             createButton(learnId, 3, "Tablet", 0xFFBBDEFB, ButtonAction.Speak("Tablet")),
             createButton(learnId, 15, "Back Home", 0xFFFFCC80, ButtonAction.LinkToBoard(homeId))
        ).toMutableList()
        fillGaps(learnButtons, learnId)
        repository.saveBoard(Board(learnId, "Learn", buttons = learnButtons))

        // 4. Home Board (Hub)
        val homeButtons = mutableListOf<AacButton>()
        // Top Row: Core words
        homeButtons.add(createButton(homeId, 0, "I want", 0xFFE0E0E0, ButtonAction.Speak("I want")))
        homeButtons.add(createButton(homeId, 1, "Stop", 0xFFFFCDD2, ButtonAction.Speak("Stop")))
        homeButtons.add(createButton(homeId, 2, "Yes", 0xFFC8E6C9, ButtonAction.Speak("Yes")))
        homeButtons.add(createButton(homeId, 3, "No", 0xFFFFCDD2, ButtonAction.Speak("No")))
        
        // Folder Links (Middle)
        homeButtons.add(createButton(homeId, 4, "Food", 0xFFFFE0B2, ButtonAction.LinkToBoard(foodId)))
        homeButtons.add(createButton(homeId, 5, "Feelings", 0xFFE1BEE7, ButtonAction.LinkToBoard(feelId)))
        homeButtons.add(createButton(homeId, 6, "Learn", 0xFFBBDEFB, ButtonAction.LinkToBoard(learnId)))
        
        // Common Actions
        homeButtons.add(createButton(homeId, 8, "Help", 0xFFB2DFDB, ButtonAction.Speak("Help me please")))
        homeButtons.add(createButton(homeId, 9, "Bathroom", 0xFFCFD8DC, ButtonAction.Speak("I need to use the bathroom")))
        homeButtons.add(createButton(homeId, 10, "Play", 0xFFC5CAE9, ButtonAction.Speak("I want to play")))

        fillGaps(homeButtons, homeId)
        
        repository.saveBoard(Board(homeId, "Home Board", buttons = homeButtons))
    }

    private fun createButton(boardId: String, index: Int, label: String, color: Long, action: ButtonAction): AacButton {
        return AacButton(
            id = "${boardId}_btn_$index",
            label = label,
            speechText = null,
            backgroundColor = color,
            action = action
        )
    }
    
    // Fill empty slots up to 16
    private fun fillGaps(buttons: MutableList<AacButton>, boardId: String) {
        for (i in 0..15) {
            if (buttons.none { it.id.endsWith("_btn_$i") }) {
                buttons.add(createButton(boardId, i, "", 0xFFFFFFFF, ButtonAction.Speak("")))
            }
        }
        buttons.sortBy { 
            try {
                it.id.substringAfterLast("_btn_").toInt()
            } catch (e: Exception) { 0 }
        }
    }

    fun createNewBoard(name: String) {
        viewModelScope.launch {
            val newId = java.util.UUID.randomUUID().toString()
            val buttons = List(16) { index ->
                createButton(newId, index, "", 0xFFFFFFFF, ButtonAction.Speak(""))
            }
            val newBoard = Board(id = newId, name = name, buttons = buttons)
            repository.saveBoard(newBoard)
            _uiState.update { it.copy(currentBoard = newBoard) }
        }
    }
    
    fun navigateToBoard(boardId: String) {
        viewModelScope.launch {
            val board = repository.getBoard(boardId)
            if (board != null) {
                _uiState.update { it.copy(currentBoard = board) }
            }
        }
    }

    fun createMagicBoard(name: String, topic: String) {
        if (geminiService == null) return
        viewModelScope.launch {
            val words = geminiService.generateBoard(topic)
            val newId = java.util.UUID.randomUUID().toString()
            
            // Map words to buttons with symbols
            val buttons = words.mapIndexed { index, word ->
                var iconPath: String? = null
                try {
                     val searchResults = arasaacService.searchPictograms(word)
                     if (searchResults.isNotEmpty()) {
                         iconPath = arasaacService.getImageUrl(searchResults[0]._id)
                     }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                createButton(newId, index, word, 0xFFFFF9C4, ButtonAction.Speak(word))
                    .copy(iconPath = iconPath)
            }.toMutableList()

            // Fill remaining slots
            val currentSize = buttons.size
            for (i in currentSize..15) {
               buttons.add(createButton(newId, i, "", 0xFFFFFFFF, ButtonAction.Speak("")))
            }
            
            val newBoard = Board(id = newId, name = name, buttons = buttons)
            repository.saveBoard(newBoard)
            _uiState.update { it.copy(currentBoard = newBoard) }
        }
    }

    fun deleteBoard(boardId: String) {
        viewModelScope.launch {
            repository.deleteBoard(boardId)
            // If the deleted board was the current one, go home
            if (_uiState.value.currentBoard?.id == boardId) {
                navigateToBoard("home")
            }
        }
    }

    suspend fun checkSymbol(query: String): String? {
        return try {
            val searchResults = arasaacService.searchPictograms(query)
            if (searchResults.isNotEmpty()) {
                arasaacService.getImageUrl(searchResults[0]._id)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun unlockCaregiverMode(pin: String): Boolean {
        // Debug mode: auto-unlock without PIN check (for development/testing)
        if (com.example.myaac.BuildConfig.DEBUG_MODE) {
            _uiState.update { it.copy(isCaregiverMode = true) }
            android.util.Log.d("BoardViewModel", "Debug mode: Auto-unlocked caregiver mode")
            return true
        }
        
        // Production mode: require correct PIN
        return if (pin == "1234") {
            _uiState.update { it.copy(isCaregiverMode = true) }
            true
        } else {
            false
        }
    }

    fun lockCaregiverMode() {
        _uiState.update { it.copy(isCaregiverMode = false) }
    }

    // Explicitly defining list of boards is not strictly cached here, usually we get from repo in UI. 
    // If needed for ViewModel logic:
    val allBoards = repository.allBoards

    fun onButtonPress(button: AacButton) {
        when (val action = button.action) {
            is ButtonAction.Speak -> {
                addToSentence(action.text)
                // Trigger prediction
                updatePredictions()
            }
            is ButtonAction.LinkToBoard -> {
                navigateToBoard(action.boardId)
            }
            ButtonAction.ClearSentence -> {
                clearSentence()
            }
            ButtonAction.DeleteLastWord -> {
                if (_uiState.value.sentence.isNotEmpty()) {
                    _uiState.update { it.copy(sentence = it.sentence.dropLast(1)) }
                }
            }
        }
    }

    private fun addToSentence(text: String) {
        _uiState.update {
            it.copy(sentence = it.sentence + text)
        }
    }

    private fun updatePredictions() {
        if (geminiService == null) return
        viewModelScope.launch {
            val currentSentence = _uiState.value.sentence.joinToString(" ")
            val allLabels = repository.getAllButtonLabels()
            val predictedLabels = geminiService.predictNextButtons(currentSentence, allLabels)
            val predictedButtons = predictedLabels.mapNotNull { label ->
                repository.findButtonByLabel(label) 
            }
            _uiState.update { it.copy(recommendedButtons = predictedButtons) }
        }
    }

    fun clearSentence() {
        _uiState.update {
            it.copy(sentence = emptyList(), recommendedButtons = emptyList())
        }
    }

    fun updateButton(button: AacButton) {
        val current = _uiState.value.currentBoard ?: return
        val updatedList = current.buttons.map { if (it.id == button.id) button else it }
        val updatedBoard = current.copy(buttons = updatedList)
        viewModelScope.launch {
            repository.saveBoard(updatedBoard)
            // No strict need to update UI state manually if using Flow from Room, but currentBoard in state might be static
            _uiState.update { it.copy(currentBoard = updatedBoard) } 
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyAacApplication)
                BoardViewModel(application.repository, application.settingsRepository, application.geminiService)
            }
        }
    }
}
