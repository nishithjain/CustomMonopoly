package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.screens.history.HistoryDetail
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficCourtPresentationTests {
    private val indiaDefinitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)
    private val indiaEngine = DefaultGameEngine(indiaDefinitions)
    private val mapper = GameplayResultMapper(indiaDefinitions)

    @Test
    fun trafficCourtResultShowsJailTransferAndNextTurn() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        val result = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02"))
        val ui = mapper.mapEventResult(result, "EVT_12")

        assertEquals("Traffic Court", ui.title)
        assertEquals("USR_02", ui.primaryPlayerId)
        assertEquals(EntityRef.JAIL, ui.secondaryPlayerId)
        assertEquals("USR_01", ui.nextTurnPlayerId)
        assertTrue(ui.primaryMessage.contains("was sent directly to Jail"))
        assertTrue(ui.primaryMessage.contains("No GO amount was collected"))
        assertTrue(ui.primaryMessage.contains("turn has ended"))
    }

    @Test
    fun trafficCourtHistoryShowsJailMovementAndNextTurn() {
        val session = indiaEngine.process(
            AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02")),
            GameCommand.ApplyEvent("EVT_12", "USR_01"),
        ).session

        val entries = TransactionHistoryEntries.build(session, indiaDefinitions)
        val jailEntry = entries.first { it.title == "Traffic Court" }
        val jailDetail = jailEntry.detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_01", (jailDetail.from as DisplayIdentity.Player).playerId)
        assertEquals(DisplayIdentity.Jail, jailDetail.to)

        assertEquals("USR_02", session.turnState!!.activePlayerId)
        val nextTurn = entries.first {
            it.title == "Next turn" &&
                (it.detail as HistoryDetail.PlayerMention).playerId == "USR_02"
        }
        val nextDetail = nextTurn.detail as HistoryDetail.PlayerMention
        assertEquals("USR_02", nextDetail.playerId)
        assertEquals(CommonUiIcon.CURRENT_TURN, nextTurn.entryIcon)
    }
}
