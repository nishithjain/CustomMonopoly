package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.dice.SequenceDiceRoller
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.DiceGambleMode
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.LuckyBreakEventSnapshot
import com.boardbanker.core.model.PhysicalDiceGambleOutcome
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuckyBreakHistoryTests {
    private val definitions = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    private fun engineWithRolls(vararg rolls: Pair<Int, Int>) =
        DefaultGameEngine(definitions, SequenceDiceRoller(*rolls))

    private fun startInApp(engine: DefaultGameEngine) =
        AppTestSupport.selectDiceGambleMode(
            engine.process(
                AppTestSupport.newGameForEdition(EditionIds.INDIA).let { session ->
                    session.copy(
                        players = session.players.mapValues { (id, player) ->
                            if (id == "USR_01") player.copy(balance = 50000) else player
                        },
                    )
                },
                GameCommand.ApplyEvent("EVT_17", "USR_01"),
            ).session,
            DiceGambleMode.IN_APP,
            engine,
        )

    @Test
    fun inAppJackpotHistoryShowsModeOutcomePlayerAndBankTransfer() {
        val engine = engineWithRolls(4 to 4)
        val session = engine.process(
            startInApp(engine),
            GameCommand.RollEventDice("EVT_17", "USR_01"),
        ).session

        val entry = TransactionHistoryEntries.build(session, definitions)
            .first { it.title == "Lucky Break — Jackpot" }
        val detail = entry.detail as HistoryDetail.LuckyBreakResolution

        assertTrue(entry.subtitle!!.contains("In-app dice: 4 + 4"))
        assertTrue(entry.subtitle!!.contains("Attempt 1 of 3"))
        assertEquals(DisplayIdentity.Bank, detail.transfer.from)
        assertTrue(detail.transfer.to is DisplayIdentity.Player)
        assertEquals("USR_01", (detail.transfer.to as DisplayIdentity.Player).playerId)
        assertTrue(detail.transfer.amount.contains("15"))
    }

    @Test
    fun inAppPenaltyHistoryShowsFinalRollAndAttempt() {
        val engine = engineWithRolls(1 to 2, 2 to 3, 4 to 5)
        var session = startInApp(engine)
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01")).session

        val entry = TransactionHistoryEntries.build(session, definitions)
            .first { it.title == "Lucky Break — Penalty" }

        assertTrue(entry.subtitle!!.contains("Final roll: 4 + 5"))
        assertTrue(entry.subtitle!!.contains("Attempt 3 of 3"))
        val detail = entry.detail as HistoryDetail.LuckyBreakResolution
        assertEquals("USR_01", (detail.transfer.from as DisplayIdentity.Player).playerId)
        assertEquals(DisplayIdentity.Bank, detail.transfer.to)
    }

    @Test
    fun physicalJackpotHistoryDoesNotInventDiceValues() {
        val engine = engineWithRolls()
        var session = AppTestSupport.selectDiceGambleMode(
            engine.process(
                AppTestSupport.newGameForEdition(EditionIds.INDIA),
                GameCommand.ApplyEvent("EVT_17", "USR_01"),
            ).session,
            DiceGambleMode.PHYSICAL,
            engine,
        )
        session = engine.process(
            session,
            GameCommand.ResolvePhysicalDiceGamble("EVT_17", "USR_01", PhysicalDiceGambleOutcome.JACKPOT),
        ).session

        val entry = TransactionHistoryEntries.build(session, definitions)
            .first { it.title == "Lucky Break — Jackpot" }

        assertEquals("Physical dice • Doubles confirmed", entry.subtitle)
        assertTrue(!entry.subtitle!!.contains("+"))
    }

    @Test
    fun resumePreservesSingleLuckyBreakHistoryEntry() {
        val engine = engineWithRolls(6 to 6)
        var session = engine.process(
            startInApp(engine),
            GameCommand.RollEventDice("EVT_17", "USR_01"),
        ).session
        val serializer = KotlinGameSessionSerializer()
        session = serializer.deserialize(serializer.serialize(session))

        val entries = TransactionHistoryEntries.build(session, definitions)
            .filter { it.title.startsWith("Lucky Break") }
        assertEquals(1, entries.size)
        val metadata = LuckyBreakEventSnapshot.fromTransaction(
            session.transactions.single { LuckyBreakEventSnapshot.isLuckyBreakResolution(it) },
        )
        assertEquals("USR_01", metadata!!.playerId)
    }
}
