package com.boardbanker.app.ui.screens.banking

import com.boardbanker.app.game.ActiveGamePresentation
import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.util.pluralize
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PlayerState
import com.boardbanker.core.rules.PlayerActiveEventEffects

data class PlayerStatusUi(
    val playerId: String,
    val playerName: String,
    val balanceText: String,
    val propertyCountLabel: String,
    val energyGridCountLabel: String?,
    val hasEnergyGridsInEdition: Boolean,
    val statusLabel: String,
    val statusIcon: CommonUiIcon?,
    val isCurrentTurn: Boolean,
    val effectBadges: List<String>,
)

data class GameStatusUiModel(
    val playerCount: Int,
    val players: List<PlayerStatusUi>,
)

object GameStatusPresentation {
    fun build(session: GameSession, definitions: GameDefinitions): GameStatusUiModel {
        val players = ActiveGamePresentation.buildPlayerDashboard(session, definitions)
            .map { dashboard ->
                val playerState = session.players[dashboard.playerId]
                    ?: error("Missing player state for ${dashboard.playerId}")
                val status = resolveStatus(playerState)
                PlayerStatusUi(
                    playerId = dashboard.playerId,
                    playerName = dashboard.playerName,
                    balanceText = dashboard.balanceText,
                    propertyCountLabel = formatAssetCount(dashboard.propertyCount, "property"),
                    energyGridCountLabel = if (dashboard.hasEnergyGridsInEdition) {
                        formatAssetCount(dashboard.energyGridCount, "energy grid")
                    } else {
                        null
                    },
                    hasEnergyGridsInEdition = dashboard.hasEnergyGridsInEdition,
                    statusLabel = status.label,
                    statusIcon = status.icon,
                    isCurrentTurn = dashboard.isActiveTurn,
                    effectBadges = PlayerActiveEventEffects.activeEventNames(
                        playerId = dashboard.playerId,
                        session = session,
                        definitions = definitions,
                    ),
                )
            }
        return GameStatusUiModel(
            playerCount = players.size,
            players = players,
        )
    }

    internal fun resolveStatus(player: PlayerState): PlayerStatusLabel = when {
        player.bankrupt -> PlayerStatusLabel("Bankrupt", CommonUiIcon.ERROR)
        player.jailStatus -> PlayerStatusLabel("In jail", CommonUiIcon.JAIL)
        player.pendingSkipTurnCount > 0 -> PlayerStatusLabel("Turn skipped", null)
        else -> PlayerStatusLabel("Active", null)
    }

    internal fun formatAssetCount(count: Int, singular: String): String =
        "$count ${pluralize(count, singular)}"
}

internal data class PlayerStatusLabel(
    val label: String,
    val icon: CommonUiIcon?,
)

object GameStatusTestTags {
    const val TOP_BAR_BACK = "game_status_top_bar_back"
    const val PLAYER_LIST = "game_status_player_list"

    fun playerCard(playerId: String): String = "player_status_card_$playerId"
    fun currentTurnBadge(playerId: String): String = "player_status_current_turn_$playerId"
    fun balance(playerId: String): String = "player_status_balance_$playerId"
    fun properties(playerId: String): String = "player_status_properties_$playerId"
    fun energyGrids(playerId: String): String = "player_status_energy_grids_$playerId"
    fun status(playerId: String): String = "player_status_status_$playerId"
}
