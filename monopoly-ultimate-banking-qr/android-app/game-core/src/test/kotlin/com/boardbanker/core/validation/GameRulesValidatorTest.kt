package com.boardbanker.core.validation

import com.boardbanker.core.TestEditionResources
import com.boardbanker.core.TestFixtures
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventActionType
import com.boardbanker.core.model.EventTargetType
import com.boardbanker.core.model.EditionIds
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesValidatorTest {
    @Test
    fun ukEditionLoadsTypedGameRulesMatchingUltimateBankingDefaults() {
        val definitions = TestFixtures.definitions
        assertEquals(2, definitions.rules.setup.minimumPlayers)
        assertEquals(4, definitions.rules.setup.maximumPlayers)
        assertEquals(5, definitions.rules.rent.maximumRentLevel)
        assertEquals(1, definitions.rules.undo.undoDepth)
        assertEquals(3, definitions.rules.jail.exitByDoublesMaxAttempts)
        assertEquals(30, definitions.rules.auction.timedAuctionSeconds)
        assertTrue(GameRulesValidator.validateAgainstEdition(definitions.rules, definitions).isEmpty())
    }

    @Test
    fun indiaEditionUsesSameGameplayRulesAsUk() {
        val india = TestFixtures.loadEdition(EditionIds.INDIA)
        assertEquals(2, india.rules.setup.minimumPlayers)
        assertEquals(4, india.rules.setup.maximumPlayers)
        assertEquals(5, india.rules.rent.maximumRentLevel)
    }

    @Test
    fun customTestEditionUsesConfiguredRuleVariations() {
        val definitions = loadCustomTestEdition()
        assertEquals(5, definitions.rules.setup.maximumPlayers)
        assertEquals(6, definitions.rules.rent.maximumRentLevel)
        assertEquals(2, definitions.rules.undo.undoDepth)
    }

    @Test
    fun invalidSecondActionFailsEditionValidation() {
        val uk = TestFixtures.definitions
        val template = uk.events["EVT_13"]!!
        val invalid = uk.copy(
            events = uk.events + (
                "EVT_BAD_SECOND" to template.copy(
                    eventId = "EVT_BAD_SECOND",
                    actions = listOf(
                        EventActionDefinition(actionType = "TEMPORARY_RENT_CAP"),
                        EventActionDefinition(
                            actionType = "SET_PROPERTY_RENT_LEVEL",
                            targetType = EventTargetType.PROPERTY.name,
                            amount = 99,
                        ),
                    ),
                )
            ),
        )
        val problems = EventActionValidator.validateAgainstEdition(invalid)
        assertTrue(problems.any { it.contains("actions[1]") && it.contains("rent level bounds") })
    }

    @Test
    fun wealthUsesRentLevelTrueIsRejected() {
        val rules = TestFixtures.definitions.rules.copy(
            winner = TestFixtures.definitions.rules.winner.copy(wealthUsesRentLevel = true),
        )
        val problems = GameRulesValidator.validate(rules, EditionIds.UK)
        assertTrue(problems.any { it.contains("wealthUsesRentLevel=true") })
    }

    @Test
    fun unknownDebtModeStillValidatesKnownModes() {
        val rules = TestFixtures.definitions.rules.copy(
            debt = TestFixtures.definitions.rules.debt.copy(
                resolutionMode = com.boardbanker.core.model.DebtResolutionMode.IMMEDIATE_BANKRUPTCY,
            ),
        )
        val problems = GameRulesValidator.validate(rules, EditionIds.UK)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun allSupportedEventActionTypesValidateForUk() {
        val definitions = TestFixtures.definitions
        val problems = EventActionValidator.validateAgainstEdition(definitions)
        assertTrue(problems.isEmpty())
        assertTrue(EventActionType.entries.size >= 14)
        assertEquals(23, definitions.events.size)
    }

    private fun loadCustomTestEdition() = TestEditionResources.loadCustomTestEdition()
}
