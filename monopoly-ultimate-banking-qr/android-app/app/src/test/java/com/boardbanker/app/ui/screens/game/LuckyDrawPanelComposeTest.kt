package com.boardbanker.app.ui.screens.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.boardbanker.app.gameplay.presentation.EventDrawUiMapper
import com.boardbanker.app.gameplay.presentation.EventDrawUiState
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LuckyDrawPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(scanEnabled: Boolean = true, scanButtonLabel: String = EventDrawUiMapper.SCAN_BUTTON_LABEL) =
        EventDrawUiState(
            parentEventId = "EVT_15",
            parentEventName = "Lucky Draw",
            actingPlayerId = "USR_01",
            actingPlayerName = "Nishith",
            instruction = EventDrawUiMapper.INSTRUCTION,
            requiredDrawsText = EventDrawUiMapper.REQUIRED_DRAWS_TEXT,
            scanButtonLabel = scanButtonLabel,
            scanEnabled = scanEnabled,
        )

    @Test
    fun luckyDrawPanelShowsInstructionAndScanButton() {
        composeRule.setContent {
            BankingQRTheme {
                LuckyDrawContent(
                    state = sampleState(),
                    onScanEventCard = {},
                )
            }
        }

        composeRule.onNodeWithText("Lucky Draw").assertIsDisplayed()
        composeRule.onNodeWithText(EventDrawUiMapper.INSTRUCTION).assertIsDisplayed()
        composeRule.onNodeWithText(EventDrawUiMapper.REQUIRED_DRAWS_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText(EventDrawUiMapper.SCAN_BUTTON_LABEL).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun openingScannerLabelShownWhileLaunchInProgress() {
        composeRule.setContent {
            BankingQRTheme {
                LuckyDrawContent(
                    state = sampleState(
                        scanEnabled = false,
                        scanButtonLabel = EventDrawUiMapper.OPENING_SCANNER_LABEL,
                    ),
                    onScanEventCard = {},
                )
            }
        }

        composeRule.onNodeWithText(EventDrawUiMapper.OPENING_SCANNER_LABEL).assertIsDisplayed().assertIsNotEnabled()
    }
}
