package com.boardbanker.app.gameplay

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.gameplay.presentation.GameplayResultMapper
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.ui.screens.history.HistoryDetail
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.DirectJailEventSnapshot
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.JailStatusSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02")).session

        val entries = TransactionHistoryEntries.build(session, indiaDefinitions)
        val jailEntry = entries.first { it.title == "Traffic Court" }
        val jailDetail = jailEntry.detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_02", (jailDetail.from as DisplayIdentity.Player).playerId)
        assertEquals(DisplayIdentity.Jail, jailDetail.to)
        assertEquals(DirectJailEventSnapshot.SUBTITLE, jailEntry.subtitle)
        assertEquals(CommonUiIcon.EVENT_CARD, jailEntry.entryIcon)
        assertEquals("", jailDetail.amount)
        assertEquals(DisplayIdentity.Jail, jailDetail.to)

        assertEquals("USR_01", session.turnState!!.activePlayerId)
        val nextTurn = entries.first {
            it.title == "Next turn" &&
                (it.detail as HistoryDetail.PlayerMention).playerId == "USR_01"
        }
        val nextDetail = nextTurn.detail as HistoryDetail.PlayerMention
        assertEquals("USR_01", nextDetail.playerId)
        assertEquals(CommonUiIcon.CURRENT_TURN, nextTurn.entryIcon)
    }

    @Test
    fun trafficCourtHistoryUsesStoredAffectedPlayerAfterTurnChanges() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02")).session

        assertEquals("USR_01", session.turnState!!.activePlayerId)
        val jailDetail = TransactionHistoryEntries.build(session, indiaDefinitions)
            .first { it.title == "Traffic Court" }
            .detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_02", (jailDetail.from as DisplayIdentity.Player).playerId)
        assertNotEquals(session.turnState!!.activePlayerId, (jailDetail.from as DisplayIdentity.Player).playerId)
    }

    @Test
    fun trafficCourtHistoryWorksThroughLuckyDraw() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_15", "USR_02")).session
        session = indiaEngine.process(session, GameCommand.ResolvePendingEventDraw("EVT_12", "USR_02")).session

        val entries = TransactionHistoryEntries.build(session, indiaDefinitions)
        val trafficCourtEntries = entries.filter { it.title == "Traffic Court" }
        val nextTurnEntries = entries.filter {
            it.title == "Next turn" &&
                (it.detail as? HistoryDetail.PlayerMention)?.playerId == "USR_01"
        }

        assertEquals(1, trafficCourtEntries.size)
        assertEquals(1, nextTurnEntries.size)
        assertEquals(DirectJailEventSnapshot.SUBTITLE, trafficCourtEntries.single().subtitle)
    }

    @Test
    fun trafficCourtHistoryDoesNotDuplicateEntries() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02")).session

        val entries = TransactionHistoryEntries.build(session, indiaDefinitions)

        assertEquals(1, entries.count { it.title == "Traffic Court" })
        assertEquals(1, entries.count { it.title == "Next turn" })
        assertEquals(0, entries.count { it.title == "Jail" })
        assertEquals(0, entries.count { it.title.startsWith("Event:") })
    }

    @Test
    fun trafficCourtUndoAndResumePreserveHistoryEntries() {
        var session = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        session = AppTestSupport.sessionWithActivePlayer(session, "USR_02")
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02")).session
        val serializer = KotlinGameSessionSerializer()
        session = serializer.deserialize(serializer.serialize(session))

        val beforeUndo = TransactionHistoryEntries.build(session, indiaDefinitions)
        session = indiaEngine.process(session, GameCommand.UndoLastAction).session
        session = indiaEngine.process(session, GameCommand.ApplyEvent("EVT_12", "USR_02")).session
        session = serializer.deserialize(serializer.serialize(session))

        val afterRedo = TransactionHistoryEntries.build(session, indiaDefinitions)
        val jailEntry = afterRedo.first { it.title == "Traffic Court" }
        val jailDetail = jailEntry.detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_02", (jailDetail.from as DisplayIdentity.Player).playerId)
        assertEquals(DirectJailEventSnapshot.SUBTITLE, jailEntry.subtitle)
        assertFalse(jailEntry.undone)
        val latestNextTurn = afterRedo.first { it.title == "Next turn" }.detail as HistoryDetail.PlayerMention
        assertEquals("USR_01", latestNextTurn.playerId)
        assertFalse(afterRedo.first { it.title == "Next turn" }.undone)
        assertTrue(beforeUndo.any { it.title == "Traffic Court" })
        assertTrue(afterRedo.any { it.title == "Traffic Court" && it.undone })
    }

    @Test
    fun legacyTrafficCourtHistoryWithoutMetadataStillDisplays() {
        val base = AppTestSupport.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
        val session = base.copy(
            transactions = listOf(
                Transaction(
                    transactionId = "${base.gameId}_TX_1",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.JAIL_STATUS_CHANGE,
                    playerId = "USR_02",
                    stateBefore = JailStatusSnapshot.stateBefore(false),
                    stateAfter = JailStatusSnapshot.stateAfter(true),
                ),
                Transaction(
                    transactionId = "${base.gameId}_TX_2",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.TURN_ADVANCED,
                    fromEntity = "USR_02",
                    toEntity = "USR_01",
                    playerId = "USR_01",
                ),
                Transaction(
                    transactionId = "${base.gameId}_TX_3",
                    gameId = base.gameId,
                    timestamp = 1_000L,
                    transactionType = TransactionType.EVENT_APPLIED,
                    eventId = "EVT_12",
                    playerId = "USR_02",
                ),
            ),
        )

        val entries = TransactionHistoryEntries.build(session, indiaDefinitions)
        val jailEntry = entries.first { it.title == "Traffic Court" }
        val jailDetail = jailEntry.detail as HistoryDetail.PlayerTransfer

        assertEquals("USR_02", (jailDetail.from as DisplayIdentity.Player).playerId)
        assertEquals(DisplayIdentity.Jail, jailDetail.to)
        assertEquals(DirectJailEventSnapshot.SUBTITLE, jailEntry.subtitle)
        assertEquals("USR_01", (entries.first { it.title == "Next turn" }.detail as HistoryDetail.PlayerMention).playerId)
    }
}
