package com.boardbanker.app

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameEngine
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.validation.GameDefinitionLoader
import java.nio.file.Path
import kotlin.io.path.readText

object AppTestSupport {
    val definitions: GameDefinitions = loadDefinitions()
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
            GameSession(gameId = "APP_TEST_GAME"),
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

    private fun loadDefinitions(): GameDefinitions {
        val dataDir = listOf(
            Path.of("../../data"),
            Path.of("../../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        ).first { it.resolve("cards.json").toFile().exists() }
        val loader = GameDefinitionLoader()
        return loader.loadAll(
            cardsJson = dataDir.resolve("cards.json").readText(),
            propertiesJson = dataDir.resolve("properties.json").readText(),
            eventsJson = dataDir.resolve("events.json").readText(),
            eventEngineRulesJson = dataDir.resolve("event_engine_rules.json").readText(),
            boardRelationshipsJson = dataDir.resolve("board_relationships.json").readText(),
            gameRulesJson = dataDir.resolve("game_rules.json").readText(),
            bankingValuesJson = dataDir.resolve("banking_values.json").readText(),
        )
    }
}
