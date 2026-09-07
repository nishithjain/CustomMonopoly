package com.boardbanker.app.ui.screens.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.boardbanker.app.gameplay.presentation.DiceGambleStatus
import com.boardbanker.app.gameplay.presentation.DiceGambleUiState
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LuckyBreakPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(
        dieOne: Int? = null,
        dieTwo: Int? = null,
        attemptLabel: String = "Attempt 1 of 3",
        rollEnabled: Boolean = true,
        rollButtonLabel: String = "Roll Dice",
        status: DiceGambleStatus = DiceGambleStatus.WAITING_TO_ROLL,
        showContinue: Boolean = false,
        outcomeHeadline: String? = null,
        outcomeMessage: String? = null,
    ) = DiceGambleUiState(
        eventId = "EVT_17",
        eventName = "Lucky Break",
        playerId = "USR_01",
        playerName = "Nishith",
        attemptLabel = attemptLabel,
        maximumAttempts = 3,
        dieOne = dieOne,
        dieTwo = dieTwo,
        jackpotText = "₹15,000",
        penaltyText = "₹5,000",
        instruction = "Roll both dice up to three times.",
        status = status,
        rollEnabled = rollEnabled,
        rollButtonLabel = rollButtonLabel,
        showContinue = showContinue,
        outcomeHeadline = outcomeHeadline,
        outcomeMessage = outcomeMessage,
    )

    private fun render(state: DiceGambleUiState) {
        composeRule.setContent {
            BankingQRTheme {
                LuckyBreakContent(
                    state = state,
                    onRollDice = {},
                    onContinue = {},
                )
            }
        }
    }

    @Test
    fun luckyBreakPanelVisibleWithDiceAndRollButton() {
        render(sampleState(dieOne = 4, dieTwo = 2))

        composeRule.onNodeWithText("Lucky Break").assertIsDisplayed()
        composeRule.onNodeWithText("Attempt 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Die one: 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Die two: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Roll Dice").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun rollButtonDisabledWhenRolling() {
        render(sampleState(rollEnabled = false, status = DiceGambleStatus.ROLLING, rollButtonLabel = "Rolling..."))

        composeRule.onNodeWithText("Rolling...").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun showsRemainingAttemptsMessage() {
        render(
            sampleState(
                dieOne = 1,
                dieTwo = 3,
                attemptLabel = "No doubles — 2 attempts remaining",
                rollButtonLabel = "Roll Again",
            ),
        )

        composeRule.onNodeWithText("No doubles — 2 attempts remaining").assertIsDisplayed()
        composeRule.onNodeWithText("Roll Again").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun completedDoublesShowsContinue() {
        render(
            sampleState(
                dieOne = 6,
                dieTwo = 6,
                showContinue = true,
                outcomeHeadline = "Doubles!",
                outcomeMessage = "Player B collected ₹15,000.",
            ),
        )

        composeRule.onNodeWithText("Doubles!").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Die one: 6").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Die two: 6").assertIsDisplayed()
        composeRule.onNodeWithText("Player B collected ₹15,000.").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
    }
}
