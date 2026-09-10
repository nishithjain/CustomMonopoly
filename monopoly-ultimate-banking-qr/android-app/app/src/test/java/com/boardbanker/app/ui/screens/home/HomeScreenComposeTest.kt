package com.boardbanker.app.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.theme.BankingQRTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun iconTag(icon: CommonUiIcon): String = "common_ui_icon_${icon.name.lowercase()}"

    @Test
    fun resumeGameButtonShowsResumeGameIconAndInvokesCallback() {
        val repository = FakeGameSessionRepository()
        runBlocking { repository.save(AppTestSupport.newGame()) }
        val sessionManager = AppTestSupport.sessionManager(repository)
        val viewModel = HomeViewModel(sessionManager, repository, null)

        var resumed = false
        composeRule.setContent {
            BankingQRTheme {
                HomeScreen(
                    onNewGame = {},
                    onResumeSetup = {},
                    onResumeGame = { resumed = true },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("RESUME GAME", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag(iconTag(CommonUiIcon.RESUME_GAME), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("RESUME GAME").performClick()
        assertTrue(resumed)
    }

    @Test
    fun resumeGameButtonDisabledWhenDefinitionsFailedToLoad() {
        val repository = FakeGameSessionRepository()
        runBlocking { repository.save(AppTestSupport.newGame()) }
        val sessionManager = AppTestSupport.sessionManager(repository)
        val viewModel = HomeViewModel(sessionManager, repository, definitionsError = "load failed")

        composeRule.setContent {
            BankingQRTheme {
                HomeScreen(
                    onNewGame = {},
                    onResumeSetup = {},
                    onResumeGame = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_resume_game_button", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeRule.onNodeWithTag("home_resume_game_button").assertIsNotEnabled()
    }
}
