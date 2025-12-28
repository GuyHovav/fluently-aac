package com.example.myaac.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.Font
import com.example.myaac.R

fun getFontFamily(name: String): FontFamily {
    return when (name) {
        "OpenDyslexic" -> FontFamily(Font(R.font.open_dyslexic_regular))
        "Atkinson Hyperlegible" -> FontFamily(Font(R.font.atkinson_hyperlegible_regular))
        "Andika" -> FontFamily(Font(R.font.andika_regular))
        "Serif" -> FontFamily.Serif
        "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}

fun getTypography(fontFamily: FontFamily): Typography {
    return Typography(
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        titleLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        labelSmall = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        // Add other styles as needed, defaulting them to use the custom fontFamily
        displayLarge = Typography().displayLarge.copy(fontFamily = fontFamily),
        displayMedium = Typography().displayMedium.copy(fontFamily = fontFamily),
        displaySmall = Typography().displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = Typography().headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = Typography().headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = Typography().headlineSmall.copy(fontFamily = fontFamily),
        titleMedium = Typography().titleMedium.copy(fontFamily = fontFamily),
        titleSmall = Typography().titleSmall.copy(fontFamily = fontFamily),
        bodyMedium = Typography().bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = Typography().bodySmall.copy(fontFamily = fontFamily),
        labelLarge = Typography().labelLarge.copy(fontFamily = fontFamily),
        labelMedium = Typography().labelMedium.copy(fontFamily = fontFamily),
    )
}

// Keep original Typography for default/fallback if needed, or deprecate it
val Typography = getTypography(FontFamily.Default)
