package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.rules.PlayerActiveEventEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveGamePresentationTest {
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val engine = DefaultGameEngine(indiaDefinitions)
    private val serializer = KotlinGameSessionSerializer()

    @Test
    fun buildAssetsSummaryLineUsesSingularAndPluralLabels() {
        assertEquals(
            "1 Property • 1 Energy Grid",
            ActiveGamePresentation.buildAssetsSummaryLine(
                propertyCount = 1,
                energyGridCount = 1,
                hasEnergyGridsInEdition = true,
            ),
        )
        assertEquals(
            "4 Properties • 2 Energy Grids",
            ActiveGamePresentation.buildAssetsSummaryLine(
                propertyCount = 4,
                energyGridCount = 2,
                hasEnergyGridsInEdition = true,
            ),
        )
        assertEquals(
            "2 Properties",
            ActiveGamePresentation.buildAssetsSummaryLine(
                propertyCount = 2,
                energyGridCount = 0,
                hasEnergyGridsInEdition = false,
            ),
        )
    }

    @Test
    fun playerDashboardShowsSingleAppliedEventLine() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_01")).session

        val dashboard = ActiveGamePresentation.buildPlayerDashboard(session, indiaDefinitions)
        val player = dashboard.single { it.playerId == "USR_01" }

        assertEquals(listOf("Event applied: Rent Relief"), player.activeEventLines)
    }

    @Test
    fun playerDashboardOmitsEventsForOtherPlayers() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_01")).session

        val dashboard = ActiveGamePresentation.buildPlayerDashboard(session, indiaDefinitions)
        val other = dashboard.single { it.playerId == "USR_02" }

        assertTrue(other.activeEventLines.isEmpty())
    }

    @Test
    fun playerDashboardShowsMultipleAppliedEventLines() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_01")).session
        session = engine.process(session, GameCommand.ApplyEvent("EVT_24", "USR_01")).session

        val player = ActiveGamePresentation.buildPlayerDashboard(session, indiaDefinitions)
            .single { it.playerId == "USR_01" }

        assertEquals(
            listOf(
                "Events applied:",
                "• Rent Relief",
                "• Second Wind",
            ),
            player.activeEventLines,
        )
    }

    @Test
    fun resumeGameRebuildsActiveEventLines() {
        var session = AppTestSupport.newGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_11", "USR_01")).session
        val restored = serializer.deserialize(serializer.serialize(session))

        val player = ActiveGamePresentation.buildPlayerDashboard(restored, indiaDefinitions)
            .single { it.playerId == "USR_01" }

        assertEquals(
            listOf("Event applied: Get Out of Jail Pass"),
            player.activeEventLines,
        )
        assertEquals(
            listOf("Get Out of Jail Pass"),
            PlayerActiveEventEffects.activeEventNames("USR_01", restored, indiaDefinitions),
        )
    }

    @Test
    fun buildOwnedEnergyGridsUsesEditionDefinitions() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_02")).session

        val owned = ActiveGamePresentation.buildOwnedEnergyGrids(session, "USR_01", indiaDefinitions)

        assertEquals("ENG_02", owned.single().energyGridId)
        assertEquals("Wind Energy", owned.single().energyGridName)
        assertEquals("Board 29", owned.single().boardPositionLabel)
        assertTrue(owned.single().currentRentText.contains("5"))
        assertTrue(owned.single().purchasePriceText.contains("20"))
    }

    @Test
    fun buildOwnedEnergyGridsReturnsEmptyForOtherPlayer() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01")).session

        val owned = ActiveGamePresentation.buildOwnedEnergyGrids(session, "USR_02", indiaDefinitions)

        assertTrue(owned.isEmpty())
    }

    @Test
    fun buildOwnedEnergyGridsReturnsEmptyWhenEditionHasNoGrids() {
        val ukDefinitions = AppTestSupport.editionRepository.load(EditionIds.UK)
        val ukEngine = DefaultGameEngine(ukDefinitions)
        var session = AppTestSupport.newGame()
        session = ukEngine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session

        val owned = ActiveGamePresentation.buildOwnedEnergyGrids(session, "USR_01", ukDefinitions)

        assertTrue(owned.isEmpty())
    }

    @Test
    fun buildOwnedPropertiesFormatsColorGroupLabel() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_03")).session

        val owned = ActiveGamePresentation.buildOwnedProperties(session, "USR_01", indiaDefinitions)

        assertEquals("Light Blue", owned.single().colorGroupLabel)
    }
}
