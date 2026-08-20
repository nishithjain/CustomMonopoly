package com.boardbanker.app.scanner

import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.scanner.ScanGate
import com.boardbanker.core.scanner.ScanProcessor
import com.boardbanker.core.scanner.ScanProcessorResult
import com.boardbanker.core.validation.GameDefinitionLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.readText

class FakeQrCodeSourceTest {
    @Test
    fun fakePayloadFlowResolvesCard() {
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
        )
        val processor = ScanProcessor(ScanGate(), DefaultCardResolver(definitions))
        val source = FakeQrCodeSource(
            listOf(
                FakeQrEvent.Detected("MUB:P:01"),
                FakeQrEvent.Detected("MUB:P:01"),
            ),
        )
        source.start()
        val first = processor.onQrPayload("MUB:P:01")
        val second = processor.onQrPayload("MUB:P:01")
        assertTrue(first is ScanProcessorResult.CardResolved)
        assertTrue(second is ScanProcessorResult.Ignored)
        val resolved = first as ScanProcessorResult.CardResolved
        assertEquals("PRP_01", resolved.resolution.cardId)
    }
}
