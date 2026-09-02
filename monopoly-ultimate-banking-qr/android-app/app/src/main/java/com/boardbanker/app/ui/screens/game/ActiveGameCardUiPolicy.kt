package com.boardbanker.app.ui.screens.game

import com.boardbanker.app.gameplay.presentation.GameplayResultUiModel
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import com.boardbanker.core.card.CardType

data class ActiveGameActionVisibility(
    val showBuy: Boolean = false,
    val showAuction: Boolean = false,
    val showContinue: Boolean = false,
    val showScanPlayer: Boolean = false,
    val showScanProperty: Boolean = false,
    val showDone: Boolean = false,
    val showCancel: Boolean = false,
)

object ActiveGameCardUiPolicy {
    fun displayCardId(
        workflowState: GameplayWorkflowState,
        result: GameplayResultUiModel?,
    ): String? {
        result?.displayCardId?.let { return it }
        return when (val workflow = workflowState) {
            is GameplayWorkflowState.PropertySummary -> workflow.propertyId
            is GameplayWorkflowState.UnownedPropertyDecision -> workflow.propertyId
            is GameplayWorkflowState.WaitingForPurchasingPlayer -> workflow.propertyId
            is GameplayWorkflowState.WaitingForRentPayer -> workflow.propertyId
            is GameplayWorkflowState.WaitingForAuctionStarter -> workflow.propertyId
            is GameplayWorkflowState.EventIntro -> workflow.eventId
            is GameplayWorkflowState.EventCollectingTargets -> workflow.eventId
            is GameplayWorkflowState.EventConfirm -> workflow.eventId
            is GameplayWorkflowState.EventDiceGamble -> workflow.eventId
            is GameplayWorkflowState.EventDrawScanRequired -> workflow.parentEventId
            is GameplayWorkflowState.EventPropertyChoice -> workflow.propertyId
            is GameplayWorkflowState.PlayerInfo -> workflow.playerId
            else -> null
        }
    }

    fun actionVisibility(
        workflowState: GameplayWorkflowState,
        result: GameplayResultUiModel?,
        gameplayLocked: Boolean,
    ): ActiveGameActionVisibility {
        if (gameplayLocked) {
            return ActiveGameActionVisibility(showDone = result != null)
        }
        if (result != null) {
            return ActiveGameActionVisibility(showDone = true)
        }
        return when (workflowState) {
            is GameplayWorkflowState.UnownedPropertyDecision -> ActiveGameActionVisibility(
                showBuy = true,
                showAuction = true,
                showCancel = true,
            )
            is GameplayWorkflowState.WaitingForRentPayer -> ActiveGameActionVisibility(
                showScanPlayer = true,
                showCancel = true,
            )
            is GameplayWorkflowState.EventIntro -> ActiveGameActionVisibility(
                showContinue = true,
                showCancel = true,
            )
            is GameplayWorkflowState.EventDiceGamble -> ActiveGameActionVisibility()
            is GameplayWorkflowState.EventDrawScanRequired -> ActiveGameActionVisibility()
            is GameplayWorkflowState.EventCollectingTargets,
            is GameplayWorkflowState.EventConfirm,
            is GameplayWorkflowState.WaitingForPurchasingPlayer,
            is GameplayWorkflowState.WaitingForAuctionStarter,
            -> ActiveGameActionVisibility(showCancel = true)
            is GameplayWorkflowState.PlayerInfo -> ActiveGameActionVisibility(showDone = true)
            is GameplayWorkflowState.EventPropertyChoice -> ActiveGameActionVisibility(
                showBuy = true,
                showAuction = true,
                showCancel = true,
            )
            is GameplayWorkflowState.LocationWaitingForDestinationProperty -> ActiveGameActionVisibility(
                showScanProperty = true,
                showCancel = true,
            )
            else -> ActiveGameActionVisibility()
        }
    }

    fun displayCardType(
        workflowState: GameplayWorkflowState,
        result: GameplayResultUiModel?,
        displayCardId: String?,
    ): CardType? {
        displayCardId?.let { cardIdFromId(it) }?.let { return it }
        return when (workflowState) {
            is GameplayWorkflowState.PropertySummary -> CardType.PROPERTY
            is GameplayWorkflowState.UnownedPropertyDecision -> CardType.PROPERTY
            is GameplayWorkflowState.WaitingForPurchasingPlayer -> CardType.PROPERTY
            is GameplayWorkflowState.WaitingForRentPayer -> CardType.PROPERTY
            is GameplayWorkflowState.WaitingForAuctionStarter -> CardType.PROPERTY
            is GameplayWorkflowState.EventIntro -> CardType.EVENT
            is GameplayWorkflowState.EventCollectingTargets -> CardType.EVENT
            is GameplayWorkflowState.EventConfirm -> CardType.EVENT
            is GameplayWorkflowState.EventDiceGamble -> CardType.EVENT
            is GameplayWorkflowState.EventDrawScanRequired -> CardType.EVENT
            is GameplayWorkflowState.EventPropertyChoice -> CardType.PROPERTY
            is GameplayWorkflowState.PlayerInfo -> CardType.USER
            is GameplayWorkflowState.LocationWaitingForDestinationProperty -> CardType.PROPERTY
            else -> null
        }
    }

    fun cardIdFromId(cardId: String): CardType? = when {
        cardId.startsWith("USR_") -> CardType.USER
        cardId.startsWith("PRP_") -> CardType.PROPERTY
        cardId.startsWith("EVT_") -> CardType.EVENT
        else -> null
    }

    fun showCardInteraction(
        workflowState: GameplayWorkflowState,
        result: GameplayResultUiModel?,
    ): Boolean = displayCardId(workflowState, result) != null
}
