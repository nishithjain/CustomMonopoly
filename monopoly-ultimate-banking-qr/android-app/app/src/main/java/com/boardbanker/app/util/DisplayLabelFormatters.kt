package com.boardbanker.app.util

fun formatEnumLabel(raw: String): String =
    raw.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { char -> char.titlecase() }
        }

fun pluralize(count: Int, singular: String): String =
    if (count == 1) singular else "${singular}s"
