package com.example.myaac.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size classes based on Material Design 3 breakpoints
 */
enum class WindowWidthSizeClass {
    /** Width < 600dp - Phone in portrait, small tablets in portrait */
    Compact,
    /** Width 600dp-840dp - Large phone in landscape, tablet in portrait, foldables */
    Medium,
    /** Width > 840dp - Tablet in landscape, desktop */
    Expanded
}

enum class WindowHeightSizeClass {
    /** Height < 480dp */
    Compact,
    /** Height 480dp-900dp */
    Medium,
    /** Height > 900dp */
    Expanded
}

/**
 * Data class representing the current window size classification
 */
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val widthDp: Dp,
    val heightDp: Dp
)

/**
 * Composable function that calculates and remembers the current window size class
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val widthDp = configuration.screenWidthDp.dp
        val heightDp = configuration.screenHeightDp.dp
        
        val widthClass = when {
            configuration.screenWidthDp < 600 -> WindowWidthSizeClass.Compact
            configuration.screenWidthDp < 840 -> WindowWidthSizeClass.Medium
            else -> WindowWidthSizeClass.Expanded
        }
        
        val heightClass = when {
            configuration.screenHeightDp < 480 -> WindowHeightSizeClass.Compact
            configuration.screenHeightDp < 900 -> WindowHeightSizeClass.Medium
            else -> WindowHeightSizeClass.Expanded
        }
        
        WindowSizeClass(
            widthSizeClass = widthClass,
            heightSizeClass = heightClass,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}

/**
 * Extension functions for convenient size class checks
 */

/**
 * Returns true if device is a tablet (width >= 600dp)
 */
fun WindowSizeClass.isTablet(): Boolean {
    return widthSizeClass != WindowWidthSizeClass.Compact
}

/**
 * Returns true if device is in landscape orientation with tablet width
 */
fun WindowSizeClass.isLandscapeTablet(): Boolean {
    return widthSizeClass == WindowWidthSizeClass.Expanded || 
           (widthSizeClass == WindowWidthSizeClass.Medium && widthDp > heightDp)
}

/**
 * Returns true if device is in portrait orientation
 */
fun WindowSizeClass.isPortrait(): Boolean {
    return heightDp > widthDp
}

/**
 * Returns true if device is in landscape orientation
 */
fun WindowSizeClass.isLandscape(): Boolean {
    return widthDp > heightDp
}

/**
 * Returns true if the device should use a permanent sidebar (large landscape tablets)
 */
fun WindowSizeClass.shouldUsePermanentSidebar(): Boolean {
    return widthSizeClass == WindowWidthSizeClass.Expanded && isLandscape()
}

/**
 * Returns the recommended number of grid columns based on screen size
 * @param baseColumns The default number of columns for phone screens
 */
fun WindowSizeClass.getRecommendedColumns(baseColumns: Int): Int {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> baseColumns
        WindowWidthSizeClass.Medium -> if (isPortrait()) {
            baseColumns + 1
        } else {
            baseColumns + 2
        }
        WindowWidthSizeClass.Expanded -> if (isPortrait()) {
            baseColumns + 2
        } else {
            baseColumns + 3
        }
    }
}

/**
 * Returns a content padding multiplier based on screen size
 * Use this to increase padding on larger screens for better visual hierarchy
 */
fun WindowSizeClass.getContentPaddingMultiplier(): Float {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 1.0f
        WindowWidthSizeClass.Medium -> 1.25f
        WindowWidthSizeClass.Expanded -> 1.5f
    }
}

/**
 * Returns a recommended sidebar width for tablet layouts
 */
fun WindowSizeClass.getSidebarWidth(): Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 0.dp // No permanent sidebar
        WindowWidthSizeClass.Medium -> 300.dp
        WindowWidthSizeClass.Expanded -> 360.dp
    }
}
