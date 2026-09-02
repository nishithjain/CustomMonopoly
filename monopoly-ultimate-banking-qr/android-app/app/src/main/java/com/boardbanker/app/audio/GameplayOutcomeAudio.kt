package com.boardbanker.app.audio

import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType

enum class GameplayAudioCue {
    GAME_STARTS,
    PROPERTY_PURCHASED,
    COLOR_SET_COMPLETE,
    RENT_TRANSFER,
    RENT_LEVEL_INCREASED,
    RENT_LEVEL_DECREASED,
    GO,
    GO_TO_JAIL,
    KA_CHING,
    MONEY_LOST,
    UNDO,
    UNDO_LAST_ACTION,
    LOST_GAME,
    WINNER,
    AUCTION_BEGINS,
    AUCTION_ENDING,
    JAIL_WORKFLOW,
}

sealed class CommitAudioTrigger {
    data class GameWorkflow(val context: WorkflowCommandContext) : CommitAudioTrigger()
    data class Banking(val command: GameCommand) : CommitAudioTrigger()
    data object AuctionStarted : CommitAudioTrigger()
    data object AuctionEnding : CommitAudioTrigger()
    data object GameStarted : CommitAudioTrigger()
    data object Bankruptcy : CommitAudioTrigger()
    data object DebtSettled : CommitAudioTrigger()
    data object WinnerPresentation : CommitAudioTrigger()
    data class JailWorkflowEntered(val playerId: String) : CommitAudioTrigger()
}

/**
 * Maps committed [GameResult] outcomes to semantic gameplay audio cues.
 *
 * Audio reacts to engine outcomes — it never decides game rules.
 */
object GameplayOutcomeAudio {
    fun resolveCue(
        result: GameResult,
        sessionBefore: GameSession,
        trigger: CommitAudioTrigger,
    ): GameplayAudioCue? {
        if (!result.isSuccess) return null

        return when (trigger) {
            CommitAudioTrigger.GameStarted ->
                if (result.transactions.any { it.transactionType == TransactionType.GAME_START }) {
                    GameplayAudioCue.GAME_STARTS
                } else {
                    null
                }
            CommitAudioTrigger.Bankruptcy -> GameplayAudioCue.LOST_GAME
            CommitAudioTrigger.WinnerPresentation -> GameplayAudioCue.WINNER
            CommitAudioTrigger.AuctionStarted -> GameplayAudioCue.AUCTION_BEGINS
            CommitAudioTrigger.AuctionEnding -> GameplayAudioCue.AUCTION_ENDING
            is CommitAudioTrigger.JailWorkflowEntered -> GameplayAudioCue.JAIL_WORKFLOW
            is CommitAudioTrigger.Banking -> resolveBankingCue(result, sessionBefore, trigger.command)
            is CommitAudioTrigger.GameWorkflow -> resolveWorkflowCue(result, sessionBefore, trigger.context)
            CommitAudioTrigger.DebtSettled -> resolveDebtCue(result, sessionBefore)
        }
    }

    fun playCommittedOutcome(
        audio: GameAudioFeedback,
        result: GameResult,
        sessionBefore: GameSession,
        trigger: CommitAudioTrigger,
    ) {
        val cue = resolveCue(result, sessionBefore, trigger) ?: return
        playCue(audio, cue)
    }

    fun playCue(audio: GameAudioFeedback, cue: GameplayAudioCue) {
        when (cue) {
            GameplayAudioCue.GAME_STARTS -> audio.playGameStarted()
            GameplayAudioCue.PROPERTY_PURCHASED -> audio.playPropertyPurchased()
            GameplayAudioCue.COLOR_SET_COMPLETE -> audio.playColorSetComplete()
            GameplayAudioCue.RENT_TRANSFER -> audio.playRentTransfer()
            GameplayAudioCue.RENT_LEVEL_INCREASED -> audio.playRentLevelIncreased()
            GameplayAudioCue.RENT_LEVEL_DECREASED -> audio.playRentLevelDecreased()
            GameplayAudioCue.GO -> audio.playGo()
            GameplayAudioCue.GO_TO_JAIL -> audio.playGoToJail()
            GameplayAudioCue.KA_CHING -> audio.playKaChing()
            GameplayAudioCue.MONEY_LOST -> audio.playMoneyLost()
            GameplayAudioCue.UNDO -> audio.playUndo()
            GameplayAudioCue.UNDO_LAST_ACTION -> audio.playUndoLastAction()
            GameplayAudioCue.LOST_GAME -> audio.playLostGame()
            GameplayAudioCue.WINNER -> audio.playWinner()
            GameplayAudioCue.AUCTION_BEGINS -> audio.playAuctionBegins()
            GameplayAudioCue.AUCTION_ENDING -> audio.playAuctionEnding()
            GameplayAudioCue.JAIL_WORKFLOW -> audio.playJail()
        }
    }

    private fun resolveBankingCue(
        result: GameResult,
        sessionBefore: GameSession,
        command: GameCommand,
    ): GameplayAudioCue? = when (command) {
        is GameCommand.PayGoSalary -> GameplayAudioCue.GO
        is GameCommand.PayLocationFee -> GameplayAudioCue.MONEY_LOST
        is GameCommand.SendPlayerToJail -> GameplayAudioCue.GO_TO_JAIL
        is GameCommand.PayJailFee -> GameplayAudioCue.KA_CHING
        is GameCommand.UseGetOutOfJailPass -> GameplayAudioCue.JAIL_WORKFLOW
        is GameCommand.UndoLastAction ->
            if (result.transactions.any { it.transactionType == TransactionType.UNDO }) {
                GameplayAudioCue.UNDO_LAST_ACTION
            } else {
                null
            }
        else -> null
    }

    private fun resolveDebtCue(result: GameResult, sessionBefore: GameSession): GameplayAudioCue? {
        val cashPaid = result.transactions
            .filter {
                it.transactionType == TransactionType.BANK_DEBIT ||
                    it.transactionType == TransactionType.RENT_PAYMENT
            }
            .sumOf { it.amount ?: 0 }
        return if (cashPaid > 0) GameplayAudioCue.MONEY_LOST else null
    }

    private fun resolveWorkflowCue(
        result: GameResult,
        sessionBefore: GameSession,
        context: WorkflowCommandContext,
    ): GameplayAudioCue? {
        val newlyJailed = playersNewlyInJail(sessionBefore, result)
        if (newlyJailed.isNotEmpty()) {
            return GameplayAudioCue.GO_TO_JAIL
        }

        return when (context) {
            is WorkflowCommandContext.Purchase -> resolvePurchaseCue(result)
            is WorkflowCommandContext.PropertyLanding -> resolvePropertyLandingCue(result, context, sessionBefore)
            is WorkflowCommandContext.ApplyEvent -> resolveEventCue(context.eventId, result, sessionBefore)
            is WorkflowCommandContext.EventChoice -> resolveEventCue(context.eventId, result, sessionBefore)
            is WorkflowCommandContext.ResolvePendingEventDraw -> resolveEventCue(context.eventId, result, sessionBefore)
            is WorkflowCommandContext.RollEventDice -> resolveDiceGambleCue(result)
        }
    }

    private fun resolveDiceGambleCue(result: GameResult): GameplayAudioCue? {
        if (result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT }) {
            return GameplayAudioCue.KA_CHING
        }
        if (result.transactions.any { it.transactionType == TransactionType.BANK_DEBIT }) {
            return GameplayAudioCue.MONEY_LOST
        }
        return null
    }

    private fun resolvePurchaseCue(result: GameResult): GameplayAudioCue? {
        if (result.transactions.any { it.transactionType == TransactionType.COLOR_SET_COMPLETION_BONUS }) {
            return GameplayAudioCue.COLOR_SET_COMPLETE
        }
        if (result.transactions.any { it.transactionType == TransactionType.PROPERTY_PURCHASE }) {
            return GameplayAudioCue.PROPERTY_PURCHASED
        }
        return null
    }

    private fun resolvePropertyLandingCue(
        result: GameResult,
        context: WorkflowCommandContext.PropertyLanding,
        sessionBefore: GameSession,
    ): GameplayAudioCue? {
        if (result.transactions.any { it.transactionType == TransactionType.RENT_PAYMENT }) {
            return GameplayAudioCue.RENT_TRANSFER
        }
        val ownerId = sessionBefore.properties[context.propertyId]?.ownerPlayerId
        if (ownerId == context.playerId && rentLevelsIncreased(sessionBefore, result)) {
            return GameplayAudioCue.RENT_LEVEL_INCREASED
        }
        return null
    }

    private fun resolveEventCue(
        eventId: String,
        result: GameResult,
        sessionBefore: GameSession,
    ): GameplayAudioCue? {
        val rentIncreased = rentLevelsIncreased(sessionBefore, result)
        val rentDecreased = rentLevelsDecreased(sessionBefore, result)
        val propertyPurchased = result.transactions.any { it.transactionType == TransactionType.PROPERTY_PURCHASE }
        val bankCredit = result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT }
        val bankDebit = result.transactions.any { it.transactionType == TransactionType.BANK_DEBIT }

        return when (eventId) {
            "EVT_01", "EVT_03", "EVT_18" -> when {
                propertyPurchased -> GameplayAudioCue.PROPERTY_PURCHASED
                rentIncreased -> GameplayAudioCue.RENT_LEVEL_INCREASED
                else -> null
            }
            "EVT_02", "EVT_05", "EVT_12", "EVT_17", "EVT_22" ->
                if (rentIncreased) GameplayAudioCue.RENT_LEVEL_INCREASED else null
            "EVT_04", "EVT_15", "EVT_19", "EVT_20" ->
                if (rentDecreased) GameplayAudioCue.RENT_LEVEL_DECREASED else null
            "EVT_16" ->
                if (rentDecreased) GameplayAudioCue.RENT_LEVEL_DECREASED else null
            "EVT_08", "EVT_10" ->
                if (rentIncreased) GameplayAudioCue.RENT_LEVEL_INCREASED else null
            "EVT_07" ->
                if (bankDebit) GameplayAudioCue.MONEY_LOST else null
            "EVT_11", "EVT_23" ->
                if (bankCredit) GameplayAudioCue.KA_CHING else null
            else -> null
        }
    }

    internal fun rentLevelsIncreased(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.properties.keys.any { propertyId ->
            val before = sessionBefore.properties[propertyId]?.currentRentLevel ?: return@any false
            val after = result.session.properties[propertyId]?.currentRentLevel ?: before
            after > before
        }

    internal fun rentLevelsDecreased(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.properties.keys.any { propertyId ->
            val before = sessionBefore.properties[propertyId]?.currentRentLevel ?: return@any false
            val after = result.session.properties[propertyId]?.currentRentLevel ?: before
            after < before
        }

    internal fun playersNewlyInJail(sessionBefore: GameSession, result: GameResult): List<String> =
        sessionBefore.players.filter { (playerId, player) ->
            !player.jailStatus && result.session.players[playerId]?.jailStatus == true
        }.keys.toList()
}
