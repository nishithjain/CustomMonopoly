package com.boardbanker.app.scanner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.BackActionButton
import com.boardbanker.app.ui.components.TopBarBackButton
import com.boardbanker.app.ui.theme.BankingQRTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScannerScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun iconTag(icon: CommonUiIcon): String = "common_ui_icon_${icon.name.lowercase()}"

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBarAndBottomBackButtonsShowBackIcon() {
        var topBackClicked = false
        var bottomBackClicked = false

        composeRule.setContent {
            BankingQRTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                TopBarBackButton(
                                    onClick = { topBackClicked = true },
                                    testTag = ScannerTestTags.TOP_BAR_BACK,
                                )
                            },
                            title = { Text("Scan a Game Card") },
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxWidth(),
                    ) {
                        BackActionButton(
                            onClick = { bottomBackClicked = true },
                            testTag = ScannerTestTags.BOTTOM_BACK,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(ScannerTestTags.TOP_BAR_BACK, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(iconTag(CommonUiIcon.BACK), useUnmergedTree = true)
            .assertCountEquals(2)

        composeRule.onNodeWithTag(ScannerTestTags.BOTTOM_BACK).performClick()
        assertTrue(bottomBackClicked)

        composeRule.onNodeWithTag(ScannerTestTags.TOP_BAR_BACK).performClick()
        assertTrue(topBackClicked)
    }
}
