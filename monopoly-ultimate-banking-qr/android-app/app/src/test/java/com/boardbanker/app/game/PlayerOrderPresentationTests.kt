package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.screens.history.HistoryDetail
import com.boardbanker.app.ui.screens.history.TransactionHistoryEntries
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerOrderPresentationTests {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.UK)
    private val engine = DefaultGameEngine(definitions)
    private val serializer = KotlinGameSessionSerializer()

    private val scanOrder = listOf("USR_03", "USR_04", "USR_02", "USR_01")
    private val scanNames = mapOf(
        "USR_03" to "Player A",
        "USR_04" to "Player B",
        "USR_02" to "Player C",
        "USR_01" to "Player D",
    )

    @Test
    fun activeGameDashboardShowsPlayersInScanOrder() {
        val session = startGame(scanOrder)
        val dashboard = ActiveGamePresentation.buildPlayerDashboard(session, definitions)
        assertEquals(scanOrder, dashboard.map { it.playerId })
        assertEquals("Player A", dashboard.first().playerName)
        assertTrue(dashboard.first().isActiveTurn)
    }

    @Test
    fun dashboardOrderDoesNotChangeWhenActiveTurnChanges() {
        var session = startGame(scanOrder)
        val before = ActiveGamePresentation.buildPlayerDashboard(session, definitions).map { it.playerId }
        session = engine.process(session, GameCommand.EndTurn("USR_03")).session
        val dashboard = ActiveGamePresentation.buildPlayerDashboard(session, definitions)
        assertEquals(before, dashboard.map { it.playerId })
        assertEquals("USR_04", dashboard.single { it.isActiveTurn }.playerId)
    }

    @Test
    fun recentBankingShowsCorrectNextPlayerAfterEndTurn() {
        var session = startGame(scanOrder)
        session = engine.process(session, GameCommand.EndTurn("USR_03")).session

        val advanceTx = session.transactions.last { it.transactionType == TransactionType.TURN_ADVANCED }
        assertEquals("USR_03", advanceTx.fromEntity)
        assertEquals("USR_04", advanceTx.toEntity)

        val historyEntries = TransactionHistoryEntries.build(session, definitions)
        val turnEndedEntries = historyEntries.filter { it.title == TransactionHistoryEntries.TURN_ENDED_TITLE }
        val nextTurnEntries = historyEntries.filter { it.title == "Next turn" }
        assertTrue(turnEndedEntries.isNotEmpty())
        assertTrue(nextTurnEntries.isNotEmpty())
        val turnEnded = turnEndedEntries.first().detail as HistoryDetail.PlayerMention
        assertEquals("USR_03", turnEnded.playerId)
        assertEquals("Player A", turnEnded.playerName)
        assertTrue(
            nextTurnEntries.any { entry ->
                when (val detail = entry.detail) {
                    is HistoryDetail.PlayerMention ->
                        detail.playerId == "USR_04" && detail.playerName == "Player B"
                    is HistoryDetail.Text -> detail.value.contains("Player B")
                    else -> false
                }
            },
        )
    }

    @Test
    fun resumePreservesDashboardOrder() {
        val session = startGame(scanOrder)
        val restored = serializer.deserialize(serializer.serialize(session))
        val dashboard = ActiveGamePresentation.buildPlayerDashboard(restored, definitions)
        assertEquals(scanOrder, dashboard.map { it.playerId })
        assertEquals("Player A", dashboard.first().playerName)
        assertFalse(dashboard[1].isActiveTurn)
    }

    private fun startGame(playerIds: List<String>): com.boardbanker.core.model.GameSession {
        var result = engine.process(
            com.boardbanker.core.model.GameSession(
                gameId = "SCAN_ORDER_UI",
                editionId = EditionIds.UK,
            ),
            GameCommand.CreateGame("SCAN_ORDER_UI"),
        )
        for (playerId in playerIds) {
            result = engine.process(
                result.session,
                GameCommand.RegisterPlayer(playerId, scanNames[playerId]!!),
            )
        }
        return engine.process(result.session, GameCommand.StartGame).session
    }
}
