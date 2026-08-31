package com.boardbanker.app

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

    private fun loadDefinitions(): GameDefinitions {
        val dataDir = listOf(
            Path.of("../../data"),
            Path.of("../../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        ).first { it.resolve("common/card_registry.json").toFile().exists() }
        return EditionRepository(FileEditionFileSource(dataDir)).load(EditionIds.UK)
    }
}
