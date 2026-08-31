package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EditionCatalog(
    val defaultEditionId: String,
    val editions: List<EditionCatalogEntry>,
)

@Serializable
data class EditionCatalogEntry(
    val editionId: String,
    val name: String,
    val enabled: Boolean,
)
