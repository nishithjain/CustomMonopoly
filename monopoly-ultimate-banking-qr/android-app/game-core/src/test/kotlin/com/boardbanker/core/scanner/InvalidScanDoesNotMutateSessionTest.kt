package com.boardbanker.core.scanner

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvalidScanDoesNotMutateSessionTest {
    private val definitions = TestFixtures.definitions
    private val engine = DefaultGameEngine(definitions)
    private val processor = ScanProcessor(
        scanGate = ScanGate(),
        cardResolver = DefaultCardResolver(definitions),
    )

    private fun startedSession(): GameSession {
        var result = engine.process(
            GameSession(gameId = "SCAN_TEST", editionId = definitions.editionId),
            GameCommand.CreateGame("SCAN_TEST"),
        )
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = engine.process(result.session, GameCommand.StartGame)
        return result.session
    }

    @Test
    fun unknownQrScan_doesNotMutateSession() {
        val before = startedSession()
        val scan = processor.onQrPayload("https://example.com/not-a-game-card")
        assertTrue(scan is ScanProcessorResult.UnknownCard)
        assertEquals(before, before.copy())
    }

    @Test
    fun ignoredDuplicateScan_doesNotMutateSession() {
        val before = startedSession()
        processor.onQrPayload("MUB:P:01")
        val duplicate = processor.onQrPayload("MUB:P:01")
        assertTrue(duplicate is ScanProcessorResult.Ignored)
        assertEquals(before.players, before.players)
        assertEquals(before.properties, before.properties)
    }
}
