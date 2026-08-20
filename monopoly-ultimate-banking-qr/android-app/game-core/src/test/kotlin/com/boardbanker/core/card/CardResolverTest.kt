package com.boardbanker.core.card

import com.boardbanker.core.model.GameDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardResolverTest {
    private val definitions = com.boardbanker.core.TestFixtures.definitions
    private val resolver = DefaultCardResolver(definitions)

    @Test
    fun usr01ResolvesCorrectly() {
        val result = resolver.resolve("MUB:PL:CAR")
        assertTrue(result is CardResolution.Success)
        val success = result as CardResolution.Success
        assertEquals("USR_01", success.cardId)
        assertEquals(CardType.USER, success.cardType)
        assertEquals("Car", success.displayName)
    }

    @Test
    fun usr04ResolvesCorrectly() {
        val result = resolver.resolve("MUB:PL:AEROPLANE")
        assertTrue(result is CardResolution.Success)
        val success = result as CardResolution.Success
        assertEquals("USR_04", success.cardId)
        assertEquals(CardType.USER, success.cardType)
    }

    @Test
    fun prp01ResolvesCorrectly() {
        val result = resolver.resolve("MUB:P:01")
        assertTrue(result is CardResolution.Success)
        val success = result as CardResolution.Success
        assertEquals("PRP_01", success.cardId)
        assertEquals(CardType.PROPERTY, success.cardType)
        assertEquals("Old Kent Road", success.displayName)
    }

    @Test
    fun prp22ResolvesCorrectly() {
        val result = resolver.resolve("MUB:P:22")
        assertTrue(result is CardResolution.Success)
        val success = result as CardResolution.Success
        assertEquals("PRP_22", success.cardId)
        assertEquals(CardType.PROPERTY, success.cardType)
    }

    @Test
    fun evt01ResolvesCorrectly() {
        val result = resolver.resolve("MUB:E:E01")
        assertTrue(result is CardResolution.Success)
        val success = result as CardResolution.Success
        assertEquals("EVT_01", success.cardId)
        assertEquals(CardType.EVENT, success.cardType)
        assertEquals("Boom Town", success.displayName)
    }

    @Test
    fun evt23ResolvesCorrectly() {
        val result = resolver.resolve("MUB:E:E23")
        assertTrue(result is CardResolution.Success)
        val success = result as CardResolution.Success
        assertEquals("EVT_23", success.cardId)
        assertEquals(CardType.EVENT, success.cardType)
    }

    @Test
    fun unknownQrReturnsUnknownQr() {
        val result = resolver.resolve("https://example.com")
        assertTrue(result is CardResolution.UnknownQr)
    }

    @Test
    fun allQrPayloadsAreUnique() {
        val payloads = definitions.cards.values.map { it.qrPayload }
        assertEquals(49, payloads.size)
        assertEquals(49, payloads.toSet().size)
    }

    @Test
    fun allRegisteredCardsResolveFromRegistry() {
        var resolved = 0
        var unknown = 0
        var mismatches = 0
        for (card in definitions.cards.values.sortedBy { it.cardId }) {
            when (val result = resolver.resolve(card.qrPayload)) {
                is CardResolution.Success -> {
                    resolved++
                    if (result.cardId != card.cardId ||
                        result.cardType != card.cardType ||
                        result.displayName != card.name
                    ) {
                        mismatches++
                    }
                }
                is CardResolution.UnknownQr -> unknown++
            }
        }
        assertEquals(49, resolved)
        assertEquals(0, unknown)
        assertEquals(0, mismatches)
    }

    @Test
    fun prp01RentValuesMatchMasterData() {
        val property = definitions.properties["PRP_01"]!!
        assertEquals(60, property.purchasePrice)
        assertEquals(listOf(70, 130, 220, 370, 750), property.rentLevels.map { it.amount })
    }

    @Test
    fun prp22RentValuesMatchMasterData() {
        val property = definitions.properties["PRP_22"]!!
        assertEquals(400, property.purchasePrice)
        assertEquals(listOf(300, 400, 560, 810, 1600), property.rentLevels.map { it.amount })
    }
}
