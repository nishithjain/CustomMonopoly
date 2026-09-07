package com.boardbanker.app

import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.dice.DiceRoller
import com.boardbanker.core.dice.RandomDiceRoller
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

    fun newGameForEdition(
        editionId: String,
        playerIds: List<String> = listOf("USR_01", "USR_02"),
    ): GameSession {
        val editionDefinitions = editionRepository.load(editionId)
        val editionEngine = DefaultGameEngine(editionDefinitions)
        var result = editionEngine.process(
            GameSession(
                gameId = "APP_TEST_GAME",
                editionId = editionId,
                editionDefinitionVersion = editionDefinitions.edition!!.definitionVersion,
            ),
            GameCommand.CreateGame("APP_TEST_GAME"),
        )
        for (playerId in playerIds) {
            result = editionEngine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, defaultTestPlayerName(playerId)),
            )
        }
        result = editionEngine.process(result.session, GameCommand.StartGame)
        return result.session
    }

    fun sessionManager(
        repository: FakeGameSessionRepository = FakeGameSessionRepository(),
        diceRoller: DiceRoller = RandomDiceRoller(),
    ): ActiveGameSessionManager = sessionManagerWithStore(repository, diceRoller).first

    fun sessionManagerWithStore(
        repository: FakeGameSessionRepository = FakeGameSessionRepository(),
        diceRoller: DiceRoller = RandomDiceRoller(),
    ): Pair<ActiveGameSessionManager, CommittedGameSessionStore> {
        val store = CommittedGameSessionStore(repository)
        val manager = ActiveGameSessionManager(
            editionResolver = { editionId -> editionRepository.load(editionId) },
            committedStore = store,
            repository = repository,
            diceRoller = diceRoller,
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

    fun sessionWithActivePlayer(session: GameSession, playerId: String): GameSession {
        var current = session
        val activeEngine = if (session.editionId == EditionIds.INDIA) {
            DefaultGameEngine(editionRepository.load(EditionIds.INDIA))
        } else {
            engine
        }
        repeat(session.players.size + 1) {
            if (current.turnState?.activePlayerId == playerId) return current
            current = activeEngine.process(
                current,
                GameCommand.EndTurn(current.turnState!!.activePlayerId),
            ).session
        }
        error("Could not advance turn to $playerId")
    }

    private fun resolveDataDir(): Path = listOf(
        Path.of("../../data"),
        Path.of("../../../data"),
        Path.of("../../../../monopoly-ultimate-banking-qr/data"),
        Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
    ).first { it.resolve("common/card_registry.json").toFile().exists() }
}
