package com.example.myaac.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myaac.model.Board
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards")
    fun getAllBoards(): Flow<List<Board>>

    @Query("SELECT * FROM boards WHERE id = :boardId")
    suspend fun getBoardById(boardId: String): Board?
    
    @Query("SELECT * FROM boards WHERE id = :boardId")
    fun getBoardasFlow(boardId: String): Flow<Board?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoard(board: Board)

    @Query("DELETE FROM boards WHERE id = :boardId")
    suspend fun deleteBoard(boardId: String)
    
    // For Smart Strip AI
    @Query("SELECT * FROM boards")
    suspend fun getAllBoardsData(): List<Board> 
    // Room might not query inside JSON directly easily without TypeConverters, 
    // so we fetch boards and flatten in Repo.
}
