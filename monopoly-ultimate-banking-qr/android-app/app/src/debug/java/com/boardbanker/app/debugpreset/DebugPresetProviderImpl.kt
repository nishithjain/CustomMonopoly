package com.boardbanker.app.debugpreset

import android.content.res.AssetManager
import com.boardbanker.app.util.GameIdProvider
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.ColorGroupState
import com.boardbanker.core.model.EnergyGridState
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.DebtResolutionState
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.PlayerState
import com.boardbanker.core.model.PropertyState
import com.boardbanker.core.model.TurnKind
import com.boardbanker.core.model.TurnState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SessionRestoreValidator

class DebugPresetProviderImpl(
    private val services: DebugPresetServices,
    private val presetPathProvider: (() -> List<String>)? = null,
    private val presetTextReader: (String) -> String = { path ->
        services.assetManager.open(path).bufferedReader().use { it.readText() }
    },
) : DebugPresetProvider {
    constructor(services: DebugPresetServices) : this(
        services = services,
        presetPathProvider = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = KotlinGameSessionSerializer(json)
    private val maxJsonSize = 2 * 1024 * 1024

    override val available: Boolean = true

    override suspend fun discover(): List<DebugPresetSummary> = presetPaths()
        .map { path ->
            runCatching { decode(path) }
                .fold(
                    onSuccess = { preset ->
                    DebugPresetSummary(
                        presetId = preset.presetId,
                        name = preset.name,
                        description = preset.description,
                        editionId = preset.editionId,
                        startDestination = preset.startDestination,
                        players = preset.players.map { player ->
                            DebugPresetPlayerSummary(
                                name = player.name,
                                token = player.token,
                                balance = player.balance,
                                propertyCount = player.ownedPropertyIds.size,
                                energyGridCount = player.ownedEnergyGridIds.size,
                            )
                        },
                        ownedProperties = preset.properties.map { it.propertyId },
                        ownedEnergyGrids = preset.energyGrids.map { it.energyGridId },
                        pendingDebt = preset.pendingDebt?.let { "${it.amountRemaining} remaining (${it.reason})" },
                        pendingWorkflow = preset.startDestination,
                    )
                    },
                    onFailure = {
                        DebugPresetSummary(
                            presetId = "invalid:$path",
                            name = "Invalid debug preset: $path",
                            description = "",
                            editionId = "invalid",
                            players = emptyList(),
                            startDestination = "INVALID",
                        )
                    },
                )
        }
        .sortedBy { it.name }

    override suspend fun load(presetId: String): DebugPresetLoadResult = runCatching {
        val path = presetPaths().singleOrNull { path ->
            runCatching { decode(path).presetId == presetId }
                .getOrDefault("invalid:$path" == presetId)
        }
            ?: error("Debug preset '$presetId' was not found")
        val preset = decode(path)
        buildSession(preset) to summary(preset)
    }.fold(
        onSuccess = { (session, summary) -> DebugPresetLoadResult.Success(session, summary) },
        onFailure = { DebugPresetLoadResult.Failure(it.message ?: "Invalid debug preset") },
    )

    override suspend fun importJson(jsonText: String): DebugPresetLoadResult = runCatching {
        require(jsonText.toByteArray(Charsets.UTF_8).size <= maxJsonSize) {
            "Debug state JSON exceeds the ${maxJsonSize / 1024} KB limit"
        }
        val root = json.decodeFromString<JsonObject>(jsonText)
        val mode = root["mode"]?.toString()?.trim('"')?.uppercase()
            ?: error("Missing mode; expected BUILDER or RAW_SESSION")
        when (mode) {
            "BUILDER" -> {
                val preset = json.decodeFromJsonElement(DebugPresetJson.serializer(), root)
                buildSession(preset) to summary(preset)
            }
            "RAW_SESSION" -> {
                val sessionElement = root["session"] ?: error("RAW_SESSION is missing session")
                val session = serializer.deserialize(json.encodeToString(JsonElement.serializer(), sessionElement))
                validateRawSession(session)
                session to rawSummary(root, session)
            }
            else -> error("Unsupported mode '$mode'; expected BUILDER or RAW_SESSION")
        }
    }.fold(
        onSuccess = { (session, summary) -> DebugPresetLoadResult.Success(session, summary) },
        onFailure = { DebugPresetLoadResult.Failure(it.message ?: "Invalid debug state JSON") },
    )

    override suspend fun exportCurrent(): DebugPresetExportResult {
        val session = services.committedGameSessionStore.currentSession()
            ?: return DebugPresetExportResult.Failure("There is no current saved game to export")
        val sessionElement = json.decodeFromString<JsonElement>(serializer.serialize(session))
        val wrapper = buildJsonObject {
            put("schemaVersion", 1)
            put("mode", "RAW_SESSION")
            put("name", "Exported ${session.gameId}")
            put("session", sessionElement)
        }
        return DebugPresetExportResult.Success(json.encodeToString(JsonObject.serializer(), wrapper))
    }

    override fun hasActiveSavedGame(): Boolean = services.committedGameSessionStore.currentSession() != null

    override suspend fun start(session: GameSession): DebugPresetLoadResult {
        val previousGameId = services.committedGameSessionStore.currentSession()?.gameId
        services.activeGameSessionManager.bindEditionForSetup(session.editionId)
        return when (val commit = services.committedGameSessionStore.commitGameResult(GameResult(session))) {
            is com.boardbanker.app.persistence.CommitResult.Persisted -> {
                if (previousGameId != null && previousGameId != commit.session.gameId) {
                    services.committedGameSessionStore.deleteSavedGame(previousGameId)
                }
                DebugPresetLoadResult.Success(commit.session)
            }
            is com.boardbanker.app.persistence.CommitResult.PersistenceFailed ->
                DebugPresetLoadResult.Failure("Could not save debug game: ${commit.reason}")
            is com.boardbanker.app.persistence.CommitResult.NotPersisted ->
                DebugPresetLoadResult.Failure("Could not save debug game")
        }
    }

    private fun buildSession(preset: DebugPresetJson): GameSession {
        require(preset.players.isNotEmpty()) { "Preset must contain at least one player" }
        require(preset.players.map { it.playerId }.distinct().size == preset.players.size) {
            "Preset contains duplicate player IDs"
        }
        require(preset.currentPlayerId in preset.players.map { it.playerId }) {
            "currentPlayerId must reference a preset player"
        }

        val definitions = services.loadDefinitions(preset.editionId)
        val editionVersion = definitions.edition?.definitionVersion
            ?: error("Edition '${definitions.editionId}' has no definition version")
        val ownersByAsset = mutableMapOf<String, String>()
        preset.properties.forEach { property ->
            require(definitions.properties.containsKey(property.propertyId)) { "Unknown property ID '${property.propertyId}'" }
            property.ownerPlayerId?.let { owner ->
                val previous = ownersByAsset.put(property.propertyId, owner)
                require(previous == null || previous == owner) {
                    "Asset '${property.propertyId}' is assigned more than once"
                }
            }
        }
        preset.energyGrids.forEach { grid ->
            require(definitions.energyGrids.containsKey(grid.energyGridId)) { "Unknown Energy Grid ID '${grid.energyGridId}'" }
            grid.ownerPlayerId?.let { owner ->
                val previous = ownersByAsset.put(grid.energyGridId, owner)
                require(previous == null || previous == owner) {
                    "Asset '${grid.energyGridId}' is assigned more than once"
                }
            }
        }
        val players = preset.players.map { player ->
            val definition = definitions.players[player.playerId]
                ?: error("Unknown Player Card ID '${player.playerId}'")
            require(definition.displayName.equals(player.token, ignoreCase = true)) {
                "Token '${player.token}' does not match ${player.playerId} (${definition.displayName})"
            }
            require(player.balance >= 0) { "Balance for ${player.playerId} cannot be negative" }
            require(player.skipTurns >= 0) { "skipTurns for ${player.playerId} cannot be negative" }
            require(player.ownedPropertyIds.size == player.ownedPropertyIds.distinct().size) {
                "Asset is assigned more than once to ${player.playerId}"
            }
            require(player.ownedEnergyGridIds.size == player.ownedEnergyGridIds.distinct().size) {
                "Asset is assigned more than once to ${player.playerId}"
            }
            player.ownedPropertyIds.forEach { propertyId ->
                require(definitions.properties.containsKey(propertyId)) { "Unknown property ID '$propertyId'" }
                claim(ownersByAsset, propertyId, player.playerId)
            }
            player.ownedEnergyGridIds.forEach { gridId ->
                require(definitions.energyGrids.containsKey(gridId)) { "Unknown Energy Grid ID '$gridId'" }
                claim(ownersByAsset, gridId, player.playerId)
            }
            player.playerId to PlayerState(
                playerId = player.playerId,
                playerName = player.name,
                balance = player.balance,
                jailStatus = player.inJail,
                pendingSkipTurnCount = player.skipTurns,
            )
        }.toMap()

        ownersByAsset.forEach { (assetId, ownerId) ->
            require(ownerId in players) { "Asset '$assetId' references unknown player '$ownerId'" }
        }

        val rentLevels = preset.rentLevels
        rentLevels.keys.forEach { propertyId ->
            require(definitions.properties.containsKey(propertyId)) { "Unknown rent-level property ID '$propertyId'" }
        }
        val properties = definitions.properties.values.associate { definition ->
            val level = preset.properties.firstOrNull { it.propertyId == definition.propertyId }?.rentLevel
                ?: rentLevels[definition.propertyId] ?: definition.initialRentLevel
            require(level in 1..definitions.rules.rent.maximumRentLevel) {
                "Invalid rent level $level for ${definition.propertyId}"
            }
            definition.propertyId to PropertyState(
                propertyId = definition.propertyId,
                ownerPlayerId = ownersByAsset[definition.propertyId],
                currentRentLevel = level,
            )
        }
        val energyGrids = definitions.energyGrids.values.associate { definition ->
            definition.energyGridId to EnergyGridState(
                energyGridId = definition.energyGridId,
                ownerPlayerId = ownersByAsset[definition.energyGridId],
            )
        }
        val order = preset.players.map { it.playerId }
        val debt = preset.pendingDebt?.let { pending ->
            require(pending.debtorId in players) { "pendingDebt.debtorId references an unknown player" }
            require(pending.creditorId == EntityRef.BANK || pending.creditorId in players) {
                "pendingDebt.creditorId references an unknown player"
            }
            require(pending.debtorId != pending.creditorId) { "pendingDebt debtor and creditor must differ" }
            require(pending.originalAmountDue > 0 && pending.cashAmountUsed >= 0 && pending.amountRemaining >= 0) {
                "pendingDebt amounts must be non-negative and originalAmountDue must be positive"
            }
            require(pending.originalAmountDue == pending.cashAmountUsed + pending.amountRemaining) {
                "pendingDebt.originalAmountDue must equal cashAmountUsed + amountRemaining"
            }
            if (pending.reason == DebtReason.RENT) {
                require(pending.propertyId != null) { "RENT pendingDebt requires propertyId" }
                require(properties[pending.propertyId]?.ownerPlayerId == pending.creditorId) {
                    "pendingDebt.propertyId must be owned by the creditor"
                }
            }
            DebtResolutionState(
                debtorPlayerId = pending.debtorId,
                creditorPlayerId = pending.creditorId,
                amountRemaining = pending.amountRemaining,
                reason = pending.reason,
                propertyId = pending.propertyId,
                originalAmountDue = pending.originalAmountDue,
                cashAmountUsed = pending.cashAmountUsed,
            )
        }
        return GameSession(
            gameId = GameIdProvider.newGameId(),
            editionId = definitions.editionId,
            editionDefinitionVersion = editionVersion,
            status = GameStatus.ACTIVE,
            players = players,
            playerJoinOrder = order,
            properties = properties,
            energyGrids = energyGrids,
            colorGroups = definitions.boardRelationships.colorGroups.keys.associateWith { ColorGroupState(it) },
            turnState = TurnState(preset.currentPlayerId, order, TurnKind.NORMAL),
            debtResolution = debt,
            debugPresetId = preset.presetId,
        )
    }

    private fun validateRawSession(session: GameSession) {
        val definitions = services.loadDefinitions(session.editionId)
        val expectedVersion = services.loadEditionDefinition(session.editionId).definitionVersion
        require(session.editionDefinitionVersion == expectedVersion) {
            "Edition definition version ${session.editionDefinitionVersion} does not match $expectedVersion"
        }
        val problems = SessionRestoreValidator(definitions).validate(session)
        require(problems.isEmpty()) { problems.joinToString("; ") }
    }

    private fun summary(preset: DebugPresetJson): DebugPresetSummary = DebugPresetSummary(
        presetId = preset.presetId,
        name = preset.name,
        description = preset.description,
        editionId = preset.editionId,
        startDestination = preset.startDestination,
        players = preset.players.map {
            DebugPresetPlayerSummary(it.name, it.token, it.balance, it.ownedPropertyIds.size, it.ownedEnergyGridIds.size)
        },
        ownedProperties = preset.properties.map { it.propertyId } + preset.players.flatMap { it.ownedPropertyIds },
        ownedEnergyGrids = preset.energyGrids.map { it.energyGridId } + preset.players.flatMap { it.ownedEnergyGridIds },
        pendingDebt = preset.pendingDebt?.let { "${it.amountRemaining} remaining (${it.reason})" },
        pendingWorkflow = preset.startDestination,
    )

    private fun rawSummary(root: JsonObject, session: GameSession): DebugPresetSummary = DebugPresetSummary(
        presetId = session.gameId,
        name = root["name"]?.toString()?.trim('"') ?: session.gameId,
        description = "Imported production GameSession",
        editionId = session.editionId,
        startDestination = if (session.debtResolution != null) "DEBT_RESOLUTION" else "ACTIVE_GAME",
        players = session.playerJoinOrder.mapNotNull { id -> session.players[id]?.let { DebugPresetPlayerSummary(it.playerName, id, it.balance, session.properties.values.count { p -> p.ownerPlayerId == id }, session.energyGrids.values.count { g -> g.ownerPlayerId == id }) } },
        ownedProperties = session.properties.values.filter { it.ownerPlayerId != null }.map { it.propertyId },
        ownedEnergyGrids = session.energyGrids.values.filter { it.ownerPlayerId != null }.map { it.energyGridId },
        pendingDebt = session.debtResolution?.let { "${it.amountRemaining} remaining (${it.reason})" },
        pendingWorkflow = if (session.debtResolution != null) "DEBT_RESOLUTION" else "ACTIVE_GAME",
    )

    private fun claim(owners: MutableMap<String, String>, assetId: String, playerId: String) {
        require(owners[assetId] == null || owners[assetId] == playerId) {
            "Asset '$assetId' is assigned more than once"
        }
        owners[assetId] = playerId
    }

    private fun presetPaths(): List<String> = presetPathProvider?.invoke()
        ?: (discoverPaths("debug-game-states").ifEmpty { discoverPaths("game-presets") })

    private fun discoverPaths(path: String): List<String> {
        val children = services.assetManager.list(path).orEmpty()
        return children.flatMap { child ->
            val childPath = "$path/$child"
            if (child.endsWith(".json", ignoreCase = true)) listOf(childPath)
            else discoverPaths(childPath)
        }
    }

    private fun decode(path: String): DebugPresetJson = json.decodeFromString(presetTextReader(path))
}

@Serializable
private data class DebugPresetJson(
    val schemaVersion: Int = 1,
    val mode: String = "BUILDER",
    val presetId: String,
    val name: String,
    val description: String = "",
    val editionId: String,
    val startDestination: String = "ACTIVE_GAME",
    val currentPlayerId: String,
    val players: List<DebugPresetPlayerJson>,
    val rentLevels: Map<String, Int> = emptyMap(),
    val properties: List<DebugPresetPropertyJson> = emptyList(),
    val energyGrids: List<DebugPresetEnergyGridJson> = emptyList(),
    val pendingDebt: DebugPresetDebtJson? = null,
)

@Serializable
private data class DebugPresetPlayerJson(
    val playerId: String,
    val name: String,
    val token: String,
    val balance: Int,
    val ownedPropertyIds: List<String> = emptyList(),
    val ownedEnergyGridIds: List<String> = emptyList(),
    val inJail: Boolean = false,
    val skipTurns: Int = 0,
)

@Serializable
private data class DebugPresetPropertyJson(
    val propertyId: String,
    val ownerPlayerId: String? = null,
    val rentLevel: Int? = null,
)

@Serializable
private data class DebugPresetEnergyGridJson(
    val energyGridId: String,
    val ownerPlayerId: String? = null,
)

@Serializable
private data class DebugPresetDebtJson(
    val debtorId: String,
    val creditorId: String,
    val originalAmountDue: Int,
    val cashAmountUsed: Int,
    val amountRemaining: Int,
    val reason: DebtReason,
    val propertyId: String? = null,
)
