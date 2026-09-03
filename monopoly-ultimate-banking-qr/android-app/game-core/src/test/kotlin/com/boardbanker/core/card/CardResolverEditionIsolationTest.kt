package com.boardbanker.core.card

import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class CardResolverEditionIsolationTest {
    private val dataDir: Path = listOf(
        Path.of("../../data"),
        Path.of("../../../data"),
        Path.of("../../../../monopoly-ultimate-banking-qr/data"),
        Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/data"),
    ).first { it.resolve("common/card_registry.json").toFile().exists() }

    private val repository = EditionRepository(FileEditionFileSource(dataDir))
    private val ukDefinitions = repository.load(EditionIds.UK)
    private val indiaDefinitions = repository.load(EditionIds.INDIA)
    private val ukResolver = DefaultCardResolver(ukDefinitions)
    private val indiaResolver = DefaultCardResolver(indiaDefinitions)

    @Test
    fun sharedPayload_resolvesEditionSpecificPropertyNames() {
        val payload = "MUB:P:14"
        val uk = ukResolver.resolve(payload) as CardResolution.Success
        val india = indiaResolver.resolve(payload) as CardResolution.Success

        assertEquals("PRP_14", uk.cardId)
        assertEquals("PRP_14", india.cardId)
        assertEquals("[14] Trafalgar Square", uk.displayName)
        assertEquals("[14] Mehrangarh Fort", india.displayName)
    }

    @Test
    fun sharedPayload_resolvesEditionSpecificEventNames() {
        val payload = "MUB:E:E01"
        val uk = ukResolver.resolve(payload) as CardResolution.Success
        val india = indiaResolver.resolve(payload) as CardResolution.Success

        assertEquals("EVT_01", uk.cardId)
        assertEquals("EVT_01", india.cardId)
        assertEquals("Boom Town", uk.displayName)
        assertEquals("Advance to GO", india.displayName)
    }

    @Test
    fun indiaOnlyEvent_isUnknownInUk() {
        val payload = "MUB:E:E24"
        val india = indiaResolver.resolve(payload)
        val uk = ukResolver.resolve(payload)

        assertTrue(india is CardResolution.Success)
        assertEquals("EVT_24", (india as CardResolution.Success).cardId)
        assertTrue(uk is CardResolution.UnknownQr)
    }

    @Test
    fun whitespaceNormalization_appliesBeforeLookup() {
        val payload = "  MUB:PL:CAR  "
        val resolved = ukResolver.resolve(payload) as CardResolution.Success
        assertEquals("USR_01", resolved.cardId)
    }

    @Test
    fun emptyPayload_isUnknown() {
        assertTrue(ukResolver.resolve("") is CardResolution.UnknownQr)
        assertTrue(ukResolver.resolve("   ") is CardResolution.UnknownQr)
    }
}
