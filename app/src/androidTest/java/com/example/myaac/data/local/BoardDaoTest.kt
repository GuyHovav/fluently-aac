package com.example.myaac.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myaac.model.AacButton
import com.example.myaac.model.Board
import com.example.myaac.model.ButtonAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BoardDaoTest {
    private lateinit var boardDao: BoardDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        boardDao = db.boardDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetBoard() = kotlinx.coroutines.test.runTest {
        val board = Board(id = "1", name = "Test Board", buttons = emptyList())
        boardDao.insertBoard(board)
        val byId = boardDao.getBoardById("1")
        assertEquals(board.name, byId?.name)
    }

    @Test
    @Throws(Exception::class)
    fun updateBoardButtons() = kotlinx.coroutines.test.runTest {
        val button1 = AacButton(id = "btn1", label = "Hello", speechText = "Hello", action = ButtonAction.Speak("Hello"))
        val board = Board(id = "2", name = "Button Board", buttons = listOf(button1))
        
        boardDao.insertBoard(board)
        
        var retrieved = boardDao.getBoardById("2")
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.buttons?.size)
        assertEquals("Hello", retrieved?.buttons?.get(0)?.label)

        // Update
        val button2 = AacButton(id = "btn2", label = "World", speechText = "World", action = ButtonAction.Speak("World"))
        val updatedButtons = retrieved!!.buttons + button2
        val updatedBoard = retrieved.copy(buttons = updatedButtons)
        
        boardDao.insertBoard(updatedBoard) // Insert with OnConflictStrategy.REPLACE
        
        retrieved = boardDao.getBoardById("2")
        assertEquals(2, retrieved?.buttons?.size)
        assertEquals("World", retrieved?.buttons?.get(1)?.label)
    }
}
