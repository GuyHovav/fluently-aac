package com.example.myaac.model

data class HomeButtonConfig(
    val id: String,
    val label: String,
    val iconPath: String? = null,
    val action: ButtonAction,
    val backgroundColor: Long = 0xFFE3F2FD,
    val order: Int = 0,
    val isVisible: Boolean = true
)
