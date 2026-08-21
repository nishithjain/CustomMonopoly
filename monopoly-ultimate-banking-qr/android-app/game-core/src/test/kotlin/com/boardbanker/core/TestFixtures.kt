package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TemporaryEffect
import java.nio.file.Path

object TestFixtures {
    private val dataDir: Path = resolveDataDir()
    private val editionRepository = EditionRepository(FileEditionFileSource(dataDir))

    val definitions: GameDefinitions = editionRepository.load(EditionIds.DEFAULT)
    val engine: GameEngine = DefaultGameEngine(definitions)

    fun loadEdition(editionId: String): GameDefinitions = editionRepository.load(editionId)

    private fun resolveDataDir(): Path {
        val candidates = listOf(
            Path.of("../../../data"),
            Path.of("../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        )
        return candidates.firstOrNull { it.resolve("common/card_registry.json").toFile().exists() }
            ?: error("Could not locate data directory")
    }

    fun defaultTestPlayerName(playerId: String): String = when (playerId) {
        "USR_01" -> "Nishith"
        "USR_02" -> "Aditya"
        "USR_03" -> "Rahul"
        "USR_04" -> "Arun"
        else -> "Player"
    }

    fun newGame(playerIds: List<String> = listOf("USR_01", "USR_02")): GameSession {
        var result = engine.process(
            GameSession(gameId = "TEST_GAME", editionId = definitions.editionId),
            GameCommand.CreateGame("TEST_GAME"),
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

    fun sessionWithProperty(
        propertyId: String,
        ownerId: String?,
        rentLevel: Int = 1,
        players: List<String> = listOf("USR_01", "USR_02"),
    ): GameSession {
        val session = newGame(players)
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

    fun sessionWithBalances(
        balances: Map<String, Int>,
        players: List<String> = listOf("USR_01", "USR_02"),
    ): GameSession {
        var session = newGame(players)
        session = session.copy(
            players = session.players.mapValues { (id, player) ->
                balances[id]?.let { player.copy(balance = it) } ?: player
            },
        )
        return session
    }

    fun sessionWithJail(playerId: String, inJail: Boolean = true): GameSession {
        val session = newGame()
        val player = session.players[playerId]!!.copy(jailStatus = inJail)
        return session.copy(players = session.players + (playerId to player))
    }

    fun sessionWithTemporaryEffect(effect: TemporaryEffect): GameSession {
        val session = newGame()
        return session.copy(temporaryEffects = listOf(effect))
    }

    fun rentAmount(propertyId: String, level: Int): Int =
        definitions.properties[propertyId]!!.rentLevels.first { it.level == level }.amount
}
