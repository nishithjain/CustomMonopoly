package com.boardbanker.core.persistence

import com.boardbanker.core.model.EditionDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

class SavedGameRestoreOrchestrator(
    private val serializer: GameSessionSerializer,
    private val editionLoader: (String) -> GameDefinitions,
    private val manifestLoader: (String) -> EditionDefinition,
    private val metadataReader: SavedGameMetadataReader = SavedGameMetadataReader(),
    private val sessionRestoreValidatorFactory: (GameDefinitions) -> SessionRestoreValidator =
        { definitions -> SessionRestoreValidator(definitions) },
) {
    var editionLoadCount: Int = 0
        private set
    var semanticValidationCount: Int = 0
        private set

    private val versionChecker = EditionDefinitionVersionChecker(manifestLoader)

    fun restore(raw: RawSavedGame): SavedGameLoadResult {
        if (raw.schemaVersion > GameSessionSchema.CURRENT_VERSION) {
            return SavedGameLoadResult.IncompatibleVersion(
                found = raw.schemaVersion,
                supported = GameSessionSchema.CURRENT_VERSION,
            )
        }

        when (val metadataResult = metadataReader.read(raw.sessionJson, raw.schemaVersion)) {
            is SavedGameMetadataReadResult.Corrupted ->
                return SavedGameLoadResult.Corrupted(metadataResult.reason)
            is SavedGameMetadataReadResult.Success -> {
                val metadata = metadataResult.metadata
                val gameStatus = metadataReader.readGameStatus(raw.sessionJson)
                versionChecker.checkMetadata(metadata, gameStatus)?.let { return it }

                val definitions = loadEditionDefinitions(metadata.editionId) ?: return editionLoadFailure(
                    metadata.editionId,
                )

                val session = deserializeSession(raw.sessionJson) ?: return SavedGameLoadResult.Corrupted(
                    "Invalid session JSON",
                )

                semanticValidationCount++
                val validationProblems = sessionRestoreValidatorFactory(definitions).validate(session)
                if (validationProblems.isNotEmpty()) {
                    return SavedGameLoadResult.SessionValidationFailed(
                        editionId = metadata.editionId,
                        reason = validationProblems.joinToString("; "),
                    )
                }

                return SavedGameLoadResult.Success(session)
            }
        }
    }

    fun validateForSave(session: GameSession): SavedGameLoadResult? {
        val editionId = EditionIds.normalize(session.editionId)
        val definitions = loadEditionDefinitions(editionId) ?: return editionLoadFailure(editionId)
        semanticValidationCount++
        val validationProblems = sessionRestoreValidatorFactory(definitions).validate(session)
        if (validationProblems.isEmpty()) {
            return null
        }
        return SavedGameLoadResult.SessionValidationFailed(
            editionId = editionId,
            reason = validationProblems.joinToString("; "),
        )
    }

    private fun loadEditionDefinitions(editionId: String): GameDefinitions? {
        editionLoadCount++
        return try {
            editionLoader(editionId)
        } catch (_: Exception) {
            null
        }
    }

    private fun editionLoadFailure(editionId: String): SavedGameLoadResult.MissingEdition =
        SavedGameLoadResult.MissingEdition(
            editionId = editionId,
            reason = "Edition data for '$editionId' is not available.",
        )

    private fun deserializeSession(sessionJson: String): GameSession? =
        try {
            serializer.deserialize(sessionJson)
        } catch (_: Exception) {
            null
        }
}
