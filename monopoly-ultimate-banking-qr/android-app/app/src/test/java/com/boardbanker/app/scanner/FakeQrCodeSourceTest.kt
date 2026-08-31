package com.boardbanker.app.scanner

import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.scanner.ScanGate
import com.boardbanker.core.scanner.ScanProcessor
import com.boardbanker.core.scanner.ScanProcessorResult
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class FakeQrCodeSourceTest {
    @Test
    fun fakePayloadFlowResolvesCard() {
        val dataDir = listOf(
            Path.of("../../data"),
            Path.of("../../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        ).first { it.resolve("common/card_registry.json").toFile().exists() }
        val definitions = EditionRepository(FileEditionFileSource(dataDir)).load(EditionIds.UK)
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
