package com.boardbanker.app.ui.screens.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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

    private fun sampleState(scanEnabled: Boolean = true) = EventDrawUiState(
        parentEventId = "EVT_15",
        parentEventName = "Lucky Draw",
        actingPlayerId = "USR_01",
        actingPlayerName = "Nishith",
        instruction = EventDrawUiMapper.INSTRUCTION,
        chainProgressText = "Additional draw 1 of 3",
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
        composeRule.onNodeWithText("Additional draw 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("Scan Event Card").assertIsDisplayed().assertIsEnabled()
    }
}
