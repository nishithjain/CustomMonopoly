package com.boardbanker.app.scanner

import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.scanner.ScanProcessorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path

class ScannerControllerTest {
    private lateinit var controller: ScannerController

    @Before
    fun setUp() {
        val dataDir = listOf(
            Path.of("../../data"),
            Path.of("../../../data"),
            Path.of("../../../../monopoly-ultimate-banking-qr/data"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
        ).first { it.resolve("common/card_registry.json").toFile().exists() }
        val definitions = EditionRepository(FileEditionFileSource(dataDir)).load(EditionIds.DEFAULT)
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
