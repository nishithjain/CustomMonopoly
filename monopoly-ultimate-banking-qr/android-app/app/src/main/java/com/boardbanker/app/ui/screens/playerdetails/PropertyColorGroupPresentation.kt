package com.boardbanker.app.ui.screens.playerdetails

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun propertyColorGroupColor(colorGroup: String): Color =
    when (colorGroup.uppercase()) {
        "BROWN" -> Color(0xFF8B4513)
        "LIGHT_BLUE" -> Color(0xFF87CEEB)
        "PINK" -> Color(0xFFFF69B4)
        "ORANGE" -> Color(0xFFFF8C00)
        "RED" -> Color(0xFFE53935)
        "YELLOW" -> Color(0xFFFFD54F)
        "GREEN" -> Color(0xFF43A047)
        "DARK_BLUE" -> Color(0xFF1E88E5)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
