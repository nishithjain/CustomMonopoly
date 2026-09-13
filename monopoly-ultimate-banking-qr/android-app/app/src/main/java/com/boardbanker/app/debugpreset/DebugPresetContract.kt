package com.boardbanker.app.debugpreset

import android.content.res.AssetManager
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession

interface DebugPresetServices {
    val assetManager: AssetManager
    val committedGameSessionStore: CommittedGameSessionStore
    val activeGameSessionManager: ActiveGameSessionManager
    fun loadDefinitions(editionId: String): GameDefinitions
    fun loadEditionDefinition(editionId: String): com.boardbanker.core.model.EditionDefinition
}

data class DebugPresetSummary(
    val presetId: String,
    val name: String,
    val description: String,
    val editionId: String,
    val startDestination: String,
    val players: List<DebugPresetPlayerSummary>,
    val ownedProperties: List<String> = emptyList(),
    val ownedEnergyGrids: List<String> = emptyList(),
    val pendingDebt: String? = null,
    val pendingWorkflow: String? = null,
)

data class DebugPresetPlayerSummary(
    val name: String,
    val token: String,
    val balance: Int,
    val propertyCount: Int,
    val energyGridCount: Int,
)

sealed interface DebugPresetLoadResult {
    data class Success(val session: GameSession, val summary: DebugPresetSummary? = null) : DebugPresetLoadResult
    data class Failure(val message: String) : DebugPresetLoadResult
}

interface DebugPresetProvider {
    val available: Boolean
    suspend fun discover(): List<DebugPresetSummary>
    suspend fun load(presetId: String): DebugPresetLoadResult
    suspend fun importJson(json: String): DebugPresetLoadResult
    suspend fun exportCurrent(): DebugPresetExportResult
    fun hasActiveSavedGame(): Boolean
    suspend fun start(session: GameSession): DebugPresetLoadResult
}

sealed interface DebugPresetExportResult {
    data class Success(val json: String) : DebugPresetExportResult
    data class Failure(val message: String) : DebugPresetExportResult
}

object DebugPresetProviderFactory {
    fun create(services: DebugPresetServices): DebugPresetProvider {
        if (!com.boardbanker.app.BuildConfig.DEBUG) return NoOpDebugPresetProvider
        return runCatching {
            val type = Class.forName("com.boardbanker.app.debugpreset.DebugPresetProviderImpl")
            type.getConstructor(DebugPresetServices::class.java)
                .newInstance(services) as DebugPresetProvider
        }.getOrElse { NoOpDebugPresetProvider }
    }
}

private object NoOpDebugPresetProvider : DebugPresetProvider {
    override val available: Boolean = false
    override suspend fun discover(): List<DebugPresetSummary> = emptyList()
    override suspend fun load(presetId: String): DebugPresetLoadResult =
        DebugPresetLoadResult.Failure("Debug presets are unavailable in this build")
    override suspend fun importJson(json: String): DebugPresetLoadResult =
        DebugPresetLoadResult.Failure("Debug presets are unavailable in this build")
    override suspend fun exportCurrent(): DebugPresetExportResult =
        DebugPresetExportResult.Failure("Debug presets are unavailable in this build")
    override fun hasActiveSavedGame(): Boolean = false
    override suspend fun start(session: GameSession): DebugPresetLoadResult =
        DebugPresetLoadResult.Failure("Debug presets are unavailable in this build")
}
