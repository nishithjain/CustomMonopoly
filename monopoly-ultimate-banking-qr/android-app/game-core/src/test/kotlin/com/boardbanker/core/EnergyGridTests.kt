package com.boardbanker.core

import com.boardbanker.core.card.CardType
import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.rules.BoardTraversal
import com.boardbanker.core.rules.EnergyGridRentCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyGridDefinitionLoadingTest {
    private val repository = EditionRepository(FileEditionFileSource(TestFixtures.dataDir))
    private val india: GameDefinitions = repository.load(EditionIds.INDIA)
    private val uk: GameDefinitions = repository.load(EditionIds.UK)

    @Test
    fun indiaLoadsFourEnergyGridsFromJson() {
        assertEquals(4, india.energyGrids.size)
        assertEquals(4, india.edition!!.cardConfiguration!!.energyGridCardCount)
        val grid = india.energyGrids["ENG_01"]!!
        assertEquals("Solar Energy", grid.name)
        assertEquals("MUB:G:01", grid.qrPayload)
        assertEquals(20000, grid.purchasePrice)
        assertEquals(5000, grid.rentLevels.first { it.ownedCount == 1 }.amount)
        assertEquals(40000, grid.rentLevels.first { it.ownedCount == 4 }.amount)
    }

    @Test
    fun ukLoadsWithZeroEnergyGrids() {
        assertTrue(uk.energyGrids.isEmpty())
        assertEquals(0, uk.edition!!.cardConfiguration!!.energyGridCardCount)
    }

    @Test
    fun indiaBoardMapsEnergyGridSpaces() {
        val layout = india.boardLayout
        assertEquals("ENG_04", layout.spaceAt(4)?.targetId)
        assertEquals("ENG_01", layout.spaceAt(15)?.targetId)
        assertEquals("ENG_03", layout.spaceAt(23)?.targetId)
        assertEquals("ENG_02", layout.spaceAt(29)?.targetId)
    }

    @Test
    fun indiaEditionDefinitionVersionIsThree() {
        assertEquals(3, india.edition!!.definitionVersion)
    }
}

class EnergyGridQrResolutionTest {
    private val india = TestFixtures.loadEdition(EditionIds.INDIA)
    private val uk = TestFixtures.loadEdition(EditionIds.UK)
    private val indiaResolver = DefaultCardResolver(india)
    private val ukResolver = DefaultCardResolver(uk)

    @Test
    fun indiaResolvesEnergyGridPayloads() {
        assertEquals("ENG_01", indiaResolver.resolve("MUB:G:01").let { (it as com.boardbanker.core.card.CardResolution.Success).cardId })
        assertEquals(CardType.ENERGY_GRID, indiaResolver.resolve("MUB:G:04").let { (it as com.boardbanker.core.card.CardResolution.Success).cardType })
    }

    @Test
    fun ukRejectsEnergyGridPayloads() {
        assertTrue(ukResolver.resolve("MUB:G:01") is com.boardbanker.core.card.CardResolution.UnknownQr)
    }

    @Test
    fun invalidEnergyGridPayloadRejected() {
        assertTrue(indiaResolver.resolve("MUB:G:05") is com.boardbanker.core.card.CardResolution.UnknownQr)
    }
}

class EnergyGridGameplayTest {
    private val definitions = TestFixtures.loadEdition(EditionIds.INDIA)
    private val engine = DefaultGameEngine(definitions)

    @Test
    fun purchaseAndRentByOwnedCount() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01")).session
        session = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_02")).session
        assertEquals(12000, EnergyGridRentCalculator.rentForOwner(definitions, session, "USR_01"))

        session = engine.process(session, GameCommand.ProcessEnergyGridLanding("USR_02", "ENG_01")).session
        val rentTx = session.transactions.lastOrNull { it.transactionType == TransactionType.RENT_PAYMENT }
        assertNotNull(rentTx)
        assertEquals(12000, rentTx!!.amount)
    }

    @Test
    fun jailedPlayerCannotPurchaseEnergyGrid() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        val jailed = session.players["USR_01"]!!.copy(jailStatus = true)
        session = session.copy(players = session.players + ("USR_01" to jailed))
        val result = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01"))
        assertFalse(result.isSuccess)
    }

    @Test
    fun evt21MovesToNextEnergyGridWithGoOnce() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_21",
                actingPlayerId = "USR_01",
                fromBoardPosition = 30,
            ),
        )
        assertTrue(result.isSuccess)
        assertEquals(before + definitions.bankingValues.goSalary, result.session.players["USR_01"]!!.balance)
        assertEquals("ENG_04", result.session.pendingEnergyGridLanding?.energyGridId)
    }

    @Test
    fun evt21FromTwentyEightTargetsWindEnergy() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        val result = engine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_21",
                actingPlayerId = "USR_01",
                fromBoardPosition = 28,
            ),
        )
        assertEquals("ENG_02", result.session.pendingEnergyGridLanding?.energyGridId)
    }

    @Test
    fun evt21WrapAroundFromAfterTwentyNine() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        val result = engine.process(
            session,
            GameCommand.ApplyEvent(
                eventId = "EVT_21",
                actingPlayerId = "USR_01",
                fromBoardPosition = 30,
            ),
        )
        assertEquals("ENG_04", result.session.pendingEnergyGridLanding?.energyGridId)
    }

    @Test
    fun boardTraversalFindsNextGrid() {
        val next = BoardTraversal.nextEnergyGridSpace(definitions, 28)
        assertNotNull(next)
        assertEquals("ENG_02", next!!.targetId)
        val wrap = BoardTraversal.nextEnergyGridSpace(definitions, 30)
        assertEquals("ENG_04", wrap?.targetId)
    }

    @Test
    fun ownerLandingDoesNotChargeRent() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_01")).session
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ProcessEnergyGridLanding("USR_01", "ENG_01"))
        assertEquals(before, result.session.players["USR_01"]!!.balance)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.RENT_PAYMENT })
    }
}

class EnergyGridPersistenceTest {
    private val engine = DefaultGameEngine(TestFixtures.loadEdition(EditionIds.INDIA))

    @Test
    fun saveAndRestorePreservesEnergyGridOwnership() {
        var session = TestFixtures.newGameForEdition(EditionIds.INDIA)
        session = engine.process(session, GameCommand.PurchaseEnergyGrid("USR_01", "ENG_03")).session
        val snapshot = session.snapshot()
        val restored = session.restoreFrom(snapshot)
        assertEquals("USR_01", restored.energyGrids["ENG_03"]?.ownerPlayerId)
    }

    @Test
    fun incompatibleIndiaSaveVersionIsDetectable() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA).copy(editionDefinitionVersion = 2)
        val currentVersion = TestFixtures.loadEdition(EditionIds.INDIA).edition!!.definitionVersion
        assertTrue(session.editionDefinitionVersion < currentVersion)
    }
}
