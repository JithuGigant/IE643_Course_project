package com.ie643.cxr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0B6E6E)
private val Light = lightColorScheme(
    primary = Teal, onPrimary = Color.White, primaryContainer = Color(0xFFCDEBEA), onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6362), background = Color(0xFFF6FAFA), surface = Color(0xFFF6FAFA),
    surfaceVariant = Color(0xFFDBE5E4), error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7FD6D4), onPrimary = Color(0xFF003736), primaryContainer = Color(0xFF00504F), onPrimaryContainer = Color(0xFFCDEBEA),
    secondary = Color(0xFFB0CCCB), background = Color(0xFF0E1514), surface = Color(0xFF0E1514),
    surfaceVariant = Color(0xFF3F4948), error = Color(0xFFF2B8B5),
)

val WarnColor = Color(0xFFB26A00)
val BlockColor = Color(0xFFB3261E)
val HintColor = Color(0xFF5F6B6B)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
