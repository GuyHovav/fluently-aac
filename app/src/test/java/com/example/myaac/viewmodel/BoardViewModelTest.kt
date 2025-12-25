package com.example.myaac.viewmodel

import com.example.myaac.data.remote.ArasaacService
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.repository.BoardRepository
import com.example.myaac.model.AacButton
import com.example.myaac.model.Board
import com.example.myaac.model.ButtonAction
import com.example.myaac.rules.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: BoardRepository
    private lateinit var settingsRepository: com.example.myaac.data.repository.SettingsRepository
    private lateinit var geminiService: GeminiService
    private lateinit var arasaacService: ArasaacService
    private lateinit var viewModel: BoardViewModel

    @Before
    fun setup() {
        repository = mock()
        geminiService = mock()
        arasaacService = mock()
        settingsRepository = mock()
        
        // Mock settings flow
        val settingsFlow = kotlinx.coroutines.flow.MutableStateFlow(
            com.example.myaac.data.repository.AppSettings(textScale = 1.0f)
        )
        whenever(settingsRepository.settings).thenReturn(settingsFlow)
        
        // Initialize ViewModel with mocks
        viewModel = BoardViewModel(repository, settingsRepository, geminiService, arasaacService)
        // Clear invocations from init block
        clearInvocations(repository)
    }

    @Test
    fun `createMagicBoard generates board using gemini service`() = runTest {
        // Arrange
        val topic = "Zoo Animals"
        val expectedWords = listOf("Lion", "Tiger", "Bear")
        whenever(geminiService.generateBoard(topic)).thenReturn(expectedWords)
        whenever(arasaacService.searchPictograms(any())).thenReturn(emptyList())
        
        // Act
        viewModel.createMagicBoard("My Zoo", topic)
        advanceUntilIdle()

        // Assert
        // Verify service was called
        verify(geminiService).generateBoard(topic)
        
        // Verify board was saved to repository
        argumentCaptor<Board>().apply {
            verify(repository).saveBoard(capture())
            
            val savedBoard = firstValue
            assertEquals("My Zoo", savedBoard.name)
            // Check if buttons were created from words
            val buttonLabels = savedBoard.buttons.filter { it.label.isNotEmpty() }.map { it.label }
            assertTrue(buttonLabels.containsAll(expectedWords))
        }
    }

    @Test
    fun `createMagicBoard handles empty response gracefully`() = runTest {
         // Arrange
        val topic = "Unknown"
        whenever(geminiService.generateBoard(topic)).thenReturn(emptyList())

        // Act
        viewModel.createMagicBoard("Empty Board", topic)
        advanceUntilIdle()

        // Assert
        verify(repository).saveBoard(any())
    }

    @Test
    fun `deleteBoard calls repository to delete board`() = runTest {
        // Arrange
        val boardId = "board_to_delete"
        
        // Act
        viewModel.deleteBoard(boardId)
        advanceUntilIdle()
        
        // Assert
        verify(repository).deleteBoard(boardId)
    }

    @Test
    fun `deleteBoard navigates to home if current board is deleted`() = runTest {
        // Arrange
        val boardId = "board_to_delete"
        val homeId = "home"
        val boardToDelete = Board(id = boardId, name = "Delete Me", buttons = emptyList())
        val homeBoard = Board(id = homeId, name = "Home", buttons = emptyList())
        
        whenever(repository.getBoard(boardId)).thenReturn(boardToDelete)
        whenever(repository.getBoard(homeId)).thenReturn(homeBoard)
        
        // Set state to the board we want to delete
        viewModel.navigateToBoard(boardId)
        
        // Act
        viewModel.deleteBoard(boardId)
        advanceUntilIdle()
        
        // Assert
        verify(repository).deleteBoard(boardId)
        // Check for navigation to home
        verify(repository, atLeastOnce()).getBoard(homeId) 
    }

    // Prediction feature has been removed

    @Test
    fun `unlockCaregiverMode with correct PIN updates state to true`() = runTest {
        // Act
        val result = viewModel.unlockCaregiverMode("1234")

        // Assert
        assertTrue(result)
        assertTrue(viewModel.uiState.value.isCaregiverMode)
    }

    @Test
    fun `unlockCaregiverMode with incorrect PIN keeps state as false`() = runTest {
        // Act
        val result = viewModel.unlockCaregiverMode("wrong")

        // Assert
        assertFalse(result)
        assertFalse(viewModel.uiState.value.isCaregiverMode)
    }

    @Test
    fun `lockCaregiverMode updates state to false`() = runTest {
        // Arrange
        viewModel.unlockCaregiverMode("1234")
        assertTrue(viewModel.uiState.value.isCaregiverMode)

        // Act
        viewModel.lockCaregiverMode()

        // Assert
        assertFalse(viewModel.uiState.value.isCaregiverMode)
    }}
