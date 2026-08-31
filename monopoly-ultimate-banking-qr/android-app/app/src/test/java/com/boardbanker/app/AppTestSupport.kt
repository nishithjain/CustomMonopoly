package com.boardbanker.app

import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import java.nio.file.Path

object AppTestSupport {
    val dataDir: Path = resolveDataDir()
    val editionRepository: EditionRepository = EditionRepository(FileEditionFileSource(dataDir))
    val definitions: GameDefinitions = editionRepository.load(EditionIds.UK)
    val engine: GameEngine = DefaultGameEngine(definitions)

    fun defaultTestPlayerName(playerId: String): String = when (playerId) {
        "USR_01" -> "Nishith"
        "USR_02" -> "Aditya"
        "USR_03" -> "Rahul"
        "USR_04" -> "Arun"
        else -> "Player"
    }

    fun newGame(playerIds: List<String> = listOf("USR_01", "USR_02")): GameSession {
        var result = engine.process(
            GameSession(gameId = "APP_TEST_GAME", editionId = definitions.editionId),
            GameCommand.CreateGame("APP_TEST_GAME"),
        )
        for (playerId in playerIds) {
            result = engine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, defaultTestPlayerName(playerId)),
            )
        }
        result = engine.process(result.session, GameCommand.StartGame)
        return result.session
    }

    fun sessionManager(
        repository: FakeGameSessionRepository = FakeGameSessionRepository(),
    ): ActiveGameSessionManager = sessionManagerWithStore(repository).first

    fun sessionManagerWithStore(
        repository: FakeGameSessionRepository = FakeGameSessionRepository(),
    ): Pair<ActiveGameSessionManager, CommittedGameSessionStore> {
        val store = CommittedGameSessionStore(repository)
        val manager = ActiveGameSessionManager(
            editionResolver = { editionId -> editionRepository.load(editionId) },
            committedStore = store,
            repository = repository,
        )
        return manager to store
    }

    fun sessionWithProperty(
        propertyId: String,
        ownerId: String?,
        rentLevel: Int = 1,
        playerIds: List<String> = listOf("USR_01", "USR_02"),
    ): GameSession {
        val session = newGame(playerIds)
        val property = session.properties[propertyId]!!
        return session.copy(
            properties = session.properties + (
                propertyId to property.copy(
                    ownerPlayerId = ownerId,
                    currentRentLevel = rentLevel,
                )
            ),
        )
    }

    private fun resolveDataDir(): Path = listOf(
        Path.of("../../data"),
        Path.of("../../../data"),
        Path.of("../../../../monopoly-ultimate-banking-qr/data"),
        Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
    ).first { it.resolve("common/card_registry.json").toFile().exists() }
}
