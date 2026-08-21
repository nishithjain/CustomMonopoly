package com.boardbanker.app.scanner

import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.scanner.ScanGate
import com.boardbanker.core.scanner.ScanProcessor
import com.boardbanker.core.scanner.ScanProcessorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.readText
import com.boardbanker.core.validation.GameDefinitionLoader

class ScannerControllerTest {
    private lateinit var controller: ScannerController

    @Before
    fun setUp() {
        val dataDir = listOf(
            Path.of("../../data"),
            Path.of("../../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        ).first { it.resolve("cards.json").toFile().exists() }
        val loader = GameDefinitionLoader()
        val definitions = loader.loadAll(
            cardsJson = dataDir.resolve("cards.json").readText(),
            propertiesJson = dataDir.resolve("properties.json").readText(),
            eventsJson = dataDir.resolve("events.json").readText(),
            eventEngineRulesJson = dataDir.resolve("event_engine_rules.json").readText(),
            boardRelationshipsJson = dataDir.resolve("board_relationships.json").readText(),
            gameRulesJson = dataDir.resolve("game_rules.json").readText(),
            bankingValuesJson = dataDir.resolve("banking_values.json").readText(),
        )
        controller = ScannerController(definitions)
    }

    @Test
    fun resolvesKnownPropertyCard() {
        val result = controller.onQrPayload("MUB:P:07")
        assertTrue(result is ScanProcessorResult.CardResolved)
        val resolved = result as ScanProcessorResult.CardResolved
        assertEquals("PRP_07", resolved.resolution.cardId)
        assertEquals("Whitehall", resolved.resolution.displayName)
    }

    @Test
    fun unknownQrReturnsUnknownResult() {
        val result = controller.onQrPayload("HELLO")
        assertTrue(result is ScanProcessorResult.UnknownCard)
    }
}
