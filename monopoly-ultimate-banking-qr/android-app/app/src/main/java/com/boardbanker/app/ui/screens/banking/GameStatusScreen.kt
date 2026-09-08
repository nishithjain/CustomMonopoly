package com.boardbanker.app.ui.screens.banking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.ui.components.TopBarBackButton
import com.boardbanker.app.ui.components.TopBarIconTitle
import com.boardbanker.core.model.GameDefinitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStatusScreen(
    sessionManager: ActiveGameSessionManager,
    definitions: GameDefinitions,
    onBack: () -> Unit,
) {
    val uiModel = remember(sessionManager.currentSession(), definitions) {
        val session = sessionManager.currentSession()
        if (session == null) null else GameStatusPresentation.build(session, definitions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    TopBarBackButton(
                        onClick = onBack,
                        testTag = GameStatusTestTags.TOP_BAR_BACK,
                    )
                },
                title = {
                    TopBarIconTitle(
                        icon = CommonUiIcon.GAME_STATUS,
                        title = "Game Status",
                    )
                },
            )
        },
    ) { innerPadding ->
        if (uiModel == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text("No active game.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            GameStatusContent(
                uiModel = uiModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
internal fun GameStatusContent(
    uiModel: GameStatusUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Players · ${uiModel.playerCount}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(GameStatusTestTags.PLAYER_LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = uiModel.players,
                key = { it.playerId },
            ) { player ->
                PlayerStatusCard(player = player)
            }
        }
    }
}
