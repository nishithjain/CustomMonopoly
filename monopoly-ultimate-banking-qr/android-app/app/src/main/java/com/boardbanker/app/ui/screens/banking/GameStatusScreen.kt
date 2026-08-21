package com.boardbanker.app.ui.screens.banking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.ui.components.PlayerIdentity
import com.boardbanker.app.ui.components.PlayerIconSize
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.GameDefinitions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStatusScreen(
    sessionManager: ActiveGameSessionManager,
    definitions: GameDefinitions,
    onBack: () -> Unit,
) {
    val session = remember(sessionManager.currentSession()) { sessionManager.currentSession() }

    Scaffold(topBar = { TopAppBar(title = { Text("GAME STATUS") }) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session == null) {
                Text("No active game.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("Players", style = MaterialTheme.typography.titleMedium)
                session.players.forEach { (playerId, player) ->
                    val name = PlayerDisplayNames.displayName(session, playerId, definitions)
                    val owned = session.properties.values.count { it.ownerPlayerId == playerId }
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PlayerIdentity(
                            playerId = playerId,
                            playerName = name,
                            iconSize = PlayerIconSize.Normal,
                        )
                        Text("Balance: ${formatMoney(player.balance, definitions)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Properties: $owned", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Jail: ${if (player.jailStatus) "YES" else "NO"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                val activeEffects = session.temporaryEffects.filter { it.active }
                if (activeEffects.isNotEmpty()) {
                    Text("Active Event Effects", style = MaterialTheme.typography.titleMedium)
                    activeEffects.forEach { effect ->
                        when (effect.effectType) {
                            "FORCE_LEVEL_1_RENT" -> {
                                Text("On The Run", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${effect.remainingUses} Level-1 rent payment(s) remaining",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            else -> Text(
                                "${effect.effectType}: ${effect.remainingUses} remaining",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("BACK")
            }
        }
    }
}
