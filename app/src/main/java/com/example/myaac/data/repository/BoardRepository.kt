package com.example.myaac.data.repository

import com.example.myaac.data.local.BoardDao
import com.example.myaac.model.Board
import kotlinx.coroutines.flow.Flow

class BoardRepository(private val boardDao: BoardDao) {

    val allBoards: Flow<List<Board>> = boardDao.getAllBoards()

    suspend fun getBoard(id: String): Board? {
        return boardDao.getBoardById(id)
    }

    suspend fun saveBoard(board: Board) {
        boardDao.insertBoard(board)
    }
    
    suspend fun deleteBoard(id: String) {
        boardDao.deleteBoard(id)
    }
    
    suspend fun getAllButtonLabels(): List<String> {
        return boardDao.getAllBoardsData().flatMap { board -> 
            board.buttons.map { it.label }
        }.distinct()
    }
    
    // Helper to find a button by label (for Smart Strip action)
    suspend fun findButtonByLabel(label: String): com.example.myaac.model.AacButton? {
        val boards = boardDao.getAllBoardsData()
        for (board in boards) {
            val btn = board.buttons.find { it.label.equals(label, ignoreCase = true) }
            if (btn != null) return btn
        }
        return null
    }
}
