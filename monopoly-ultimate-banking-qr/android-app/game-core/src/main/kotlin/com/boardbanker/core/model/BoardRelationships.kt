package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BoardRelationships(
    val colorGroups: Map<String, List<String>>,
    val neighbours: Map<String, List<String>>,
    val boardSides: Map<String, List<String>>,
    val propertyToSide: Map<String, String>,
)
