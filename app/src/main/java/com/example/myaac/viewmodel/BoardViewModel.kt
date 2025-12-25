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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.example.myaac.data.remote.ArasaacService
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.repository.SettingsRepository

data class BoardUiState(
    val currentBoard: Board? = null,
    val sentence: List<AacButton> = emptyList(),
    // Predictions removed
    val isCaregiverMode: Boolean = false,
    val textScale: Float = 1.0f,
    val isLoading: Boolean = false
)

class BoardViewModel(
    private val application: android.app.Application,
    private val repository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    private val geminiService: GeminiService? = null,
    private val arasaacService: ArasaacService = ArasaacService()
) : androidx.lifecycle.AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    // Helper to get string in selected language
    private fun getString(resId: Int): String {
        val langCode = settingsRepository.settings.value.languageCode
        val locale = if (langCode == "iw") java.util.Locale("iw") else java.util.Locale.ENGLISH
        val config = android.content.res.Configuration(application.resources.configuration)
        config.setLocale(locale)
        return application.createConfigurationContext(config).getString(resId)
    }

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

    private suspend fun initializePreloadedBoards() = coroutineScope {
        val foodId = "board_food"
        val learnId = "board_learn"
        val feelId = "board_feelings"
        val homeId = "home"
        
        val currentLang = settingsRepository.settings.value.languageCode
        val localeStr = if (currentLang == "iw") "he" else "en"

        // 1. Food Board
        val foodDeferred = listOf(
            async { createButtonWithSymbol(foodId, 0, getString(com.example.myaac.R.string.btn_i_want), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_want)), "want", localeStr) },
            async { createButtonWithSymbol(foodId, 1, getString(com.example.myaac.R.string.btn_water), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_water)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 2, getString(com.example.myaac.R.string.btn_milk), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_milk)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 3, getString(com.example.myaac.R.string.btn_juice), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_juice)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 4, getString(com.example.myaac.R.string.btn_apple), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_apple)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 5, getString(com.example.myaac.R.string.btn_banana), 0xFFFFF9C4, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_banana)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 6, getString(com.example.myaac.R.string.btn_sandwich), 0xFFFFE0B2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_sandwich)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 7, getString(com.example.myaac.R.string.btn_cookie), 0xFFFFE0B2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_cookie)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 8, getString(com.example.myaac.R.string.btn_yes), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_yes)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 9, getString(com.example.myaac.R.string.btn_no), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_no)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 10, getString(com.example.myaac.R.string.btn_more), 0xFFE1BEE7, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_more)), null, localeStr) },
            async { createButtonWithSymbol(foodId, 11, getString(com.example.myaac.R.string.btn_all_done), 0xFFCFD8DC, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_all_done)), "finished", localeStr) },
            async { createButtonWithSymbol(foodId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
        )
        val foodButtons = foodDeferred.awaitAll().toMutableList()
        fillGaps(foodButtons, foodId)
        repository.saveBoard(Board(foodId, getString(com.example.myaac.R.string.board_food_name), buttons = foodButtons, iconPath = "https://static.arasaac.org/pictograms/4610/4610_300.png"))

        // 2. Feelings Board
        val feelDeferred = listOf(
            async { createButtonWithSymbol(feelId, 0, getString(com.example.myaac.R.string.btn_i_feel), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_feel)), "feeling", localeStr) },
            async { createButtonWithSymbol(feelId, 1, getString(com.example.myaac.R.string.btn_happy), 0xFFFFF9C4, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_happy)), null, localeStr) },
            async { createButtonWithSymbol(feelId, 2, getString(com.example.myaac.R.string.btn_sad), 0xFFE1BEE7, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_sad)), null, localeStr) },
            async { createButtonWithSymbol(feelId, 3, getString(com.example.myaac.R.string.btn_mad), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_mad)), "angry", localeStr) },
            async { createButtonWithSymbol(feelId, 4, getString(com.example.myaac.R.string.btn_tired), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_tired)), null, localeStr) },
            async { createButtonWithSymbol(feelId, 5, getString(com.example.myaac.R.string.btn_scared), 0xFFCFD8DC, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_scared)), null, localeStr) },
            async { createButtonWithSymbol(feelId, 6, getString(com.example.myaac.R.string.btn_excited), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_excited)), null, localeStr) },
            async { createButtonWithSymbol(feelId, 7, getString(com.example.myaac.R.string.btn_sick), 0xFFB2DFDB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_sick)), null, localeStr) },
            async { createButtonWithSymbol(feelId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
        )
        val feelButtons = feelDeferred.awaitAll().toMutableList()
        fillGaps(feelButtons, feelId)
        repository.saveBoard(Board(feelId, getString(com.example.myaac.R.string.board_feelings_name), buttons = feelButtons, iconPath = "https://static.arasaac.org/pictograms/37190/37190_300.png"))

        // 3. Learn/School Board
        val learnDeferred = listOf(
             async { createButtonWithSymbol(learnId, 0, getString(com.example.myaac.R.string.btn_i_see), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_see)), "see", localeStr) },
             async { createButtonWithSymbol(learnId, 1, getString(com.example.myaac.R.string.btn_book), 0xFFFFF9C4, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_book)), null, localeStr) },
             async { createButtonWithSymbol(learnId, 2, getString(com.example.myaac.R.string.btn_crayon), 0xFFFF8A65, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_crayon)), null, localeStr) },
             async { createButtonWithSymbol(learnId, 3, getString(com.example.myaac.R.string.btn_tablet), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_tablet)), null, localeStr) },
             async { createButtonWithSymbol(learnId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
        )
        val learnButtons = learnDeferred.awaitAll().toMutableList()
        fillGaps(learnButtons, learnId)
        repository.saveBoard(Board(learnId, getString(com.example.myaac.R.string.board_learn_name), buttons = learnButtons, iconPath = "https://static.arasaac.org/pictograms/32446/32446_300.png"))

        // 4. Home Board (Hub)
        val homeDeferred = listOf(
            async { createButtonWithSymbol(homeId, 0, getString(com.example.myaac.R.string.btn_i_want), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_want)), "want", localeStr) },
            async { createButtonWithSymbol(homeId, 1, getString(com.example.myaac.R.string.btn_stop), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_stop)), null, localeStr) },
            async { createButtonWithSymbol(homeId, 2, getString(com.example.myaac.R.string.btn_yes), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_yes)), null, localeStr) },
            async { createButtonWithSymbol(homeId, 3, getString(com.example.myaac.R.string.btn_no), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_no)), null, localeStr) },
            
            // Folder Links
            async { createButtonWithSymbol(homeId, 4, getString(com.example.myaac.R.string.board_food_name), 0xFFFFE0B2, ButtonAction.LinkToBoard(foodId), null, localeStr) },
            async { createButtonWithSymbol(homeId, 5, getString(com.example.myaac.R.string.board_feelings_name), 0xFFE1BEE7, ButtonAction.LinkToBoard(feelId), null, localeStr) },
            async { createButtonWithSymbol(homeId, 6, getString(com.example.myaac.R.string.board_learn_name), 0xFFBBDEFB, ButtonAction.LinkToBoard(learnId), null, localeStr) },
            
            // Common Actions
            async { createButtonWithSymbol(homeId, 8, getString(com.example.myaac.R.string.btn_help), 0xFFB2DFDB, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_help)), "help", localeStr) },
            async { createButtonWithSymbol(homeId, 9, getString(com.example.myaac.R.string.btn_bathroom), 0xFFCFD8DC, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_bathroom)), "toilet", localeStr) },
            async { createButtonWithSymbol(homeId, 10, getString(com.example.myaac.R.string.btn_play), 0xFFC5CAE9, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_play)), "play", localeStr) }
        )
        val homeButtons = homeDeferred.awaitAll().toMutableList()
        fillGaps(homeButtons, homeId)
        
        repository.saveBoard(Board(homeId, getString(com.example.myaac.R.string.board_home_name), buttons = homeButtons, iconPath = "https://static.arasaac.org/pictograms/6964/6964_300.png"))
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

    private suspend fun createButtonWithSymbol(
        boardId: String, 
        index: Int, 
        label: String, 
        color: Long, 
        action: ButtonAction, 
        searchTerm: String? = null,
        locale: String = "en"
    ): AacButton {
        val query = searchTerm ?: label
        var iconPath: String? = null
        if (query.isNotEmpty()) {
             try {
                val searchResults = arasaacService.searchPictograms(query, locale)
                if (searchResults.isNotEmpty()) {
                    iconPath = arasaacService.getImageUrl(searchResults[0]._id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
       
        return createButton(boardId, index, label, color, action).copy(iconPath = iconPath)
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
            _uiState.update { it.copy(isLoading = true) }
            try {
                val newId = java.util.UUID.randomUUID().toString()
                val buttons = List(16) { index ->
                    createButton(newId, index, "", 0xFFFFFFFF, ButtonAction.Speak(""))
                }
                
                // Try to find an icon for the board name
                var iconPath: String? = null
                try {
                    val searchResults = arasaacService.searchPictograms(name, java.util.Locale.getDefault().language)
                    if (searchResults.isNotEmpty()) {
                        iconPath = arasaacService.getImageUrl(searchResults[0]._id)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val newBoard = Board(id = newId, name = name, buttons = buttons, iconPath = iconPath)
                repository.saveBoard(newBoard)
                _uiState.update { it.copy(currentBoard = newBoard) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
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
        val service = geminiService ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val langCode = settingsRepository.settings.value.languageCode
                val words = service.generateBoard(topic, langCode)
                val newId = java.util.UUID.randomUUID().toString()
                
                // Map words to buttons with symbols using async fetches
                val arasaacLocale = if (langCode == "iw") "he" else "en"
                val deferredButtons = words.mapIndexed { index, word ->
                    async {
                        createButtonWithSymbol(
                            boardId = newId, 
                            index = index, 
                            label = word, 
                            color = 0xFFFFF9C4, 
                            action = ButtonAction.Speak(word),
                            searchTerm = word,
                            locale = arasaacLocale
                        )
                    }
                }
                
                val buttons = deferredButtons.awaitAll().toMutableList()

                // Fill remaining slots
                val currentSize = buttons.size
                for (i in currentSize..15) {
                   buttons.add(createButton(newId, i, "", 0xFFFFFFFF, ButtonAction.Speak("")))
                }
                
                // Try to find an icon for the topic
                var boardIconPath: String? = null
                try {
                    val searchResults = arasaacService.searchPictograms(topic, arasaacLocale)
                    if (searchResults.isNotEmpty()) {
                        boardIconPath = arasaacService.getImageUrl(searchResults[0]._id)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                val newBoard = Board(id = newId, name = name, buttons = buttons, iconPath = boardIconPath)
                repository.saveBoard(newBoard)
                _uiState.update { it.copy(currentBoard = newBoard) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteBoard(boardId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteBoard(boardId)
                // If the deleted board was the current one, go home
                if (_uiState.value.currentBoard?.id == boardId) {
                    navigateToBoard("home")
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateBoard(board: Board) {
        viewModelScope.launch {
            repository.saveBoard(board)
            if (_uiState.value.currentBoard?.id == board.id) {
                _uiState.update { it.copy(currentBoard = board) }
            }
        }
    }

    suspend fun checkSymbol(query: String): String? {
        return try {
            val searchResults = arasaacService.searchPictograms(query, java.util.Locale.getDefault().language)
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

    suspend fun simplifyLocationName(rawName: String): String {
        return geminiService?.simplifyLocationName(rawName, settingsRepository.settings.value.languageCode) ?: rawName
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

    fun doesBoardExist(name: String): Boolean {
        // This is a bit of a hack since allBoards is a Flow, checking current cache is better via UI or collecting here
        // For simplicity, we can assume the UI will check against the list it observes, OR
        // we can check if any board in the current state's cache matches (if we tracked it)
        // Better: Synchronous check for "does board with name exist" might need a suspend function in repo
        // For now, let's rely on the UI passing this check or adding a quick check here if possible.
        // Actually, let's use the allBoards flow value if collected (it's not collected here).
        // Let's defer this check to the UI which has the list.
        return false 
    }

    fun onButtonPress(button: AacButton) {
        when (val action = button.action) {
            is ButtonAction.Speak -> {
                addToSentence(button)
                // Predictions removed
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

    private fun addToSentence(button: AacButton) {
        _uiState.update {
            it.copy(sentence = it.sentence + button)
        }
    }

    // Predictions removed
    // private fun updatePredictions() { ... }

    fun clearSentence() {
        _uiState.update {
            it.copy(sentence = emptyList())
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

    fun deleteButton(buttonId: String) {
        val current = _uiState.value.currentBoard ?: return
        // Filter out the button with the given ID
        val updatedList = current.buttons.filterNot { it.id == buttonId }
        val updatedBoard = current.copy(buttons = updatedList)
        viewModelScope.launch {
            repository.saveBoard(updatedBoard)
            _uiState.update { it.copy(currentBoard = updatedBoard) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyAacApplication)
                BoardViewModel(application, application.repository, application.settingsRepository, application.geminiService)
            }
        }
    }
}
