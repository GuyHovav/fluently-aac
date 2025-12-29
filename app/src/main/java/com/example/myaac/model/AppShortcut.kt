package com.example.myaac.model

data class AppShortcut(
    val packageName: String,
    val appName: String,
    val iconPath: String? = null  // For future use if we want to cache icons
)
