package com.boardbanker.core.validation

import com.boardbanker.core.model.EditionDefinition

object DefinitionVersionValidator {
    fun validate(edition: EditionDefinition): List<String> {
        if (edition.definitionVersion < 1) {
            return listOf(
                "Edition '${edition.editionId}': definitionVersion must be >= 1 (found ${edition.definitionVersion}).",
            )
        }
        return emptyList()
    }
}
