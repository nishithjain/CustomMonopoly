package com.boardbanker.app.scanner.ui

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedCardSummaryPresentationTest {
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    @Test
    fun resolvesPropertyEventAndEnergyGridNamesFromDefinitions() {
        val property = ResolvedCard(
            cardId = "PRP_02",
            cardType = CardType.PROPERTY,
            displayName = "fallback",
            qrPayload = "payload",
        )
        val event = ResolvedCard(
            cardId = "EVT_13",
            cardType = CardType.EVENT,
            displayName = "fallback",
            qrPayload = "payload",
        )
        val energyGrid = ResolvedCard(
            cardId = "ENG_01",
            cardType = CardType.ENERGY_GRID,
            displayName = "fallback",
            qrPayload = "payload",
        )

        assertEquals("[2] Lodhi Garden", RecognizedCardSummaryPresentation.displayName(property, indiaDefinitions))
        assertEquals("Local Market Boom", RecognizedCardSummaryPresentation.displayName(event, indiaDefinitions))
        assertEquals("[15] Solar Energy", RecognizedCardSummaryPresentation.displayName(energyGrid, indiaDefinitions))
    }

    @Test
    fun typeLabelsAreReadable() {
        assertEquals("Event", RecognizedCardSummaryPresentation.typeLabel(CardType.EVENT))
        assertEquals("Property", RecognizedCardSummaryPresentation.typeLabel(CardType.PROPERTY))
        assertEquals("Energy Grid", RecognizedCardSummaryPresentation.typeLabel(CardType.ENERGY_GRID))
        assertFalse(RecognizedCardSummaryPresentation.typeLabel(CardType.EVENT).contains("EVENT"))
    }

    @Test
    fun gameCardSummaryIncludesOnlyPropertyEventAndEnergyGrid() {
        assertTrue(RecognizedCardSummaryPresentation.isGameCardSummary(CardType.PROPERTY))
        assertTrue(RecognizedCardSummaryPresentation.isGameCardSummary(CardType.EVENT))
        assertTrue(RecognizedCardSummaryPresentation.isGameCardSummary(CardType.ENERGY_GRID))
        assertFalse(RecognizedCardSummaryPresentation.isGameCardSummary(CardType.USER))
    }

    @Test
    fun propertyColorGroupResolvedFromDefinitions() {
        assertEquals("BROWN", RecognizedCardSummaryPresentation.propertyColorGroup("PRP_02", indiaDefinitions))
        assertEquals(null, RecognizedCardSummaryPresentation.propertyColorGroup("PRP_99", indiaDefinitions))
    }
}
