package com.boardbanker.core

import com.boardbanker.core.card.CardType
import com.boardbanker.core.card.DefaultCardResolver
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.EventActionType
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.money.MoneyFormatter
import com.boardbanker.core.validation.GameRulesValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * End-to-end India release-readiness checks derived from authoritative edition data.
 */
class IndiaEditionReadinessTest {
    private val dataDir: Path = TestFixtures.dataDir
    private val repository = EditionRepository(FileEditionFileSource(dataDir))
    private val workspaceRoot: Path = listOf(
        dataDir.parent!!.parent!!,
        Path.of("c:/Personal/Monopoly"),
    ).first { it.resolve("Resources/Editions/india").toFile().exists() }
    private val india: GameDefinitions = repository.load(EditionIds.INDIA)
    private val uk: GameDefinitions = repository.load(EditionIds.UK)
    private val indiaEngine = DefaultGameEngine(india)
    private val ukEngine = DefaultGameEngine(uk)

    private val indiaSupportedActions = setOf(
        EventActionType.MOVE_TO_SPACE,
        EventActionType.MOVE_BACKWARD,
        EventActionType.BANK_CREDIT,
        EventActionType.BANK_DEBIT,
        EventActionType.PAY_EACH_PLAYER,
        EventActionType.COLLECT_FROM_EACH_PLAYER,
        EventActionType.DEBIT_PER_OWNED_PROPERTY,
        EventActionType.CREDIT_PER_OWNED_PROPERTY,
        EventActionType.NEXT_RENT_WAIVER,
        EventActionType.GET_OUT_OF_JAIL_PASS,
        EventActionType.MOVE_TO_JAIL,
        EventActionType.INCREASE_SELECTED_PROPERTY_RENT_LEVEL,
        EventActionType.DECREASE_SELECTED_PROPERTY_RENT_LEVEL,
        EventActionType.DRAW_ANOTHER_EVENT,
        EventActionType.COOPERATIVE_PROPERTY_UPGRADE,
        EventActionType.GAMBLE_ON_DICE_ROLL,
        EventActionType.SKIP_NEXT_TURN,
        EventActionType.FORCED_PROPERTY_SELLBACK,
        EventActionType.TOP_UP_BALANCE_TO_THRESHOLD,
        EventActionType.MOVE_TO_NEAREST_STATION,
        EventActionType.EXTRA_TURN,
        EventActionType.COMPLETE_COLOR_SET_BONUS_CREDIT,
    )

    @Test
    fun newGameCatalogue_exposesIndiaEdition() {
        val catalog = repository.loadEditionCatalog()
        val enabledIds = catalog.editions.map { it.editionId }
        assertTrue("India must be enabled in edition catalogue", EditionIds.INDIA in enabledIds)
        assertEquals("UK Edition", catalog.editions.first { it.editionId == EditionIds.UK }.name)
        assertEquals("India Edition", catalog.editions.first { it.editionId == EditionIds.INDIA }.name)
        assertEquals(EditionIds.UK, catalog.defaultEditionId)
    }

    @Test
    fun indiaSession_initializesWithIndiaMetadataAndDefinitions() {
        var result = indiaEngine.process(
            com.boardbanker.core.model.GameSession(
                gameId = "INDIA_READY",
                editionId = EditionIds.INDIA,
                editionDefinitionVersion = 2,
            ),
            GameCommand.CreateGame("INDIA_READY"),
        )
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = indiaEngine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = indiaEngine.process(result.session, GameCommand.StartGame)

        val session = result.session
        assertEquals(EditionIds.INDIA, session.editionId)
        assertEquals(2, session.editionDefinitionVersion)
        assertEquals(150000, session.players["USR_01"]!!.balance)
        assertEquals(20000, india.bankingValues.goSalary)
        assertEquals(10000, india.bankingValues.jailReleaseFee)
        assertEquals("₹150,000", MoneyFormatter.format(150000, india.bankingValues.currency))
        assertEquals("Cubbon Park", india.properties["PRP_01"]!!.name)
        assertEquals(25, india.events.size)
        assertEquals(22, india.properties.size)
        assertTrue(GameRulesValidator.validateAgainstEdition(india.rules, india).isEmpty())
    }

    @Test
    fun ukAndIndiaSessions_bindToDistinctDefinitions() {
        val ukSession = TestFixtures.newGame()
        val indiaSession = TestFixtures.newGameForEdition(EditionIds.INDIA)
        assertEquals(EditionIds.UK, ukSession.editionId)
        assertEquals(EditionIds.INDIA, indiaSession.editionId)
        assertEquals("Old Kent Road", uk.properties["PRP_01"]!!.name)
        assertEquals("Cubbon Park", india.properties["PRP_01"]!!.name)

        val ukResolver = DefaultCardResolver(uk)
        val indiaResolver = DefaultCardResolver(india)
        val sharedPayload = "MUB:E:E01"
        assertEquals("Boom Town", ukResolver.resolve(sharedPayload).let { (it as com.boardbanker.core.card.CardResolution.Success).displayName })
        assertEquals("Advance to GO", indiaResolver.resolve(sharedPayload).let { (it as com.boardbanker.core.card.CardResolution.Success).displayName })
        assertTrue(indiaResolver.resolve("MUB:E:E24") is com.boardbanker.core.card.CardResolution.Success)
        assertTrue(ukResolver.resolve("MUB:E:E24") is com.boardbanker.core.card.CardResolution.UnknownQr)
    }

    @Test
    fun allIndiaEvents_haveHandlerWorkflowAndAssets() {
        val rawEvents = loadRawIndiaEvents()
        val failures = mutableListOf<String>()

        assertEquals(india.edition!!.cardConfiguration!!.eventCardCount, rawEvents.size)
        assertEquals(india.edition!!.cardConfiguration!!.eventCardCount, india.events.size)

        for (raw in rawEvents) {
            val eventId = raw["eventId"]!!
            val event = india.events[eventId]
            if (event == null) {
                failures += "$eventId: missing from loaded GameDefinitions"
                continue
            }
            if (event.name != raw["name"]) {
                failures += "$eventId: title mismatch '${event.name}' vs '${raw["name"]}'"
            }
            if (event.qrPayload != raw["qrPayload"]) {
                failures += "$eventId: QR payload mismatch"
            }
            val card = india.cards[eventId]
            if (card == null || card.cardType != CardType.EVENT) {
                failures += "$eventId: missing EVENT card registry entry"
            } else if (card.qrPayload != event.qrPayload) {
                failures += "$eventId: card registry QR mismatch"
            }
            if (!workspaceRoot.resolve(raw["frontAsset"]!!).toFile().exists()) {
                failures += "$eventId: missing front asset ${raw["frontAsset"]}"
            }
            if (!workspaceRoot.resolve(raw["qrAsset"]!!).toFile().exists()) {
                failures += "$eventId: missing QR asset ${raw["qrAsset"]}"
            }
            if (india.cardsByQrPayload[event.qrPayload]?.cardId != eventId) {
                failures += "$eventId: edition resolver does not map ${event.qrPayload}"
            }

            for ((index, action) in event.actions.withIndex()) {
                val parsed = runCatching { action.parsedActionType() }
                if (parsed.isFailure) {
                    failures += "$eventId action $index: unsupported actionType '${action.actionType}'"
                } else {
                    val actionType = parsed.getOrThrow()
                    if (actionType !in indiaSupportedActions) {
                        failures += "$eventId action $index: $actionType is not in India handler set"
                    }
                }
            }
        }

        assertTrue("India event coverage failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun allIndiaProperties_areCompleteAndResolvable() {
        val failures = mutableListOf<String>()
        val expectedRentLevels = india.edition!!.cardConfiguration!!.rentLevelsPerProperty

        assertEquals(22, india.properties.size)
        assertEquals(22, india.edition!!.cardConfiguration!!.propertyCardCount)

        for ((propertyId, property) in india.properties) {
            val number = PropertyDisplayNames.propertyNumber(propertyId)
            if (number == null) {
                failures += "$propertyId: invalid property ID format"
            }
            val display = PropertyDisplayNames.displayNameWithNumber(property)
            if (number != null && !display.startsWith("[$number] ")) {
                failures += "$propertyId: display name must use numbered format"
            }
            if (property.purchasePrice <= 0) {
                failures += "$propertyId: purchase price must be positive"
            }
            if (property.rentLevels.size != expectedRentLevels) {
                failures += "$propertyId: expected $expectedRentLevels rent levels, found ${property.rentLevels.size}"
            }
            if (property.colorGroup.isBlank()) {
                failures += "$propertyId: missing colour group"
            }
            val card = india.cards[propertyId]
            if (card == null || card.cardType != CardType.PROPERTY) {
                failures += "$propertyId: missing PROPERTY card registry entry"
            }
            if (india.cardsByQrPayload[card?.qrPayload]?.cardId != propertyId) {
                failures += "$propertyId: resolver QR mapping failed"
            }
            if (!india.boardRelationships.propertyToSide.containsKey(propertyId)) {
                failures += "$propertyId: missing board relationship"
            }
            val raw = loadRawIndiaProperties()[propertyId]
            if (raw != null) {
                if (!workspaceRoot.resolve(raw["frontAsset"]!!).toFile().exists()) {
                    failures += "$propertyId: missing front asset"
                }
                if (!workspaceRoot.resolve(raw["qrAsset"]!!).toFile().exists()) {
                    failures += "$propertyId: missing QR asset"
                }
            }
        }

        val purchase = indiaEngine.process(
            TestFixtures.newGameForEdition(EditionIds.INDIA),
            GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        )
        if (!purchase.isSuccess) {
            failures += "Representative purchase failed: ${purchase.error}"
        }

        assertTrue("India property validation failures:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun indiaCards_doNotResolveThroughUkDefinitions() {
        val ukResolver = DefaultCardResolver(uk)
        val indiaOnlyPayload = "MUB:E:E25"
        assertNotNull(india.cardsByQrPayload[indiaOnlyPayload])
        assertTrue(ukResolver.resolve(indiaOnlyPayload) is com.boardbanker.core.card.CardResolution.UnknownQr)
    }

    @Test
    fun indiaEditionCounts_matchAuthoritativeConfiguration() {
        assertEquals(4, india.edition!!.cardConfiguration!!.playerCardCount)
        assertEquals(22, india.edition!!.cardConfiguration!!.propertyCardCount)
        assertEquals(25, india.edition!!.cardConfiguration!!.eventCardCount)
        assertEquals(51, india.cards.size)
        assertFalse(india.cards.values.any { it.cardType == CardType.EVENT && it.cardId !in india.events })
        assertFalse(india.cards.values.any { it.cardType == CardType.PROPERTY && it.cardId !in india.properties })
    }

    private fun loadRawIndiaEvents(): List<Map<String, String>> {
        val root = Json.parseToJsonElement(
            dataDir.resolve("editions/india/events.json").toFile().readText(),
        ).jsonObject
        return root["events"]!!.jsonArray.map { element ->
            val obj = element.jsonObject
            mapOf(
                "eventId" to obj["eventId"]!!.jsonPrimitive.content,
                "name" to obj["name"]!!.jsonPrimitive.content,
                "qrPayload" to obj["qrPayload"]!!.jsonPrimitive.content,
                "frontAsset" to obj["frontAsset"]!!.jsonPrimitive.content,
                "qrAsset" to obj["qrAsset"]!!.jsonPrimitive.content,
            )
        }
    }

    private fun loadRawIndiaProperties(): Map<String, Map<String, String>> {
        val root = Json.parseToJsonElement(
            dataDir.resolve("editions/india/properties.json").toFile().readText(),
        ).jsonObject
        return root["properties"]!!.jsonArray.associate { element ->
            val obj = element.jsonObject
            val id = obj["propertyId"]!!.jsonPrimitive.content
            id to mapOf(
                "frontAsset" to obj["frontAsset"]!!.jsonPrimitive.content,
                "qrAsset" to obj["qrAsset"]!!.jsonPrimitive.content,
            )
        }
    }
}
