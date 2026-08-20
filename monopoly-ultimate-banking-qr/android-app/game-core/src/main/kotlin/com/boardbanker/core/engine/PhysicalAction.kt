package com.boardbanker.core.engine

data class PhysicalAction(
    val instruction: String,
    val affectedPlayerIds: List<String> = emptyList(),
    val targetSpace: String? = null,
)
