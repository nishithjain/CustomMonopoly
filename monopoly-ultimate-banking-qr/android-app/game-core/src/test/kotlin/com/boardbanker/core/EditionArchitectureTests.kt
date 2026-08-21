package com.boardbanker.core

import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.CurrencyDefinition
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.money.MoneyFormatter
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionArchitectureTests {
    @Test
    fun defaultEditionIsUk() {
        assertEquals(EditionIds.UK, TestFixtures.definitions.editionId)
        assertEquals("Old Kent Road", TestFixtures.definitions.properties["PRP_01"]!!.name)
        assertEquals("Mayfair", TestFixtures.definitions.properties["PRP_22"]!!.name)
        assertEquals(60, TestFixtures.definitions.properties["PRP_01"]!!.purchasePrice)
        assertEquals(1500, TestFixtures.definitions.bankingValues.startingBalance)
        val session = TestFixtures.newGame()
        assertEquals(EditionIds.UK, session.editionId)
    }

    @Test
    fun indiaEditionResolvesCubbonParkAndDoesNotChangeUk() {
        val india = TestFixtures.loadEdition(EditionIds.INDIA)
        assertEquals("Cubbon Park", india.properties["PRP_01"]!!.name)
        assertEquals("Taj Mohalla", india.properties["PRP_22"]!!.name)
        assertEquals(6000, india.properties["PRP_01"]!!.purchasePrice)
        assertEquals(150000, india.bankingValues.startingBalance)
        assertEquals("Old Kent Road", TestFixtures.definitions.properties["PRP_01"]!!.name)
        assertEquals("Cubbon Park", india.cards["PRP_01"]!!.name)
    }

    @Test
    fun moneyFormatterIsEditionAware() {
        val uk = TestFixtures.definitions.bankingValues.currency
        val india = CurrencyDefinition(code = "INR", symbol = "₹", scale = 100)
        assertEquals("M1500", MoneyFormatter.format(1500, uk))
        assertEquals("M200", MoneyFormatter.format(200, uk))
        assertEquals("₹150,000", MoneyFormatter.format(150000, india))
        assertEquals("₹20,000", MoneyFormatter.format(20000, india))
    }

    @Test
    fun missingEditionIdFallsBackToUk() {
        val serializer = KotlinGameSessionSerializer()
        val original = TestFixtures.newGame()
        val json = serializer.serialize(original).replace("\"editionId\":\"uk\",", "")
        assertFalse(json.contains("editionId"))
        val restored = serializer.deserialize(json)
        assertEquals(EditionIds.UK, restored.editionId)
    }

    @Test
    fun indiaSessionUsesIndiaEngineNamesNotUk() {
        val india = TestFixtures.loadEdition(EditionIds.INDIA)
        val engine = DefaultGameEngine(india)
        val session = engine.process(
            com.boardbanker.core.model.GameSession(gameId = "INDIA_TEST", editionId = EditionIds.INDIA),
            com.boardbanker.core.command.GameCommand.CreateGame("INDIA_TEST"),
        ).session
        assertEquals(EditionIds.INDIA, session.editionId)
        assertEquals("Cubbon Park", india.properties["PRP_01"]!!.name)
        assertTrue(india.events.size == 23)
    }
}
