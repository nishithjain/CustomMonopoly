package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStatusPresentationTest {
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    @Test
    fun buildUsesEditionAwareMoneyFormatter() {
        val session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val uiModel = GameStatusPresentation.build(session, indiaDefinitions)
        val player = uiModel.players.first { it.playerId == "USR_01" }

        assertEquals("₹150,000", player.balanceText)
    }

    @Test
    fun buildCountsPropertiesAndEnergyGrids() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA)
        val engine = com.boardbanker.core.engine.DefaultGameEngine(indiaDefinitions)
        session = engine.process(
            session,
            com.boardbanker.core.command.GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session

        val player = GameStatusPresentation.build(session, indiaDefinitions)
            .players
            .single { it.playerId == "USR_01" }

        assertEquals("1 property", player.propertyCountLabel)
        assertEquals("0 energy grids", player.energyGridCountLabel)
    }

    @Test
    fun resolveStatusPrefersBankruptOverJail() {
        val status = GameStatusPresentation.resolveStatus(
            PlayerState(
                playerId = "USR_01",
                balance = 0,
                bankrupt = true,
                jailStatus = true,
            ),
        )

        assertEquals("Bankrupt", status.label)
    }

    @Test
    fun resolveStatusShowsTurnSkipped() {
        val status = GameStatusPresentation.resolveStatus(
            PlayerState(
                playerId = "USR_01",
                balance = 1000,
                pendingSkipTurnCount = 1,
            ),
        )

        assertEquals("Turn skipped", status.label)
    }
}
