package com.example.myaac.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boards")
data class Board(
    @PrimaryKey val id: String,
    val name: String,
    val rows: Int = 4,
    val columns: Int = 4,
    val buttons: List<AacButton> = emptyList(),
    val iconPath: String? = null
)
