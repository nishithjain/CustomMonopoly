package com.boardbanker.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun DieFace(
    value: Int?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val displayValue = value?.coerceIn(1, 6)
    val pipColor = MaterialTheme.colorScheme.onSurface
    val background = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        modifier = modifier
            .size(72.dp)
            .semantics {
                contentDescription = if (displayValue != null) {
                    "$label: $displayValue"
                } else {
                    "$label: not rolled"
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = background,
        tonalElevation = 2.dp,
    ) {
        if (displayValue != null) {
            DiePips(value = displayValue, color = pipColor)
        }
    }
}

@Composable
private fun DiePips(
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val positions = pipPositions(value)
    Canvas(modifier = modifier.size(72.dp)) {
        val radius = size.minDimension * 0.08f
        positions.forEach { (xRatio, yRatio) ->
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(size.width * xRatio, size.height * yRatio),
            )
        }
    }
}

private fun pipPositions(value: Int): List<Pair<Float, Float>> = when (value) {
    1 -> listOf(0.5f to 0.5f)
    2 -> listOf(0.28f to 0.28f, 0.72f to 0.72f)
    3 -> listOf(0.28f to 0.28f, 0.5f to 0.5f, 0.72f to 0.72f)
    4 -> listOf(
        0.28f to 0.28f,
        0.72f to 0.28f,
        0.28f to 0.72f,
        0.72f to 0.72f,
    )
    5 -> listOf(
        0.28f to 0.28f,
        0.72f to 0.28f,
        0.5f to 0.5f,
        0.28f to 0.72f,
        0.72f to 0.72f,
    )
    6 -> listOf(
        0.28f to 0.22f,
        0.72f to 0.22f,
        0.28f to 0.5f,
        0.72f to 0.5f,
        0.28f to 0.78f,
        0.72f to 0.78f,
    )
    else -> emptyList()
}
