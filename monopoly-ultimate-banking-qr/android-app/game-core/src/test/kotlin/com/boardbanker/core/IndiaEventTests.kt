package com.boardbanker.core

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IndiaEventTests {
    private lateinit var engine: DefaultGameEngine
    private val definitions get() = TestFixtures.loadEdition(EditionIds.INDIA)

    @Before
    fun setUp() {
        engine = DefaultGameEngine(definitions)
    }

    private fun indiaGame(
        players: List<String> = listOf("USR_01", "USR_02", "USR_03"),
        balances: Map<String, Int>? = null,
    ) = TestFixtures.newGameForEdition(EditionIds.INDIA, players, balances)

  private fun apply(eventId: String, acting: String = "USR_01", propertyId: String? = null, target: String? = null, secondProperty: String? = null, secondPlayer: String? = null) =
        engine.process(
            indiaGame().let { session ->
                if (propertyId != null) {
                    TestFixtures.sessionWithProperty(propertyId, acting, rentLevel = 2, players = listOf("USR_01", "USR_02", "USR_03"))
                } else {
                    session
                }
            },
            GameCommand.ApplyEvent(eventId, acting, propertyId, target, secondProperty, secondPlayer),
        )

    @Test
    fun indiaEditionLoadsTwentyFiveEvents() {
        assertEquals(25, definitions.events.size)
        assertEquals(2, definitions.edition!!.definitionVersion)
        assertEquals("Advance to GO", definitions.events["EVT_01"]!!.name)
    }

    @Test
    fun qrPayloadsResolveForAllIndiaEvents() {
        for (index in 1..25) {
            val eventId = "EVT_%02d".format(index)
            val payload = "MUB:E:E%02d".format(index)
            val card = definitions.cardsByQrPayload[payload]
            assertNotNull("Missing QR mapping for $payload", card)
            assertEquals(eventId, card!!.cardId)
        }
    }

    @Test
    fun editionIsolation_sameQrDifferentBehaviour() {
        val uk = DefaultGameEngine(TestFixtures.definitions)
        val india = DefaultGameEngine(definitions)
        val ukSession = TestFixtures.newGame()
        val indiaSession = TestFixtures.newGameForEdition(EditionIds.INDIA)

        val ukResult = uk.process(
            ukSession,
            GameCommand.ApplyEvent("EVT_01", "USR_01", propertyId = "PRP_10"),
        )
        val indiaResult = india.process(
            indiaSession,
            GameCommand.ApplyEvent("EVT_01", "USR_01"),
        )

        assertTrue(ukResult.session.pendingEventChoice != null)
        assertEquals(20000, indiaResult.session.players["USR_01"]!!.balance - indiaSession.players["USR_01"]!!.balance)
        assertTrue(indiaResult.physicalActions.any { it.instruction.contains("GO", ignoreCase = true) })
    }

    @Test fun evt01_advanceToGo() {
        val before = indiaGame().players["USR_01"]!!.balance
        val result = apply("EVT_01")
        assertEquals(before + 20000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt02_wrongTurn() {
        val result = apply("EVT_02")
        assertTrue(result.physicalActions.any { it.instruction.contains("backward", ignoreCase = true) })
    }

    @Test fun evt03_upiCashback() {
        val before = indiaGame().players["USR_01"]!!.balance
        val result = apply("EVT_03")
        assertEquals(before + 5000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt04_incomeTaxRefund() {
        val before = indiaGame().players["USR_01"]!!.balance
        val result = apply("EVT_04")
        assertEquals(before + 10000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt05_hospitalExpense() {
        val before = indiaGame(balances = mapOf("USR_01" to 50000)).players["USR_01"]!!.balance
        val result = engine.process(
            indiaGame(balances = mapOf("USR_01" to 50000)),
            GameCommand.ApplyEvent("EVT_05", "USR_01"),
        )
        assertEquals(before - 10000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt06_festivalContribution() {
        val session = indiaGame(balances = mapOf("USR_01" to 50000, "USR_02" to 10000, "USR_03" to 10000))
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_06", "USR_01"))
        assertEquals(before - 10000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt07_birthdayCelebration() {
        val session = indiaGame(balances = mapOf("USR_01" to 10000, "USR_02" to 50000, "USR_03" to 50000))
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_07", "USR_01"))
        assertEquals(before + 10000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt08_municipalMaintenance() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
            .let { base ->
                base.copy(
                    properties = base.properties + (
                        "PRP_01" to base.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
                    ),
                )
            }
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_08", "USR_01"))
        assertEquals(before - 2000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt09_greenBuildingGrant() {
        val session = TestFixtures.newGameForEdition(EditionIds.INDIA, listOf("USR_01", "USR_02"))
            .let { base ->
                base.copy(
                    properties = base.properties + (
                        "PRP_01" to base.properties["PRP_01"]!!.copy(ownerPlayerId = "USR_01")
                    ),
                )
            }
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_09", "USR_01"))
        assertEquals(before + 2000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt10_rentRelief() {
        val result = apply("EVT_10")
        assertTrue(result.session.players["USR_01"]!!.pendingRentWaiver)
    }

    @Test fun evt11_jailPass() {
        val result = apply("EVT_11")
        assertEquals(1, result.session.players["USR_01"]!!.jailPassCount)
    }

    @Test fun evt12_trafficCourt() {
        val result = apply("EVT_12")
        assertTrue(result.session.players["USR_01"]!!.jailStatus)
    }

    @Test fun evt13_localMarketBoom() {
        val session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", rentLevel = 2, players = listOf("USR_01", "USR_02"))
            .copy(editionId = EditionIds.INDIA, editionDefinitionVersion = 2)
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_13", "USR_01", propertyId = "PRP_01"))
        assertEquals(3, result.session.properties["PRP_01"]!!.currentRentLevel)
    }

    @Test fun evt14_marketCorrection() {
        val session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", rentLevel = 3, players = listOf("USR_01", "USR_02"))
            .copy(editionId = EditionIds.INDIA, editionDefinitionVersion = 2)
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_14", "USR_01", propertyId = "PRP_01"))
        assertEquals(2, result.session.properties["PRP_01"]!!.currentRentLevel)
    }

    @Test fun evt15_luckyDraw() {
        val result = apply("EVT_15")
        assertNotNull(result.session.pendingEventDraw)
    }

    @Test fun evt16_communityDevelopment() {
        val session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", rentLevel = 2, players = listOf("USR_01", "USR_02"))
            .let {
                it.copy(
                    editionId = EditionIds.INDIA,
                    editionDefinitionVersion = 2,
                    properties = it.properties + ("PRP_02" to it.properties["PRP_02"]!!.copy(ownerPlayerId = "USR_02", currentRentLevel = 2)),
                )
            }
        val result = engine.process(
            session,
            GameCommand.ApplyEvent("EVT_16", "USR_01", propertyId = "PRP_01", secondPropertyId = "PRP_02", secondPlayerId = "USR_02"),
        )
        assertEquals(3, result.session.properties["PRP_01"]!!.currentRentLevel)
        assertEquals(3, result.session.properties["PRP_02"]!!.currentRentLevel)
    }

    @Test fun evt17_luckyBreak_successOnDoubles() {
        val session = indiaGame()
        val started = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01"))
        val rolled = engine.process(
            started.session,
            GameCommand.RollEventDice("EVT_17", "USR_01", listOf(3, 3)),
        )
        assertEquals(session.players["USR_01"]!!.balance + 15000, rolled.session.players["USR_01"]!!.balance)
    }

    @Test fun evt17_luckyBreak_failureAfterThreeAttempts() {
        var session = indiaGame(balances = mapOf("USR_01" to 50000))
        session = engine.process(session, GameCommand.ApplyEvent("EVT_17", "USR_01")).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(1, 2))).session
        session = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(2, 3))).session
        val final = engine.process(session, GameCommand.RollEventDice("EVT_17", "USR_01", listOf(4, 5)))
        assertEquals(45000, final.session.players["USR_01"]!!.balance)
    }

    @Test fun evt18_metroDelay() {
        val result = apply("EVT_18")
        assertEquals(1, result.session.players["USR_01"]!!.pendingSkipTurnCount)
    }

    @Test fun evt19_eminentDomain() {
        val session = TestFixtures.sessionWithProperty("PRP_01", "USR_01", players = listOf("USR_01", "USR_02"))
            .copy(editionId = EditionIds.INDIA, editionDefinitionVersion = 2)
        val before = session.players["USR_01"]!!.balance
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_19", "USR_01", propertyId = "PRP_01"))
        assertEquals(null, result.session.properties["PRP_01"]!!.ownerPlayerId)
        assertEquals(before + 12000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt20_economicRelief_belowThreshold() {
        val session = indiaGame(balances = mapOf("USR_01" to 10000))
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_20", "USR_01"))
        assertEquals(30000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt20_economicRelief_atThresholdNoOp() {
        val session = indiaGame(balances = mapOf("USR_01" to 30000))
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_20", "USR_01"))
        assertEquals(30000, result.session.players["USR_01"]!!.balance)
        assertTrue(result.transactions.none { it.transactionType == TransactionType.BANK_CREDIT })
    }

    @Test fun evt21_wrongTerminal() {
        val result = apply("EVT_21")
        assertTrue(result.physicalActions.any { it.instruction.contains("Energy Station", ignoreCase = true) })
    }

    @Test fun evt22_cloudStorage() {
        val session = indiaGame(balances = mapOf("USR_01" to 50000))
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_22", "USR_01"))
        assertEquals(45000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt23_beautyContest() {
        val before = indiaGame().players["USR_01"]!!.balance
        val result = apply("EVT_23")
        assertEquals(before + 5000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun evt24_secondWind() {
        val result = apply("EVT_24")
        assertTrue(result.session.players["USR_01"]!!.pendingExtraTurn)
    }

    @Test fun evt25_greenEnergyRebate() {
        var session = indiaGame(balances = mapOf("USR_01" to 10000))
        val group = definitions.boardRelationships.colorGroups.entries.first { it.value.size >= 2 }
        group.value.forEach { propertyId ->
            session = session.copy(
                properties = session.properties + (
                    propertyId to session.properties[propertyId]!!.copy(ownerPlayerId = "USR_01")
                ),
            )
        }
        val result = engine.process(session, GameCommand.ApplyEvent("EVT_25", "USR_01"))
        assertEquals(20000, result.session.players["USR_01"]!!.balance)
    }

    @Test fun rentWaiverConsumedOnlyForPositiveRent() {
        var session = indiaGame()
        session = engine.process(session, GameCommand.ApplyEvent("EVT_10", "USR_01")).session
        session = TestFixtures.sessionWithProperty("PRP_01", "USR_02", players = listOf("USR_01", "USR_02"))
            .copy(
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = 2,
                players = session.players,
            )
        val before = session.players["USR_01"]!!.balance
        val rent = engine.process(session, GameCommand.ProcessPropertyLanding("USR_01", "PRP_01"))
        assertEquals(before, rent.session.players["USR_01"]!!.balance)
        assertFalse(rent.session.players["USR_01"]!!.pendingRentWaiver)
    }

    @Test fun allTwentyFiveIndiaEventsApplySuccessfully() {
        for (index in 1..25) {
            val eventId = "EVT_%02d".format(index)
            val session = when (eventId) {
                "EVT_13", "EVT_14", "EVT_19" -> TestFixtures.sessionWithProperty("PRP_01", "USR_01", rentLevel = 2)
                    .copy(editionId = EditionIds.INDIA, editionDefinitionVersion = 2)
                "EVT_16" -> TestFixtures.sessionWithProperty("PRP_01", "USR_01", rentLevel = 2)
                    .copy(
                        editionId = EditionIds.INDIA,
                        editionDefinitionVersion = 2,
                        properties = TestFixtures.sessionWithProperty("PRP_01", "USR_01", rentLevel = 2).properties +
                            mapOf("PRP_02" to TestFixtures.sessionWithProperty("PRP_01", "USR_01").properties["PRP_02"]!!.copy(ownerPlayerId = "USR_02", currentRentLevel = 2)),
                    )
                else -> indiaGame(balances = mapOf("USR_01" to 200000, "USR_02" to 200000, "USR_03" to 200000))
            }
            val command = when (eventId) {
                "EVT_13", "EVT_14", "EVT_19" -> GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_01")
                "EVT_16" -> GameCommand.ApplyEvent(eventId, "USR_01", propertyId = "PRP_01", secondPropertyId = "PRP_02", secondPlayerId = "USR_02")
                else -> GameCommand.ApplyEvent(eventId, "USR_01")
            }
            val result = engine.process(session, command)
            assertTrue("Failed for $eventId: ${result.outcome}", result.isSuccess || result.outcome.name == "PENDING_ACTION")
        }
    }
}
