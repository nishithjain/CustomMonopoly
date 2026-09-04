package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.rules.JailGameplayGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JailedPlayerGameplayTest {
    private lateinit var indiaEngine: DefaultGameEngine
    private val engine get() = TestFixtures.engine
    private val indiaDefinitions get() = TestFixtures.loadEdition(EditionIds.INDIA)
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        indiaEngine = DefaultGameEngine(indiaDefinitions)
    }

    @Test
    fun jailedActivePlayer_propertyLandingRejected() {
        val session = jailedActivePlayerSession("USR_02")
        val result = indiaEngine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertNull(result.session.properties["PRP_01"]!!.ownerPlayerId)
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun jailedActivePlayer_directPurchaseRejected() {
        val session = jailedActivePlayerSession("USR_02")
        val balanceBefore = session.players["USR_02"]!!.balance
        val result = indiaEngine.process(
            session,
            GameCommand.PurchaseProperty("USR_02", "PRP_01"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertEquals(balanceBefore, result.session.players["USR_02"]!!.balance)
        assertNull(result.session.properties["PRP_01"]!!.ownerPlayerId)
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.PROPERTY_PURCHASE })
    }

    @Test
    fun jailedActivePlayer_eventApplicationRejected() {
        val session = jailedActivePlayerSession("USR_02", balances = mapOf("USR_02" to 50000))
        val balanceBefore = session.players["USR_02"]!!.balance
        val result = indiaEngine.process(
            session,
            GameCommand.ApplyEvent("EVT_05", "USR_02"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertEquals(balanceBefore, result.session.players["USR_02"]!!.balance)
        assertTrue(result.session.players["USR_02"]!!.jailStatus)
    }

    @Test
    fun jailedActivePlayer_visitorRentRejected() {
        var session = jailedActivePlayerSession("USR_02")
        val property = session.properties["PRP_01"]!!
        session = session.copy(
            properties = session.properties + (
                "PRP_01" to property.copy(ownerPlayerId = "USR_01", currentRentLevel = 1)
            ),
        )
        val visitorBalance = session.players["USR_02"]!!.balance
        val ownerBalance = session.players["USR_01"]!!.balance
        val result = indiaEngine.process(
            session,
            GameCommand.ProcessPropertyLanding("USR_02", "PRP_01"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertEquals(visitorBalance, result.session.players["USR_02"]!!.balance)
        assertEquals(ownerBalance, result.session.players["USR_01"]!!.balance)
    }

    @Test
    fun jailedActivePlayer_startAuctionRejected() {
        val session = jailedActivePlayerSession("USR_02")
        val result = indiaEngine.process(
            session,
            GameCommand.StartAuction(propertyId = "PRP_01", startedByPlayerId = "USR_02"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        assertNull(result.session.auction)
    }

    @Test
    fun successfulJailRelease_allowsPropertyPurchase() {
        var session = jailedActivePlayerSession("USR_02")
        val balanceBeforeRelease = session.players["USR_02"]!!.balance
        val fee = indiaDefinitions.bankingValues.jailReleaseFee
        val released = indiaEngine.process(session, GameCommand.PayJailFee("USR_02"))
        assertFalse(released.session.players["USR_02"]!!.jailStatus)
        assertEquals(balanceBeforeRelease - fee, released.session.players["USR_02"]!!.balance)
        assertTrue(released.transactions.any { it.transactionType == TransactionType.JAIL_STATUS_CHANGE })

        session = released.session
        val purchase = indiaEngine.process(session, GameCommand.PurchaseProperty("USR_02", "PRP_01"))
        assertEquals(GameOutcome.SUCCESS, purchase.outcome)
        assertEquals("USR_02", purchase.session.properties["PRP_01"]!!.ownerPlayerId)
        assertTrue(purchase.transactions.any { it.transactionType == TransactionType.PROPERTY_PURCHASE })
    }

    @Test
    fun failedJailReleaseDebt_keepsPlayerJailed() {
        val session = jailedActivePlayerSession("USR_02", balances = mapOf("USR_02" to 0))
        val releaseAttempt = indiaEngine.process(session, GameCommand.PayJailFee("USR_02"))
        assertEquals(GameOutcome.DEBT_RESOLUTION_REQUIRED, releaseAttempt.outcome)
        assertTrue(releaseAttempt.session.players["USR_02"]!!.jailStatus)
        assertTrue(releaseAttempt.session.debtResolution != null)

        val blockedPurchase = indiaEngine.process(
            releaseAttempt.session,
            GameCommand.PurchaseProperty("USR_02", "PRP_01"),
        )
        assertEquals(GameOutcome.REJECTED, blockedPurchase.outcome)
    }

    @Test
    fun bothPlayersJailed_eachRemainsJailedIndependently() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_02")).session
        assertTrue(session.players["USR_01"]!!.jailStatus)
        assertTrue(session.players["USR_02"]!!.jailStatus)

        session = TestFixtures.endTurn(session, "USR_01", indiaEngine).session
        assertEquals("USR_02", session.turnState!!.activePlayerId)
        assertTrue(session.players["USR_01"]!!.jailStatus)
        assertTrue(session.players["USR_02"]!!.jailStatus)

        val blocked = indiaEngine.process(session, GameCommand.PurchaseProperty("USR_02", "PRP_01"))
        assertEquals(GameOutcome.REJECTED, blocked.outcome)
    }

    @Test
    fun sendingOnePlayerToJail_doesNotAffectOtherJailedState() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        assertTrue(session.players["USR_01"]!!.jailStatus)
        assertFalse(session.players["USR_02"]!!.jailStatus)

        session = indiaEngine.process(session, GameCommand.SendPlayerToJail("USR_02")).session
        assertTrue(session.players["USR_01"]!!.jailStatus)
        assertTrue(session.players["USR_02"]!!.jailStatus)
    }

    @Test
    fun endTurn_doesNotClearJailedStatus() {
        var session = jailedActivePlayerSession("USR_01")
        session = TestFixtures.endTurn(session, "USR_01", indiaEngine).session
        assertTrue(session.players["USR_01"]!!.jailStatus)
        assertEquals("USR_02", session.turnState!!.activePlayerId)
        assertTrue(session.players["USR_02"]!!.jailStatus.not())
    }

    @Test
    fun sendToJail_clearsPendingEventExecutionForPlayer() {
        var session = TestFixtures.newGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_01", "USR_01")).session
        assertTrue(session.pendingEventExecution != null)

        session = engine.process(session, GameCommand.SendPlayerToJail("USR_01")).session
        assertNull(session.pendingEventExecution)
        assertTrue(session.players["USR_01"]!!.jailStatus)
    }

    @Test
    fun saveRestore_preservesJailedStatusAndActivePlayer() {
        val session = jailedActivePlayerSession("USR_02")
        val restored = serializer.deserialize(serializer.serialize(session))
        assertEquals(session, restored)
        assertTrue(restored.players["USR_02"]!!.jailStatus)
        assertEquals("USR_02", restored.turnState!!.activePlayerId)
    }

    @Test
    fun nonJailedActivePlayer_canPurchaseNormally() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = TestFixtures.endTurn(session, "USR_01", indiaEngine).session
        assertEquals("USR_02", session.turnState!!.activePlayerId)
        assertFalse(session.players["USR_02"]!!.jailStatus)

        val result = indiaEngine.process(session, GameCommand.PurchaseProperty("USR_02", "PRP_01"))
        assertEquals(GameOutcome.SUCCESS, result.outcome)
        assertEquals("USR_02", result.session.properties["PRP_01"]!!.ownerPlayerId)
    }

    @Test
    fun directPurchaseRejected_usesPurchaseSpecificMessage() {
        val session = jailedActivePlayerSession("USR_02")
        val result = indiaEngine.process(
            session,
            GameCommand.PurchaseProperty("USR_02", "PRP_01"),
        )
        assertEquals(GameOutcome.REJECTED, result.outcome)
        val errorMessage = (result.error as? com.boardbanker.core.error.GameError.Validation)?.message
        assertTrue(errorMessage?.contains("must get out before purchasing a property") == true)
    }

    @Test
    fun jailGuardMessage_usesPlayerDisplayName() {
        val session = jailedActivePlayerSession("USR_02")
        val expectedName = indiaDefinitions.players["USR_02"]!!.displayName
        val message = JailGameplayGuard.boardActionBlockedMessage(indiaDefinitions, session, "USR_02")
        assertEquals(
            "$expectedName is in Jail and must complete the jail-release action before continuing.",
            message,
        )
    }

    private fun jailedActivePlayerSession(
        activePlayerId: String,
        balances: Map<String, Int>? = null,
    ) = TestFixtures.newGameForEdition(EditionIds.INDIA).let { base ->
        var session = if (balances != null) {
            base.copy(
                players = base.players.mapValues { (id, player) ->
                    balances[id]?.let { player.copy(balance = it) } ?: player
                },
            )
        } else {
            base
        }
        if (activePlayerId == "USR_02") {
            session = TestFixtures.endTurn(session, "USR_01", indiaEngine).session
        }
        session = indiaEngine.process(session, GameCommand.SendPlayerToJail(activePlayerId)).session
        session
    }
}
