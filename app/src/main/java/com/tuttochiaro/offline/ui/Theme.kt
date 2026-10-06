package com.tuttochiaro.offline.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TuttoColors {
    val Paper = Color(0xFFF5F0E5)
    val PaperDeep = Color(0xFFE9E0CF)
    val Ink = Color(0xFF132936)
    val InkSoft = Color(0xFF50636A)
    val Tomato = Color(0xFFD9573F)
    val TomatoDark = Color(0xFFA63C2D)
    val Olive = Color(0xFF4E6A52)
    val Sun = Color(0xFFF1B24B)
    val White = Color(0xFFFFFCF5)
}

private val LightColors = lightColorScheme(
    primary = TuttoColors.Tomato,
    onPrimary = TuttoColors.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = TuttoColors.TomatoDark,
    secondary = TuttoColors.Olive,
    onSecondary = TuttoColors.White,
    background = TuttoColors.Paper,
    onBackground = TuttoColors.Ink,
    surface = TuttoColors.White,
    onSurface = TuttoColors.Ink,
    surfaceVariant = TuttoColors.PaperDeep,
    onSurfaceVariant = TuttoColors.InkSoft,
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 37.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp),
)

@Composable
fun TuttoChiaroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
