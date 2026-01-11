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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.example.myaac.data.remote.ArasaacService
import com.example.myaac.data.remote.GlobalSymbolsService
import com.example.myaac.data.remote.CompositeSymbolService
import com.example.myaac.data.remote.SymbolService
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.myaac.util.GrammarEngine

data class BoardUiState(
    val currentBoard: Board? = null,
    val sentence: List<AacButton> = emptyList(),
    val isCaregiverMode: Boolean = false,
    val textScale: Float = 1.0f,
    val isLoading: Boolean = false,
    val selectedButtonIds: Set<String> = emptySet(),
    // Smart Grammar State
    val originalSentence: List<AacButton>? = null,
    val showUndoAi: Boolean = false,
    val isGrammarLoading: Boolean = false,
    val pendingGrammarCorrection: String? = null,
    val hasPendingCorrection: Boolean = false,
    // Prediction State
    val predictions: List<String> = emptyList(),
    val predictionSymbols: Map<String, String?> = emptyMap(), // word -> symbol URL
    val isPredictionLoading: Boolean = false,
    // Agent State
    val agentResponse: String? = null,
    val isAgentProcessing: Boolean = false
)

class BoardViewModel(
    private val application: android.app.Application,
    private val repository: BoardRepository,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: com.example.myaac.data.repository.CloudRepository? = null,
    private val geminiService: GeminiService? = null
) : androidx.lifecycle.AndroidViewModel(application) {

    // Services for symbol search
    private val arasaacService = ArasaacService()
    private val mulberryService = GlobalSymbolsService("mulberry")
    private val arasaacGlobalService = GlobalSymbolsService("arasaac")
    private val googleImageService = com.example.myaac.data.remote.GoogleImageService()
    
    private suspend fun searchSymbols(query: String): String? {
        val langCode = settingsRepository.settings.value.languageCode
        val locale = if (langCode == "iw") "he" else "en"
        val lib = settingsRepository.settings.value.symbolLibrary
        
        val services = if (lib == "MULBERRY") {
            listOf(mulberryService, arasaacService, googleImageService)
        } else {
            // Priority:
            // 1. Arasaac Direct API (Standard)
            // 2. Arasaac via Global Symbols (Backup for different indexing)
            // 3. Mulberry (Last resort fallback to ensure content)
            // 4. Google Image Search (Web fallback for proper nouns, locations, etc.)
            listOf(arasaacService, arasaacGlobalService, mulberryService, googleImageService)
        }
        
        val compositeService = CompositeSymbolService(services)
        
        return try {
            val results = compositeService.search(query, locale)
            results.firstOrNull()?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    // SharedFlow for one-time events like Speech
    private val _speechRequest = MutableSharedFlow<String>()
    val speechRequest: SharedFlow<String> = _speechRequest.asSharedFlow()

    
    // Cache for boards to access synchronously
    private var cachedBoards: List<Board> = emptyList()
    
    init {
        // Collect boards to keep cache updated and refresh home board
        viewModelScope.launch {
            repository.allBoards.collect { boards ->
                cachedBoards = boards
                // Sync home board links
                syncHomeBoardLinks()
            }
        }
    }
    
    
    // Cache service for AI predictions and grammar corrections
    private val phraseCacheService: com.example.myaac.data.cache.PhraseCacheService by lazy {
        val database = com.example.myaac.data.local.AppDatabase.getDatabase(application)
        com.example.myaac.data.cache.PhraseCacheService(database.phraseCacheDao())
    }
    
    // Prediction repository
    private val predictionRepository: com.example.myaac.data.repository.PredictionRepository by lazy {
        val database = com.example.myaac.data.local.AppDatabase.getDatabase(application)
        com.example.myaac.data.repository.PredictionRepository(
            wordFrequencyDao = database.wordFrequencyDao(),
            geminiService = geminiService,
            settingsRepository = settingsRepository,
            cacheService = phraseCacheService
        )
    }

    
    // Prediction update job for debouncing
    private var predictionUpdateJob: kotlinx.coroutines.Job? = null

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
            // Check availability of Default Boards - Force update to fix symbols
            initializePreloadedBoards()
            
            val homeId = "home"
            // Load the default board
            val board = repository.getBoard(homeId)
            _uiState.update { it.copy(currentBoard = board) }
            
            // Trigger initial predictions
            updatePredictions()
        }
        
        // Periodic cache cleanup - run once on startup and then daily
        viewModelScope.launch {
            while (true) {
                try {
                    phraseCacheService.cleanupExpiredEntries()
                    android.util.Log.d("BoardViewModel", "Cache cleanup completed")
                } catch (e: Exception) {
                    android.util.Log.e("BoardViewModel", "Cache cleanup failed", e)
                }
                // Wait 24 hours before next cleanup
                kotlinx.coroutines.delay(24 * 60 * 60 * 1000L)
            }
        }
    }

    private suspend fun initializePreloadedBoards() = coroutineScope {
        val homeId = "home"
        val iWantId = "board_i_want"
        val iNeedId = "board_i_need"
        val iFeelId = "board_i_feel"
        val youId = "board_you"
        val familyId = "board_family"
        val friendsId = "board_friends"
        val appsId = "board_apps"
        
        val currentLang = settingsRepository.settings.value.languageCode
        val localeStr = if (currentLang == "iw") "he" else "en"

        // 1. I Want Board
        if (repository.getBoard(iWantId) == null) {
            val iWantDeferred = listOf(
                async { createButtonWithSymbol(iWantId, 0, getString(com.example.myaac.R.string.btn_i_want), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_want)), "want", localeStr) },
                async { createButtonWithSymbol(iWantId, 1, getString(com.example.myaac.R.string.btn_to_eat), 0xFFFFE0B2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_to_eat)), "eat", localeStr) },
                async { createButtonWithSymbol(iWantId, 2, getString(com.example.myaac.R.string.btn_to_sleep), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_to_sleep)), "sleep", localeStr) },
                async { createButtonWithSymbol(iWantId, 3, getString(com.example.myaac.R.string.btn_to_go), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_to_go)), "go", localeStr) },
                async { createButtonWithSymbol(iWantId, 4, getString(com.example.myaac.R.string.btn_play), 0xFFC5CAE9, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_play)), "play", localeStr) },
                async { createButtonWithSymbol(iWantId, 5, getString(com.example.myaac.R.string.btn_drink), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_drink)), "drink", localeStr) },
                async { createButtonWithSymbol(iWantId, 6, getString(com.example.myaac.R.string.btn_help), 0xFFB2DFDB, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_help)), "help", localeStr) },
                async { createButtonWithSymbol(iWantId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val iWantButtons = iWantDeferred.awaitAll()
            repository.saveBoard(Board(iWantId, getString(com.example.myaac.R.string.board_i_want_name), buttons = iWantButtons, iconPath = "https://static.arasaac.org/pictograms/5441/5441_300.png"))
        }

        // 2. I Need Board
        if (repository.getBoard(iNeedId) == null) {
            val iNeedDeferred = listOf(
                async { createButtonWithSymbol(iNeedId, 0, getString(com.example.myaac.R.string.btn_i_need), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_need)), "need", localeStr) },
                async { createButtonWithSymbol(iNeedId, 1, getString(com.example.myaac.R.string.btn_bathroom), 0xFFCFD8DC, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_bathroom)), "toilet", localeStr) },
                async { createButtonWithSymbol(iNeedId, 2, getString(com.example.myaac.R.string.btn_tissue), 0xFFFFFFFF, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_tissue)), "tissue", localeStr) },
                async { createButtonWithSymbol(iNeedId, 3, getString(com.example.myaac.R.string.btn_help), 0xFFB2DFDB, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_help)), "help", localeStr) },
                async { createButtonWithSymbol(iNeedId, 4, getString(com.example.myaac.R.string.btn_water), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_water)), "water", localeStr) },
                async { createButtonWithSymbol(iNeedId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val iNeedButtons = iNeedDeferred.awaitAll()
            repository.saveBoard(Board(iNeedId, getString(com.example.myaac.R.string.board_i_need_name), buttons = iNeedButtons, iconPath = "https://static.arasaac.org/pictograms/37160/37160_300.png"))
        }

        // 3. I Feel Board (Feelings)
        if (repository.getBoard(iFeelId) == null) {
            val iFeelDeferred = listOf(
                async { createButtonWithSymbol(iFeelId, 0, getString(com.example.myaac.R.string.btn_i_feel), 0xFFE0E0E0, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_i_feel)), "feeling", localeStr) },
                async { createButtonWithSymbol(iFeelId, 1, getString(com.example.myaac.R.string.btn_happy), 0xFFFFF9C4, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_happy)), null, localeStr) },
                async { createButtonWithSymbol(iFeelId, 2, getString(com.example.myaac.R.string.btn_sad), 0xFFE1BEE7, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_sad)), null, localeStr) },
                async { createButtonWithSymbol(iFeelId, 3, getString(com.example.myaac.R.string.btn_mad), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_mad)), "angry", localeStr) },
                async { createButtonWithSymbol(iFeelId, 4, getString(com.example.myaac.R.string.btn_tired), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_tired)), null, localeStr) },
                async { createButtonWithSymbol(iFeelId, 5, getString(com.example.myaac.R.string.btn_scared), 0xFFCFD8DC, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_scared)), null, localeStr) },
                async { createButtonWithSymbol(iFeelId, 6, getString(com.example.myaac.R.string.btn_excited), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_excited)), null, localeStr) },
                async { createButtonWithSymbol(iFeelId, 7, getString(com.example.myaac.R.string.btn_sick), 0xFFB2DFDB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_sick)), null, localeStr) },
                async { createButtonWithSymbol(iFeelId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val iFeelButtons = iFeelDeferred.awaitAll()
            repository.saveBoard(Board(iFeelId, getString(com.example.myaac.R.string.board_feelings_name), buttons = iFeelButtons, iconPath = "https://static.arasaac.org/pictograms/37190/37190_300.png"))
        }

        // 4. You Board (Questions about others)
        if (repository.getBoard(youId) == null) {
            val youDeferred = listOf(
                async { createButtonWithSymbol(youId, 0, getString(com.example.myaac.R.string.btn_how_are_you), 0xFFE1BEE7, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_how_are_you)), "how", localeStr) },
                async { createButtonWithSymbol(youId, 1, getString(com.example.myaac.R.string.btn_what_do_you_want), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_what_do_you_want)), "what", localeStr) },
                async { createButtonWithSymbol(youId, 2, getString(com.example.myaac.R.string.btn_where_are_you), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_where_are_you)), "where", localeStr) },
                async { createButtonWithSymbol(youId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val youButtons = youDeferred.awaitAll()
            repository.saveBoard(Board(youId, getString(com.example.myaac.R.string.board_you_name), buttons = youButtons, iconPath = "https://static.arasaac.org/pictograms/6625/6625_300.png"))
        }

        // 5. Family Board
        if (repository.getBoard(familyId) == null) {
            val familyDeferred = listOf(
                async { createButtonWithSymbol(familyId, 0, getString(com.example.myaac.R.string.btn_mom), 0xFFFFE0B2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_mom)), "mother", localeStr) },
                async { createButtonWithSymbol(familyId, 1, getString(com.example.myaac.R.string.btn_dad), 0xFFBBDEFB, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_dad)), "father", localeStr) },
                async { createButtonWithSymbol(familyId, 2, getString(com.example.myaac.R.string.btn_brother), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_brother)), null, localeStr) },
                async { createButtonWithSymbol(familyId, 3, getString(com.example.myaac.R.string.btn_sister), 0xFFE1BEE7, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_sister)), null, localeStr) },
                async { createButtonWithSymbol(familyId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val familyButtons = familyDeferred.awaitAll()
            repository.saveBoard(Board(familyId, getString(com.example.myaac.R.string.board_family_name), buttons = familyButtons, iconPath = "https://static.arasaac.org/pictograms/38351/38351_300.png"))
        }

        // 6. Friends Board
        if (repository.getBoard(friendsId) == null) {
            val friendsDeferred = listOf(
                async { createButtonWithSymbol(friendsId, 0, getString(com.example.myaac.R.string.btn_friend), 0xFFC5CAE9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_friend)), null, localeStr) },
                async { createButtonWithSymbol(friendsId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val friendsButtons = friendsDeferred.awaitAll()
            repository.saveBoard(Board(friendsId, getString(com.example.myaac.R.string.board_friends_name), buttons = friendsButtons, iconPath = "https://static.arasaac.org/pictograms/2255/2255_300.png"))
        }

        // 7. Apps Board
        if (repository.getBoard(appsId) == null) {
            val appsDeferred = listOf(
                async { createButtonWithSymbol(appsId, 15, getString(com.example.myaac.R.string.btn_back_home), 0xFFFFCC80, ButtonAction.LinkToBoard(homeId), "home", localeStr) }
            )
            val appsButtons = appsDeferred.awaitAll()
            repository.saveBoard(Board(appsId, getString(com.example.myaac.R.string.board_apps_name), buttons = appsButtons, iconPath = "https://static.arasaac.org/pictograms/28099/28099_300.png"))
        }

        // 8. Home Board (Hub) - Links to all category boards
        if (repository.getBoard(homeId) == null) {
            val homeDeferred = listOf(
                // Category buttons - large navigation buttons
                async { createButtonWithSymbol(homeId, 0, getString(com.example.myaac.R.string.board_i_want_name), 0xFFE3F2FD, ButtonAction.LinkToBoard(iWantId), "want", localeStr) },
                async { createButtonWithSymbol(homeId, 1, getString(com.example.myaac.R.string.board_i_need_name), 0xFFFFF3E0, ButtonAction.LinkToBoard(iNeedId), "need", localeStr) },
                async { createButtonWithSymbol(homeId, 2, getString(com.example.myaac.R.string.board_feelings_name), 0xFFFCE4EC, ButtonAction.LinkToBoard(iFeelId), "feeling", localeStr) },
                async { createButtonWithSymbol(homeId, 3, getString(com.example.myaac.R.string.board_you_name), 0xFFE8EAF6, ButtonAction.Speak("you"), "you", localeStr) },
                async { createButtonWithSymbol(homeId, 4, getString(com.example.myaac.R.string.board_family_name), 0xFFFFEBEE, ButtonAction.LinkToBoard(familyId), "family", localeStr) },
                async { createButtonWithSymbol(homeId, 5, getString(com.example.myaac.R.string.board_friends_name), 0xFFE0F2F1, ButtonAction.LinkToBoard(friendsId), "friends", localeStr) },
                async { createButtonWithSymbol(homeId, 6, getString(com.example.myaac.R.string.board_apps_name), 0xFFFFF9C4, ButtonAction.LinkToBoard(appsId), "apps", localeStr) },
                
                // Quick access buttons
                async { createButtonWithSymbol(homeId, 8, getString(com.example.myaac.R.string.btn_yes), 0xFFC8E6C9, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_yes)), null, localeStr) },
                async { createButtonWithSymbol(homeId, 9, getString(com.example.myaac.R.string.btn_no), 0xFFFFCDD2, ButtonAction.Speak(getString(com.example.myaac.R.string.btn_no)), null, localeStr) },
                async { createButtonWithSymbol(homeId, 10, getString(com.example.myaac.R.string.btn_help), 0xFFB2DFDB, ButtonAction.Speak(getString(com.example.myaac.R.string.spoken_help)), "help", localeStr) }
            )
            val homeButtons = homeDeferred.awaitAll()
            
            repository.saveBoard(Board(homeId, getString(com.example.myaac.R.string.board_home_name), buttons = homeButtons, iconPath = "https://static.arasaac.org/pictograms/6964/6964_300.png"))
        }
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
             iconPath = searchSymbols(query)
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

    suspend fun createNewBoard(name: String): String {
        _uiState.update { it.copy(isLoading = true) }
        val newId = java.util.UUID.randomUUID().toString()
        try {
            // Start with an empty board - no pre-filled slots
            val buttons = emptyList<AacButton>()
            
            // Try to find an icon for the board name
            var iconPath: String? = null
            iconPath = searchSymbols(name)

            val newBoard = Board(id = newId, name = name, buttons = buttons, iconPath = iconPath)
            repository.saveBoard(newBoard)
            _uiState.update { it.copy(currentBoard = newBoard) }
            return newId
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }
    
    // Back Stack for navigation
    private val _backStack = java.util.Stack<String>()
    
    val canNavigateBack: Boolean
        get() = !_backStack.isEmpty()

    fun navigateToBoard(boardId: String) {
        viewModelScope.launch {
            // Try to get from cache first
            val board = cachedBoards.find { it.id == boardId } ?: repository.getBoard(boardId)
            
            if (board != null) {
                // If we are already on a board, push it to stack before moving
                // Don't push if we are just refreshing the same board or if it's the same ID
                val currentId = _uiState.value.currentBoard?.id
                if (currentId != null && currentId != boardId) {
                    _backStack.push(currentId)
                }
                
                _uiState.update { it.copy(currentBoard = board) }
                // Trigger predictions update as context (isHomeBoard) might have changed
                // Use instant update logic to ensure home board buttons appear immediately
                updatePredictions(instant = true)
            }
        }
    }

    fun navigateBack(): Boolean {
        if (_backStack.isEmpty()) return false
        
        val previousBoardId = _backStack.pop()
        viewModelScope.launch {
            val board = repository.getBoard(previousBoardId)
            if (board != null) {
                _uiState.update { it.copy(currentBoard = board) }
                
                updatePredictions(instant = true)
            } else {
                // Board might have been deleted? Clear stack or try next?
                // For now, if we fail to go back, just return false or stay here.
                // Maybe recurse?
                navigateBack()
            }
        }
        return true
    }

    suspend fun createMagicBoard(name: String, topic: String, negativeConstraints: String? = null): String = coroutineScope {
        val service = geminiService ?: throw IllegalStateException("Gemini Service not initialized")
        _uiState.update { it.copy(isLoading = true) }
        val newId = java.util.UUID.randomUUID().toString()
        try {
            val langCode = settingsRepository.settings.value.languageCode
            val itemsToGenerate = settingsRepository.settings.value.itemsToGenerate
            val words = service.generateBoard(topic, langCode, itemsToGenerate, negativeConstraints)
            
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
            
            val buttons = deferredButtons.awaitAll()
            
            // Try to find an icon for the topic
            var boardIconPath: String? = searchSymbols(topic)
            
            val newBoard = Board(id = newId, name = name, buttons = buttons, iconPath = boardIconPath)
            repository.saveBoard(newBoard)
            _uiState.update { it.copy(currentBoard = newBoard) }
            return@coroutineScope newId
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    suspend fun addButtonToBoard(boardId: String, button: AacButton) {
        val board = repository.getBoard(boardId) ?: return
        val currentButtons = board.buttons.toMutableList()
        
        // Find first empty slot or append
        // Logic: if we have "gap filling" logic, we might want to respect it, but for now just appending is safest
        // unless we want to replace a placeholder.
        // Step 1: Check if there's a "clean" placeholder to replace? 
        // AacButton(label="", icon=null) is a placeholder.
        val placeholderIndex = currentButtons.indexOfFirst { it.hidden || (it.label.isEmpty() && it.iconPath == null) }
        
        if (placeholderIndex != -1) {
            currentButtons[placeholderIndex] = button.copy(id = "${boardId}_btn_${java.util.UUID.randomUUID()}")
        } else {
            currentButtons.add(button.copy(id = "${boardId}_btn_${java.util.UUID.randomUUID()}"))
        }
        
        val updatedBoard = board.copy(buttons = currentButtons)
        repository.saveBoard(updatedBoard)
        
        // If current board, update UI
        if (_uiState.value.currentBoard?.id == boardId) {
            _uiState.update { it.copy(currentBoard = updatedBoard) }
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

    fun expandBoard(board: Board, itemCount: Int) {
        val service = geminiService ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Check if board isn't at capacity
                val maxCapacity = settingsRepository.settings.value.maxBoardCapacity
                if (board.buttons.size >= maxCapacity) {
                    android.util.Log.d("BoardViewModel", "Board is at max capacity ($maxCapacity)")
                    return@launch
                }

                // Calculate how many items we can actually add
                val itemsToAdd = minOf(itemCount, maxCapacity - board.buttons.size)
                
                if (itemsToAdd <= 0) {
                    android.util.Log.d("BoardViewModel", "No items to add")
                    return@launch
                }

                // Get existing words to avoid duplicates
                val existingWords = board.buttons.map { it.label }.filter { it.isNotEmpty() }
                
                // Generate new words
                val langCode = settingsRepository.settings.value.languageCode
                val topic = board.name 
                
                val newWords = service.generateMoreItems(topic, existingWords, itemsToAdd, langCode)
                
                if (newWords.isEmpty()) {
                     android.util.Log.d("BoardViewModel", "No new words generated")
                     return@launch
                }

                // Create new buttons
                val arasaacLocale = if (langCode == "iw") "he" else "en"
                
                // Calculate available indices to avoid collisions with existing buttons (e.g. Home button at 15)
                val usedIndices = board.buttons.mapNotNull { 
                     // Handle both standard IDs and potentially UUIDs (which won't parse to Int and are ignored here)
                     it.id.substringAfterLast("_btn_").toIntOrNull() 
                }.toSet()
                
                val availableIndices = mutableListOf<Int>()
                var candidate = 0
                // We need as many indices as new words
                while (availableIndices.size < newWords.size) {
                    if (candidate !in usedIndices) {
                        availableIndices.add(candidate)
                    }
                    candidate++
                }

                val deferredNewButtons = newWords.mapIndexed { index, word ->
                    async {
                        createButtonWithSymbol(
                            boardId = board.id,
                            index = availableIndices[index],
                            label = word,
                            color = 0xFFFFF9C4,
                            action = ButtonAction.Speak(word),
                            searchTerm = word,
                            locale = arasaacLocale
                        )
                    }
                }
                
                val newButtons = deferredNewButtons.awaitAll()
                
                // Append new buttons to existing board
                val updatedButtonList = board.buttons + newButtons
                
                val updatedBoard = board.copy(buttons = updatedButtonList)
                repository.saveBoard(updatedBoard)
                
                if (_uiState.value.currentBoard?.id == board.id) {
                    _uiState.update { it.copy(currentBoard = updatedBoard) }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("BoardViewModel", "Error expanding board", e)
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    suspend fun createMagicScene(name: String, image: android.graphics.Bitmap, imageUri: android.net.Uri): String {
        val service = geminiService ?: throw IllegalStateException("Gemini Service not initialized")
        _uiState.update { it.copy(isLoading = true) }
        try {
            android.util.Log.d("BoardViewModel", "Starting scene analysis for: $name")
            
            val objects = service.identifyMultipleItems(image)
            android.util.Log.d("BoardViewModel", "Found ${objects.size} objects")
            
            if (objects.isEmpty()) {
                android.util.Log.w("BoardViewModel", "No objects detected in scene")
                return ""
            }
            
            val newId = "scene_${java.util.UUID.randomUUID()}"
            
            // Save the full image permanently
            val permanentFile = java.io.File(getApplication<android.app.Application>().filesDir, "scene_${newId}.jpg")
            java.io.FileOutputStream(permanentFile).use { out ->
                image.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            }
            val permanentUri = android.net.Uri.fromFile(permanentFile).toString()

            val buttons = objects.mapIndexed { index, pair ->
                val (label, coords) = pair
                AacButton(
                    id = "${newId}_btn_$index",
                    label = label,
                    speechText = null,
                    backgroundColor = 0x00FFFFFF,
                    action = ButtonAction.Speak(label),
                    boundingBox = coords.toList()
                )
            }

            val newBoard = Board(
                id = newId, 
                name = name, 
                buttons = buttons, 
                backgroundImagePath = permanentUri
            )
            repository.saveBoard(newBoard)
            _uiState.update { it.copy(currentBoard = newBoard) }
            return newId
        } finally {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    suspend fun createQuickBoard(name: String, images: List<android.graphics.Bitmap>): String = coroutineScope {
        val service = geminiService ?: throw IllegalStateException("Gemini Service not initialized")
        _uiState.update { it.copy(isLoading = true) }
        try {
            android.util.Log.d("BoardViewModel", "Starting multi-photo board creation for: $name")
            
            // Process all images and collect objects with frequency count
            val objectFrequency = mutableMapOf<String, Int>()
            
            images.forEachIndexed { index, image ->
                val objects = service.identifyMultipleItems(image)
                objects.forEach { (label, _) ->
                    val normalizedLabel = label.lowercase().trim()
                    objectFrequency[normalizedLabel] = (objectFrequency[normalizedLabel] ?: 0) + 1
                }
            }
            
            if (objectFrequency.isEmpty()) {
                android.util.Log.w("BoardViewModel", "No objects detected in any image")
                return@coroutineScope ""
            }
            
            // Sort by frequency and take top items
            val maxItems = settingsRepository.settings.value.itemsToGenerate
            val sortedObjects = objectFrequency.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(maxItems)
                .map { it.key.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
            
            val newId = "quick_${java.util.UUID.randomUUID()}"
            val langCode = settingsRepository.settings.value.languageCode
            val arasaacLocale = if (langCode == "iw") "he" else "en"
            
            // Create buttons with symbols
            val deferredButtons = sortedObjects.mapIndexed { index, label ->
                async {
                    var iconPath: String? = searchSymbols(label)
                    
                    AacButton(
                        id = "${newId}_btn_$index",
                        label = label,
                        speechText = null,
                        backgroundColor = 0xFFFFF9C4,
                        action = ButtonAction.Speak(label),
                        iconPath = iconPath
                    )
                }
            }
            
            val buttons = deferredButtons.awaitAll().toMutableList()
            
            val newBoard = Board(
                id = newId,
                name = name,
                buttons = buttons
            )
            repository.saveBoard(newBoard)
            _uiState.update { it.copy(currentBoard = newBoard) }
            return@coroutineScope newId
        } finally {
            _uiState.update { it.copy(isLoading = false) }
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
        return searchSymbols(query)
    }

    suspend fun simplifyLocationName(rawName: String): String {
        return geminiService?.simplifyLocationName(rawName, settingsRepository.settings.value.languageCode) ?: rawName
    }

    suspend fun suggestBoardName(images: List<android.graphics.Bitmap>): String {
        return geminiService?.suggestBoardName(images, settingsRepository.settings.value.languageCode) ?: ""
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
            is ButtonAction.LaunchApp -> {
                // Launch external app - this will be handled in MainActivity
                // For now, just log it
                android.util.Log.d("BoardViewModel", "Launch app: ${action.packageName}")
            }
            ButtonAction.ClearSentence -> {
                clearSentence()
            }
            ButtonAction.DeleteLastWord -> {
                deleteLastWord()
            }
        }
    }

    private var grammarCheckJob: kotlinx.coroutines.Job? = null
    private var retriggerGrammarOnInteractionEnd = false
    private var applyCorrectionWhenReady = false // Flag to apply correction as soon as it arrives

    fun onInteractionStart() {
        if (grammarCheckJob?.isActive == true) {
            grammarCheckJob?.cancel()
            retriggerGrammarOnInteractionEnd = true
        }
    }

    fun onInteractionEnd() {
        if (retriggerGrammarOnInteractionEnd) {
            retriggerGrammarOnInteractionEnd = false
            triggerGrammarCheck()
        }
    }

    private fun triggerGrammarCheck(force: Boolean = false) {
        grammarCheckJob?.cancel()
        
        // Reset pending state when starting new check (unless we are forcing?)
        // Actually, if we change sentence, we should reset pending. 
        // Logic moved to addToSentence/clearSentence for resetting.
        
        // Don't check if empty or very short
        if (_uiState.value.sentence.isEmpty()) {
            _uiState.update { it.copy(showUndoAi = false, originalSentence = null, pendingGrammarCorrection = null, hasPendingCorrection = false) }
            return
        }
        
        // Check settings
        if (!settingsRepository.settings.value.autoGrammarCheck && !force) {
            // Even if auto is off, we might want to background fetch?
            // User request: "Grammar Check should work automatically behind the scene... but auto correction should be off"
            // So we ALWAYS run the check, but we DON'T apply it automatically unless force is true (or auto is on? No, user said auto off always for now).
            // So we proceed to run the check.
        }

        grammarCheckJob = viewModelScope.launch {
            try {
                // 1. Debounce (wait for user to stop typing)
                if (!force) {
                    kotlinx.coroutines.delay(500)
                }
                
                // 2. Prepare for API call
                val currentSentence = _uiState.value.sentence
                val text = currentSentence.joinToString(" ") { it.textToSpeak }
                
                _uiState.update { it.copy(isGrammarLoading = true) }
                
                // 3. Call Gemini with cache support
                val correctedText = geminiService?.correctGrammar(
                    sentence = text, 
                    languageCode = settingsRepository.settings.value.languageCode,
                    cacheService = phraseCacheService
                )
                
                // 4. Handle Result
                if (correctedText != null && 
                    correctedText.trim().lowercase() != text.trim().lowercase() &&
                    // Ensure the user hasn't typed since we started (though job cancellation handles most of this)
                    _uiState.value.sentence == currentSentence
                ) {
                     // Check if we should apply automatically
                     if (settingsRepository.settings.value.autoGrammarCheck || applyCorrectionWhenReady) {
                         applyGrammarCorrection(currentSentence, correctedText)
                         applyCorrectionWhenReady = false
                     } else {
                         // Background fetch mode: Store it pending
                         _uiState.update { 
                             it.copy(
                                 pendingGrammarCorrection = correctedText,
                                 hasPendingCorrection = true,
                                 isGrammarLoading = false 
                             ) 
                         }
                     }
                } else {
                    // No correction needed or failed
                    _uiState.update { it.copy(isGrammarLoading = false, pendingGrammarCorrection = null, hasPendingCorrection = false) }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isGrammarLoading = false) }
            } finally {
                // if loading was not cleared
                // _uiState.update { it.copy(isGrammarLoading = false) } // Handled in branches
            }
        }
    }
    
    fun applyPendingGrammarCorrection() {
        val state = _uiState.value
        val pending = state.pendingGrammarCorrection
        
        viewModelScope.launch {
            if (pending != null) {
                // Apply immediately
                applyGrammarCorrection(state.sentence, pending)
            } else {
                // No pending correction
                if (state.isGrammarLoading) {
                    // Currently loading, set flag to apply when done
                    applyCorrectionWhenReady = true
                    // Maybe show a "Wait..." toast? Or UI spinner is enough?
                    // For now, UI spinner on the button could be handled by isGrammarLoading
                } else {
                    // Not loading and no pending? Maybe force a check?
                    // This happens if debounce hasn't finished or check failed?
                    // Force check and apply
                    applyCorrectionWhenReady = true
                    triggerGrammarCheck(force = true)
                }
            }
        }
    }

    private suspend fun applyGrammarCorrection(original: List<AacButton>, newText: String) {
        // Reconstruct sentence with icons
        val words = newText.split(Regex("\\s+"))
        val newSentence = words.mapIndexed { index, word ->
            // 1. Try to find in original sentence (preserve icons)
            original.find { it.textToSpeak.equals(word, ignoreCase = true) }
            // 2. Or create new button (try to find icon async? For speed, maybe just text first or fast lookup)
            ?: run {
                // Fast path: Create basic button, maybe background fetch icon?
                // For "Magic" feel, let's try to fetch icon if fast, or just default.
                // We'll use a placeholder for now to be instant.
                 createButton(
                     "grammar_${java.util.UUID.randomUUID()}", 
                     index, 
                     word, 
                     0xFFEFF0F1, // Light Gray for auto-added words
                     ButtonAction.Speak(word)
                 )
            }
        }.map { 
             // Mark auto-added buttons visibly?
             it 
        }

        _uiState.update { 
            it.copy(
                sentence = newSentence,
                originalSentence = original, // Cache for undo
                showUndoAi = true,
                pendingGrammarCorrection = null, // Clear pending
                hasPendingCorrection = false,
                isGrammarLoading = false
            )
        }

        // Auto-speak the corrected sentence
        viewModelScope.launch {
            val textToSpeak = newSentence.joinToString(" ") { it.textToSpeak }
            _speechRequest.emit(textToSpeak)
        }


        // Auto-hide undo after 5 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _uiState.update { it.copy(showUndoAi = false) } // Keep sentence, just hide undo
        }
    }

    fun undoAiCorrection() {
        val original = _uiState.value.originalSentence
        if (original != null) {
            _uiState.update { 
                it.copy(
                    sentence = original,
                    originalSentence = null,
                    showUndoAi = false
                )
            }
        }
    }

    private fun addToSentence(button: AacButton) {
        _uiState.update {
            it.copy(
                sentence = it.sentence + button,
                // Reset AI state on user input
                showUndoAi = false,
                originalSentence = null,
                pendingGrammarCorrection = null,
                hasPendingCorrection = false,
                isGrammarLoading = false // Cancel loading visual (job is cancelled below)
            )
        }
        applyCorrectionWhenReady = false // Cancel auto-apply if new input added
        triggerGrammarCheck()
        updatePredictions() // Update word predictions
    }

    // ========== Word Prediction ==========
    
    /**
     * Update word predictions based on current sentence context
     * @param instant If true, skips the debounce delay (e.g. for navigation)
     */
    private fun updatePredictions(instant: Boolean = false) {
        // Cancel any pending prediction update if we are not in instant mode or if we want to override
        if (!instant) {
            predictionUpdateJob?.cancel()
        }
        
        val settings = settingsRepository.settings.value
        val isHomeBoard = _uiState.value.currentBoard?.id == "home"

        // If predictions disabled, clear them BUT still update Home Board (for links)
        if (!settings.predictionEnabled) {
            _uiState.update { it.copy(predictions = emptyList(), predictionSymbols = emptyMap()) }
            // Note: syncHomeBoardLinks is called automatically via init block when boards update
            return
        }
        
        predictionUpdateJob = viewModelScope.launch {
            try {
                // Debounce only if not instant
                if (!instant) {
                    kotlinx.coroutines.delay(300)
                }
                
                _uiState.update { it.copy(isPredictionLoading = true) }
                
                // Get context from current sentence
                val context = _uiState.value.sentence.map { it.textToSpeak }
                // Re-check home board status inside coroutine (state might have changed)
                val currentIsHomeBoard = _uiState.value.currentBoard?.id == "home"
                
                // Smart contextual predictions
                val boardContext = if (currentIsHomeBoard) null else _uiState.value.currentBoard?.name
                
                val predictions = when {
                    // Empty sentence on home board → Starter words
                    context.isEmpty() && currentIsHomeBoard -> {
                        com.example.myaac.data.nlp.HybridPredictionEngine.getStarterWords(
                            settings.languageCode
                        )
                    }
                    
                    // Single word that's a pronoun → Verbs
                    context.size == 1 && currentIsHomeBoard -> {
                        val pronoun = context[0]
                        val verbs = com.example.myaac.data.nlp.HybridPredictionEngine.getVerbsForPronoun(
                            pronoun,
                            settings.languageCode
                        )
                        if (verbs.isNotEmpty()) verbs else predictionRepository.getPredictions(context, boardContext)
                    }
                    
                    // Multiple words → Check for phrase completions
                    context.size >= 2 && currentIsHomeBoard -> {
                        val phrase = context.joinToString(" ")
                        
                         // Get user-created board names (exclude system boards)
                        val userBoardNames = cachedBoards
                            .filter { it.id != "home" && it.id != "board_you" && it.id != "board_apps" }
                            .map { it.name }
                        
                        val completions = com.example.myaac.data.nlp.HybridPredictionEngine.getCompletionsForPhrase(
                            phrase,
                            settings.languageCode,
                            userBoardNames
                        )
                        if (completions.isNotEmpty()) completions else predictionRepository.getPredictions(context, boardContext)
                    }
                    
                    // Default → AI predictions
                    else -> predictionRepository.getPredictions(context, boardContext)
                }
                
                // Fetch symbols for predictions
                val symbolMap = if (settings.showSymbolsInPredictions && predictions.isNotEmpty()) {
                    fetchSymbolsForPredictions(predictions)
                } else {
                    emptyMap()
                }
                
                // Filter predictions: only show words NOT on the current board
                val visibleWords = if (currentIsHomeBoard) {
                    _uiState.value.currentBoard?.buttons?.map { it.label } ?: emptyList()
                } else {
                    _uiState.value.currentBoard?.buttons?.map { it.label } ?: emptyList()
                }
                
                val smartPredictions = predictions.filter { prediction ->
                    visibleWords.none { it.equals(prediction, ignoreCase = true) }
                }.filter { prediction ->
                    // Filter redundant grammar words that Engine will insert
                    val lastWord = context.lastOrNull()?.lowercase()
                    if (lastWord != null) {
                        val isRedundantTo = prediction == "to" && 
                            (com.example.myaac.util.GrammarEngine.isVerb(lastWord) || lastWord == "go" || lastWord == "come")
                        !isRedundantTo
                    } else {
                        true
                    }
                }
                
                _uiState.update {
                    it.copy(
                        predictions = smartPredictions,
                        predictionSymbols = symbolMap,
                        isPredictionLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("BoardViewModel", "Error updating predictions", e)
                _uiState.update { it.copy(isPredictionLoading = false) }
            }
        }
    }
    
    /**
     * Handle prediction selection by user
     * @param word The predicted word that was selected
     */
    fun onPredictionSelected(word: String) {
        // Create a button for the predicted word
        val symbolUrl = _uiState.value.predictionSymbols[word]
        val button = AacButton(
            id = "prediction_${java.util.UUID.randomUUID()}",
            label = word,
            speechText = word,
            action = ButtonAction.Speak(word),
            backgroundColor = 0xFFE8F5E9, // Light green to indicate it's from prediction
            iconPath = symbolUrl
        )
        
        // Add to sentence
        addToSentence(button)
        
        // Record word usage for learning
        viewModelScope.launch {
            try {
                predictionRepository.recordWordUsage(word)
                android.util.Log.d("BoardViewModel", "Recorded prediction selection: $word")
            } catch (e: Exception) {
                android.util.Log.e("BoardViewModel", "Error recording prediction usage", e)
            }
        }
    }

    /**
     * Sync the list of buttons for the Home Board with available boards
     * Preserves existing button order and only adds/removes links.
     */
    private suspend fun syncHomeBoardLinks() {
        val homeId = "home"
        // 1. Get current persistent board
        val currentHomeBoard = repository.getBoard(homeId) ?: return 
        
        val allValidBoards = cachedBoards // Cached from repository.allBoards collection
        val validBoardIds = allValidBoards.map { it.id }.toSet()
        
        // 2. Filter existing buttons - remove dead links
        val preservedButtons = currentHomeBoard.buttons.filter { button ->
            if (button.action is ButtonAction.LinkToBoard) {
                 val targetId = (button.action).boardId
                 // Keep link only if board exists
                 validBoardIds.contains(targetId)
            } else {
                true // Keep other buttons (manual additions, Quick Access, etc.)
            }
        }
        
        // 3. Identify missing links
        // We need to ensure every board (except Home, You, Apps?) has a link
        val existingLinkTargets = preservedButtons
            .mapNotNull { (it.action as? ButtonAction.LinkToBoard)?.boardId }
            .toSet()
            
        // Boards that should have links but don't
        val missingBoards = allValidBoards.filter { board ->
             val id = board.id
             id != homeId && id != "board_you" && id != "board_apps" 
                 && !existingLinkTargets.contains(id)
        }
        
        // No changes needed?
        if (missingBoards.isEmpty() && preservedButtons.size == currentHomeBoard.buttons.size) {
            return
        }
        
        val newLinkButtons = missingBoards.map { board ->
             AacButton(
                 id = "link_${homeId}_board_${board.id}",
                 label = board.name,
                 speechText = null,
                 backgroundColor = 0xFFE3F2FDL, // Light blue
                 action = ButtonAction.LinkToBoard(board.id),
                 iconPath = board.iconPath
             )
        }
        
        // 4. Assemble new list - append new links
        val newButtonList = preservedButtons + newLinkButtons
        
        // 5. Save if changed
        if (newButtonList != currentHomeBoard.buttons) {
            val updatedBoard = currentHomeBoard.copy(buttons = newButtonList)
            repository.saveBoard(updatedBoard)
            
            // Update UI if currently on Home
            if (_uiState.value.currentBoard?.id == homeId) {
                 _uiState.update { it.copy(currentBoard = updatedBoard) }
            }
        }
    }
    
    /**
     * Fetch symbols for predicted words
     */
    private suspend fun fetchSymbolsForPredictions(predictions: List<String>): Map<String, String?> {
        return predictions.associateWith { word ->
            try {
                checkSymbol(word)
            } catch (e: Exception) {
                null
            }
        }
    }
    

    
    /**
     * Clear learned prediction data
     */
    fun clearLearnedVocabulary() {
        viewModelScope.launch {
            try {
                predictionRepository.clearLearnedData()
                // Clear current predictions
                _uiState.update { it.copy(predictions = emptyList(), predictionSymbols = emptyMap()) }
            } catch (e: Exception) {
                android.util.Log.e("BoardViewModel", "Error clearing learned vocabulary", e)
            }
        }
    }



    fun clearSentence() {
        _uiState.update {
            it.copy(
                sentence = emptyList(),
                showUndoAi = false,
                originalSentence = null,
                predictions = emptyList(), // Clear predictions
                predictionSymbols = emptyMap(),
                pendingGrammarCorrection = null,
                hasPendingCorrection = false
            )
        }
        applyCorrectionWhenReady = false
        grammarCheckJob?.cancel()
        predictionUpdateJob?.cancel() // Cancel pending prediction updates
        
        // Record sentence for learning before clearing
        viewModelScope.launch {
            val words = _uiState.value.sentence.map { it.textToSpeak }
            if (words.isNotEmpty()) {
                predictionRepository.recordSentence(words)
            }
        }
    }
    
    fun deleteLastWord() { // Exposed public wrapper
        if (_uiState.value.sentence.isNotEmpty()) {
            _uiState.update { 
                it.copy(
                    sentence = it.sentence.dropLast(1),
                    showUndoAi = false,
                    originalSentence = null,
                    pendingGrammarCorrection = null,
                    hasPendingCorrection = false
                ) 
            }
            applyCorrectionWhenReady = false
            triggerGrammarCheck()
            updatePredictions() // Update predictions after deletion
        }
    }

    fun updateButton(button: AacButton) {
        val current = _uiState.value.currentBoard ?: return
        val buttons = current.buttons.toMutableList()
        val index = buttons.indexOfFirst { it.id == button.id }
        
        if (index != -1) {
            buttons[index] = button
        } else {
            buttons.add(button)
        }
        
        val updatedBoard = current.copy(buttons = buttons)
        viewModelScope.launch {
            repository.saveBoard(updatedBoard)
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

    // ==================== Selection Management Functions ====================
    
    fun toggleButtonSelection(buttonId: String) {
        _uiState.update { currentState ->
            val newSelection = if (buttonId in currentState.selectedButtonIds) {
                currentState.selectedButtonIds - buttonId
            } else {
                currentState.selectedButtonIds + buttonId
            }
            currentState.copy(selectedButtonIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedButtonIds = emptySet()) }
    }

    fun deleteSelectedButtons() {
        val current = _uiState.value.currentBoard ?: return
        val selectedIds = _uiState.value.selectedButtonIds
        
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val updatedList = current.buttons.filterNot { it.id in selectedIds }
            val updatedBoard = current.copy(buttons = updatedList)
            repository.saveBoard(updatedBoard)
            _uiState.update { 
                it.copy(
                    currentBoard = updatedBoard,
                    selectedButtonIds = emptySet()
                )
            }
        }
    }

    fun toggleHideSelectedButtons() {
        val current = _uiState.value.currentBoard ?: return
        val selectedIds = _uiState.value.selectedButtonIds
        
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val updatedList = current.buttons.map { button ->
                if (button.id in selectedIds) {
                    button.copy(hidden = !button.hidden)
                } else {
                    button
                }
            }
            val updatedBoard = current.copy(buttons = updatedList)
            repository.saveBoard(updatedBoard)
            _uiState.update { 
                it.copy(
                    currentBoard = updatedBoard,
                    selectedButtonIds = emptySet()
                )
            }
        }
    }

    fun duplicateSelectedButtons() {
        val current = _uiState.value.currentBoard ?: return
        val selectedIds = _uiState.value.selectedButtonIds
        
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val selectedButtons = current.buttons.filter { it.id in selectedIds }
            val newButtons = selectedButtons.map { original ->
                original.copy(id = "${current.id}_btn_${java.util.UUID.randomUUID()}")
            }
            
            val updatedList = current.buttons + newButtons
            val updatedBoard = current.copy(buttons = updatedList)
            repository.saveBoard(updatedBoard)
            _uiState.update { 
                it.copy(
                    currentBoard = updatedBoard,
                    selectedButtonIds = emptySet()
                )
            }
        }
    }

    fun moveSelectedButtonsToBoard(targetBoardId: String) {
        val current = _uiState.value.currentBoard ?: return
        val selectedIds = _uiState.value.selectedButtonIds
        
        if (selectedIds.isEmpty() || current.id == targetBoardId) return

        viewModelScope.launch {
            val targetBoard = repository.getBoard(targetBoardId) ?: return@launch
            val selectedButtons = current.buttons.filter { it.id in selectedIds }
            
            val updatedCurrentList = current.buttons.filterNot { it.id in selectedIds }
            val updatedCurrentBoard = current.copy(buttons = updatedCurrentList)
            
            val movedButtons = selectedButtons.map { button ->
                button.copy(id = "${targetBoardId}_btn_${java.util.UUID.randomUUID()}")
            }
            val updatedTargetList = targetBoard.buttons + movedButtons
            val updatedTargetBoard = targetBoard.copy(buttons = updatedTargetList)
            
            repository.saveBoard(updatedCurrentBoard)
            repository.saveBoard(updatedTargetBoard)
            
            _uiState.update { 
                it.copy(
                    currentBoard = updatedCurrentBoard,
                    selectedButtonIds = emptySet()
                )
            }
        }
    }

    fun reorderButtons(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.currentBoard ?: return
        
        viewModelScope.launch {
            val mutableList = current.buttons.toMutableList()
            if (fromIndex in mutableList.indices && toIndex in mutableList.indices) {
                val item = mutableList.removeAt(fromIndex)
                mutableList.add(toIndex, item)
                
                val updatedBoard = current.copy(buttons = mutableList)
                repository.saveBoard(updatedBoard)
                _uiState.update { it.copy(currentBoard = updatedBoard) }
            }
        }
    }

    fun updateBoardButtons(newButtons: List<AacButton>) {
        val current = _uiState.value.currentBoard ?: return
        val updatedBoard = current.copy(buttons = newButtons)
        viewModelScope.launch {
            repository.saveBoard(updatedBoard)
            _uiState.update { it.copy(currentBoard = updatedBoard) }
        }
    }

    fun syncAppsBoard() {
        viewModelScope.launch {
            val appsId = "board_apps"
            val homeId = "home"
            val appsBoard = repository.getBoard(appsId) ?: return@launch
            
            // Get configured app shortcuts from settings
            val appShortcuts = settingsRepository.getAppShortcuts()
            
            // Create buttons for each app shortcut
            val appButtons = appShortcuts.mapIndexed { index, shortcut ->
                AacButton(
                    id = "${appsId}_app_$index",
                    label = shortcut.appName,
                    speechText = null,
                    backgroundColor = 0xFFFFF9C4,
                    action = ButtonAction.LaunchApp(shortcut.packageName),
                    iconPath = null // Could fetch app icon in future
                )
            }.toMutableList()
            
            // Add "Back Home" button at the end
            appButtons.add(
                AacButton(
                    id = "${appsId}_btn_15",
                    label = getString(com.example.myaac.R.string.btn_back_home),
                    speechText = null,
                    backgroundColor = 0xFFFFCC80,
                    action = ButtonAction.LinkToBoard(homeId),
                    iconPath = null
                )
            )
            
            // Update the Apps board
            val updatedBoard = appsBoard.copy(buttons = appButtons)
            repository.saveBoard(updatedBoard)
            
            // If currently viewing Apps board, update UI
            if (_uiState.value.currentBoard?.id == appsId) {
                _uiState.update { it.copy(currentBoard = updatedBoard) }
            }
        }
    }

    fun backupToCloud(userId: String, onCompletion: (String) -> Unit) {
        if (cloudRepository == null) {
            onCompletion("Error: Cloud service not available")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Get all boards (fresh from repo to be safe)
                val boards = repository.allBoards.first()
                // Actually repository.allBoards is a Flow so we need to collect or first()
                // Assuming we can get them.
                
                // If the flow is empty, backup what we have in cache?
                val boardsToBackup = if (boards.isNotEmpty()) boards else cachedBoards
                
                var successCount = 0
                boardsToBackup.forEach { board ->
                    cloudRepository.backupBoard(board, userId)
                    successCount++
                }
                
                onCompletion("Successfully backed up $successCount boards")
            } catch (e: Exception) {
                e.printStackTrace()
                onCompletion("Backup Failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun restoreFromCloud(userId: String, onCompletion: (String) -> Unit) {
        if (cloudRepository == null) {
            onCompletion("Error: Cloud service not available")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val boards = cloudRepository.restoreBoards(userId)
                
                // Save all to local DB
                boards.forEach { board ->
                    repository.saveBoard(board)
                }
                
                // Reload current board if needed or just navigate home
                if (boards.isNotEmpty()) {
                    navigateToBoard("home")
                    // trigger refresh
                    updatePredictions()
                }
                
                onCompletion("Restored ${boards.size} boards")
            } catch (e: Exception) {
                e.printStackTrace()
                onCompletion("Restore Failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun submitAgentQuery(query: String) {
        val service = geminiService ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAgentProcessing = true, agentResponse = null) }
            try {
                val action = service.parseAgentCommand(query)
                
                when (action) {
                    is com.example.myaac.data.remote.GeminiService.AgentAction.CreateBoard -> {
                        _uiState.update { it.copy(agentResponse = "Creating board '${action.topic}'...") }
                        createMagicBoard(action.topic, action.topic, action.negativeConstraints)
                        _uiState.update { it.copy(agentResponse = "Board created!") }
                    }
                    is com.example.myaac.data.remote.GeminiService.AgentAction.AnswerQuestion -> {
                        val answer = service.answerQuestion(action.question)
                        _uiState.update { it.copy(agentResponse = answer) }
                    }
                    is com.example.myaac.data.remote.GeminiService.AgentAction.Unknown -> {
                        val answer = service.answerQuestion(query) // Fallback to Q&A if intent is unclear but maybe just a question
                        _uiState.update { it.copy(agentResponse = answer) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(agentResponse = "Error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isAgentProcessing = false) }
            }
        }
    }
    
    fun clearAgentResponse() {
        _uiState.update { it.copy(agentResponse = null) }
    }
    
    // ========== Cache Management ==========
    
    /**
     * Get cache statistics for debugging/monitoring
     */
    suspend fun getCacheStats(): com.example.myaac.data.cache.CacheStats {
        return phraseCacheService.getCacheStats()
    }
    
    /**
     * Clear all phrase cache (predictions and grammar)
     */
    suspend fun clearPhraseCache() {
        phraseCacheService.clearAll()
        android.util.Log.d("BoardViewModel", "Phrase cache cleared")
    }
    
    /**
     * Clear only prediction cache
     */
    suspend fun clearPredictionCache() {
        phraseCacheService.clearByType(com.example.myaac.data.local.CacheType.PREDICTION)
        android.util.Log.d("BoardViewModel", "Prediction cache cleared")
    }
    
    /**
     * Clear only grammar cache
     */
    suspend fun clearGrammarCache() {
        phraseCacheService.clearByType(com.example.myaac.data.local.CacheType.GRAMMAR)
        android.util.Log.d("BoardViewModel", "Grammar cache cleared")
    }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyAacApplication)
                BoardViewModel(
                    application, 
                    application.repository, 
                    application.settingsRepository,
                    application.cloudRepository, 
                    application.geminiService
                )
            }
        }
    }
}
