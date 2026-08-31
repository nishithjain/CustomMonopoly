package com.boardbanker.core.persistence

import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession

class EditionDefinitionVersionChecker(
    private val manifestLoader: (String) -> EditionDefinition,
) {
    fun check(session: GameSession): SavedGameLoadResult? =
        checkMetadata(
            metadata = SavedGameMetadata(
                editionId = session.editionId,
                editionDefinitionVersion = session.editionDefinitionVersion,
            ),
            gameStatus = session.status,
        )

    fun checkMetadata(
        metadata: SavedGameMetadata,
        gameStatus: com.boardbanker.core.model.GameStatus? = null,
    ): SavedGameLoadResult? {
        val editionId = EditionIds.normalize(metadata.editionId)
        val manifest = try {
            manifestLoader(editionId)
        } catch (ex: Exception) {
            return SavedGameLoadResult.MissingEdition(
                editionId = editionId,
                reason = ex.message ?: "Edition data is not available.",
            )
        }
        if (metadata.editionDefinitionVersion != manifest.definitionVersion) {
            return SavedGameLoadResult.IncompatibleEditionVersion(
                editionId = editionId,
                editionName = manifest.name,
                savedVersion = metadata.editionDefinitionVersion,
                installedVersion = manifest.definitionVersion,
                gameStatus = gameStatus,
            )
        }
        return null
    }
}
