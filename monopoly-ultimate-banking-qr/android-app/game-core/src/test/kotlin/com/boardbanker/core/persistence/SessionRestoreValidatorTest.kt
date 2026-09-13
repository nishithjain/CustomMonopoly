package com.boardbanker.core.persistence

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.DebtReason
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EntityRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRestoreValidatorTest {
    private val definitions = TestFixtures.loadEdition(EditionIds.INDIA)
    private val validator = SessionRestoreValidator(definitions)
    private val engine = DefaultGameEngine(definitions)

    @Test
    fun bankCreditorDebtPassesValidation() {
        val session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            mapOf(
                "USR_01" to 3000,
                "USR_02" to 25000,
                "USR_03" to 25000,
                "USR_04" to 25000,
            ),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_02"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(DebtReason.EVENT_CONTRIBUTOR, result.session.debtResolution!!.reason)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)
        assertTrue(validator.validate(result.session).isEmpty())
    }

    @Test
    fun municipalMaintenanceDebtWithBankCreditorPassesValidation() {
        var session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
            mapOf("USR_01" to 1000, "USR_02" to 25000),
        )
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to session.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
            ),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)
        assertNotNull(result.session.debtResolution!!.eventBankDebit)
        assertTrue(validator.validate(result.session).isEmpty())
    }

    @Test
    fun hospitalExpenseDebtWithBankCreditorPassesValidation() {
        val result = engine.process(
            TestFixtures.newGameForEdition(
                EditionIds.INDIA,
                listOf("USR_01", "USR_02"),
                mapOf("USR_01" to 3000, "USR_02" to 25000),
            ),
            GameCommand.ApplyEvent("EVT_05", "USR_01"),
        )

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertNotNull(result.session.debtResolution!!.eventBankDebit)
        assertTrue(validator.validate(result.session).isEmpty())
    }

    @Test
    fun payJailFeeDebtWithBankCreditorPassesValidation() {
        val session = TestFixtures.sessionWithJail("USR_01").copy(
            players = TestFixtures.sessionWithJail("USR_01").players.mapValues { (id, player) ->
                if (id == "USR_01") player.copy(balance = 1000) else player
            },
        )
        val result = engine.process(session, GameCommand.PayJailFee("USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(DebtReason.JAIL, result.session.debtResolution!!.reason)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)
        assertEquals(10000, result.session.debtResolution!!.originalAmountDue)
        assertTrue(result.session.players["USR_01"]!!.jailStatus)
        assertTrue(validator.validate(result.session).isEmpty())
    }

    @Test
    fun cloudStorageDebtWithBankCreditorPassesValidation() {
        val session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02"),
            mapOf("USR_01" to 1000, "USR_02" to 25000),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_22", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)
        assertTrue(validator.validate(result.session).isEmpty())
    }

    @Test
    fun festivalEventDebtWithBankCreditorPassesValidation() {
        val session = TestFixtures.newGameForEdition(
            EditionIds.INDIA,
            listOf("USR_01", "USR_02", "USR_03", "USR_04"),
            mapOf(
                "USR_01" to 4000,
                "USR_02" to 10000,
                "USR_03" to 10000,
                "USR_04" to 10000,
            ),
        )
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))

        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, result.outcome)
        assertEquals(EntityRef.BANK, result.session.debtResolution!!.creditorPlayerId)
        assertTrue(validator.validate(result.session).isEmpty())
    }
}
