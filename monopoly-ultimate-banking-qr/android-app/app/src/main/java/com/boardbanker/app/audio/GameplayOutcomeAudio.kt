package com.boardbanker.app.audio

import com.boardbanker.app.gameplay.workflow.WorkflowCommandContext
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.TransactionType

enum class GameplayAudioCue {
    GAME_STARTS,
    PROPERTY_PURCHASED,
    ENERGY_GRID_PURCHASED,
    COLOR_SET_COMPLETE,
    RENT_TRANSFER,
    RENT_RELIEF,
    RENT_LEVEL_INCREASED,
    RENT_LEVEL_DECREASED,
    PROPERTY_SOLD,
    GO,
    LOCATION,
    GO_TO_JAIL,
    JAIL_RELEASE,
    JAIL_PASS,
    BANK_CREDIT,
    BANK_DEBIT,
    MONEY_TRANSFER,
    DICE_ROLL,
    MOVE_PLAYER,
    LUCKY_DRAW,
    EVENT_APPLIED,
    TURN_CHANGED,
    TURN_SKIPPED,
    EXTRA_TURN,
    UNDO,
    UNDO_LAST_ACTION,
    LOST_GAME,
    WINNER,
    AUCTION_BEGINS,
    AUCTION_ENDING,
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
    ): GameplayAudioCue? = resolveCues(result, sessionBefore, trigger).firstOrNull()

    fun resolveCues(
        result: GameResult,
        sessionBefore: GameSession,
        trigger: CommitAudioTrigger,
    ): List<GameplayAudioCue> {
        if (!result.isSuccess) return emptyList()

        return when (trigger) {
            CommitAudioTrigger.GameStarted ->
                if (result.transactions.any { it.transactionType == TransactionType.GAME_START }) {
                    listOf(GameplayAudioCue.GAME_STARTS)
                } else {
                    emptyList()
                }
            CommitAudioTrigger.Bankruptcy -> listOf(GameplayAudioCue.LOST_GAME)
            CommitAudioTrigger.WinnerPresentation -> listOf(GameplayAudioCue.WINNER)
            CommitAudioTrigger.AuctionStarted -> resolveAuctionStartedCues(result, sessionBefore)
            CommitAudioTrigger.AuctionEnding -> resolveAuctionEndingCues(result, sessionBefore)
            is CommitAudioTrigger.Banking -> resolveBankingCues(result, sessionBefore, trigger.command)
            is CommitAudioTrigger.GameWorkflow -> resolveWorkflowCues(result, sessionBefore, trigger.context)
            CommitAudioTrigger.DebtSettled ->
                resolvePrimaryOutcomeCue(result, sessionBefore)?.let { listOf(it) } ?: emptyList()
        }
    }

    fun playCommittedOutcome(
        audio: GameAudioFeedback,
        result: GameResult,
        sessionBefore: GameSession,
        trigger: CommitAudioTrigger,
    ) {
        playCues(audio, resolveCues(result, sessionBefore, trigger))
    }

    fun playCues(audio: GameAudioFeedback, cues: List<GameplayAudioCue>) {
        if (cues.isEmpty()) return
        val steps = cues.map { cue -> { playCue(audio, cue) } }
        if (steps.size == 1) {
            steps.first().invoke()
        } else {
            audio.playSoundSequence(steps, GameAudioFeedback.DEFAULT_SEQUENCE_GAP_MS)
        }
    }

    fun playCue(audio: GameAudioFeedback, cue: GameplayAudioCue) {
        when (cue) {
            GameplayAudioCue.GAME_STARTS -> audio.playGameStarted()
            GameplayAudioCue.PROPERTY_PURCHASED -> audio.playPropertyPurchased()
            GameplayAudioCue.ENERGY_GRID_PURCHASED -> audio.playEnergyGridPurchased()
            GameplayAudioCue.COLOR_SET_COMPLETE -> audio.playColorSetComplete()
            GameplayAudioCue.RENT_TRANSFER -> audio.playRentTransfer()
            GameplayAudioCue.RENT_RELIEF -> audio.playRentRelief()
            GameplayAudioCue.RENT_LEVEL_INCREASED -> audio.playRentLevelIncreased()
            GameplayAudioCue.RENT_LEVEL_DECREASED -> audio.playRentLevelDecreased()
            GameplayAudioCue.PROPERTY_SOLD -> audio.playPropertySold()
            GameplayAudioCue.GO -> audio.playGo()
            GameplayAudioCue.LOCATION -> audio.playLocation()
            GameplayAudioCue.GO_TO_JAIL -> audio.playGoToJail()
            GameplayAudioCue.JAIL_RELEASE -> audio.playJailRelease()
            GameplayAudioCue.JAIL_PASS -> audio.playJailPass()
            GameplayAudioCue.BANK_CREDIT -> audio.playBankCredit()
            GameplayAudioCue.BANK_DEBIT -> audio.playBankDebit()
            GameplayAudioCue.MONEY_TRANSFER -> audio.playMoneyTransfer()
            GameplayAudioCue.DICE_ROLL -> audio.playDiceRoll()
            GameplayAudioCue.MOVE_PLAYER -> audio.playMovePlayer()
            GameplayAudioCue.LUCKY_DRAW -> audio.playLuckyDraw()
            GameplayAudioCue.EVENT_APPLIED -> audio.playEventApplied()
            GameplayAudioCue.TURN_CHANGED -> audio.playTurnChanged()
            GameplayAudioCue.TURN_SKIPPED -> audio.playTurnSkipped()
            GameplayAudioCue.EXTRA_TURN -> audio.playExtraTurn()
            GameplayAudioCue.UNDO -> audio.playUndo()
            GameplayAudioCue.UNDO_LAST_ACTION -> audio.playUndoLastAction()
            GameplayAudioCue.LOST_GAME -> audio.playLostGame()
            GameplayAudioCue.WINNER -> audio.playWinner()
            GameplayAudioCue.AUCTION_BEGINS -> audio.playAuctionBegins()
            GameplayAudioCue.AUCTION_ENDING -> audio.playAuctionEnding()
        }
    }

    /**
     * Selects one primary asset/rent sound for a completed command using Batch 2 priority rules.
     */
    fun resolvePrimaryAssetCue(result: GameResult, sessionBefore: GameSession): GameplayAudioCue? {
        if (!result.isSuccess) return null

        if (result.transactions.any { it.transactionType == TransactionType.RENT_WAIVED }) {
            return GameplayAudioCue.RENT_RELIEF
        }
        if (result.transactions.any { it.transactionType == TransactionType.COLOR_SET_COMPLETION_BONUS }) {
            return GameplayAudioCue.COLOR_SET_COMPLETE
        }
        if (result.transactions.any { it.transactionType == TransactionType.PROPERTY_PURCHASE }) {
            return GameplayAudioCue.PROPERTY_PURCHASED
        }
        if (result.transactions.any { it.transactionType == TransactionType.ENERGY_GRID_PURCHASE }) {
            return GameplayAudioCue.ENERGY_GRID_PURCHASED
        }
        if (result.transactions.any {
                it.transactionType == TransactionType.RENT_PAYMENT && !isNonRentPlayerTransfer(result)
            }
        ) {
            return GameplayAudioCue.RENT_TRANSFER
        }
        if (rentLevelsIncreased(sessionBefore, result)) {
            return GameplayAudioCue.RENT_LEVEL_INCREASED
        }
        if (rentLevelsDecreased(sessionBefore, result)) {
            return GameplayAudioCue.RENT_LEVEL_DECREASED
        }
        if (propertySoldToBank(sessionBefore, result)) {
            return GameplayAudioCue.PROPERTY_SOLD
        }
        return null
    }

    /**
     * Selects one primary sound for a completed banking, money, jail, or event command.
     */
    fun resolvePrimaryOutcomeCue(
        result: GameResult,
        sessionBefore: GameSession,
        command: GameCommand? = null,
    ): GameplayAudioCue? {
        if (!result.isSuccess) return null

        resolvePrimaryAssetCue(result, sessionBefore)?.let { return it }

        if (isGoCollection(result, command)) {
            return GameplayAudioCue.GO
        }
        if (command is GameCommand.PayLocationFee &&
            result.transactions.any { it.transactionType == TransactionType.LOCATION_FEE }
        ) {
            return GameplayAudioCue.LOCATION
        }
        if (playersNewlyInJail(sessionBefore, result).isNotEmpty()) {
            return GameplayAudioCue.GO_TO_JAIL
        }
        if (playersReleasedFromJail(sessionBefore, result).isNotEmpty()) {
            return if (result.transactions.any { it.transactionType == TransactionType.JAIL_PASS_USED }) {
                GameplayAudioCue.JAIL_PASS
            } else {
                GameplayAudioCue.JAIL_RELEASE
            }
        }
        if (jailPassGranted(sessionBefore, result)) {
            return GameplayAudioCue.JAIL_PASS
        }
        if (hasNonRentPlayerTransfer(result)) {
            return GameplayAudioCue.MONEY_TRANSFER
        }
        if (hasEligibleBankCredit(result, command)) {
            return GameplayAudioCue.BANK_CREDIT
        }
        if (hasEligibleBankDebit(result, command)) {
            return GameplayAudioCue.BANK_DEBIT
        }
        return null
    }

    fun resolveTurnChangeCue(result: GameResult): GameplayAudioCue? {
        if (!result.isSuccess) return null
        if (result.transactions.any { it.transactionType == TransactionType.EXTRA_TURN_STARTED }) {
            return GameplayAudioCue.EXTRA_TURN
        }
        if (result.transactions.any { it.transactionType == TransactionType.TURN_SKIPPED }) {
            return GameplayAudioCue.TURN_SKIPPED
        }
        return if (result.transactions.any { it.transactionType == TransactionType.TURN_ADVANCED }) {
            GameplayAudioCue.TURN_CHANGED
        } else {
            null
        }
    }

    fun resolveLuckyBreakRollCues(result: GameResult): List<GameplayAudioCue> {
        if (!result.isSuccess) return emptyList()
        val cues = mutableListOf(GameplayAudioCue.DICE_ROLL)
        if (result.session.pendingDiceGamble == null) {
            when {
                result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT } ->
                    cues += GameplayAudioCue.BANK_CREDIT
                result.transactions.any { it.transactionType == TransactionType.BANK_DEBIT } ->
                    cues += GameplayAudioCue.BANK_DEBIT
            }
        }
        return cues
    }

    fun resolveAuctionStartedCues(result: GameResult, sessionBefore: GameSession): List<GameplayAudioCue> {
        if (!result.isSuccess) return emptyList()
        return if (sessionBefore.auction == null && result.session.auction != null) {
            listOf(GameplayAudioCue.AUCTION_BEGINS)
        } else {
            emptyList()
        }
    }

    fun resolveAuctionEndingCues(result: GameResult, sessionBefore: GameSession): List<GameplayAudioCue> {
        if (!result.isSuccess) return emptyList()
        if (result.transactions.any { it.transactionType == TransactionType.COLOR_SET_COMPLETION_BONUS }) {
            return listOf(GameplayAudioCue.COLOR_SET_COMPLETE)
        }
        if (result.transactions.any { it.transactionType == TransactionType.AUCTION_WIN }) {
            return listOf(GameplayAudioCue.AUCTION_ENDING)
        }
        if (sessionBefore.auction != null && result.session.auction == null) {
            return listOf(GameplayAudioCue.AUCTION_ENDING)
        }
        return emptyList()
    }

    internal fun playersNewlyBankrupt(sessionBefore: GameSession, result: GameResult): List<String> =
        result.session.players.filter { (playerId, after) ->
            val before = sessionBefore.players[playerId]
            before != null && !before.bankrupt && after.bankrupt
        }.keys.toList()

    private fun resolveBankingCues(
        result: GameResult,
        sessionBefore: GameSession,
        command: GameCommand,
    ): List<GameplayAudioCue> = when (command) {
        is GameCommand.EndTurn ->
            resolveTurnChangeCue(result)?.let { listOf(it) } ?: emptyList()
        is GameCommand.UndoLastAction ->
            if (result.transactions.any { it.transactionType == TransactionType.UNDO }) {
                listOf(GameplayAudioCue.UNDO)
            } else {
                emptyList()
            }
        else -> resolvePrimaryOutcomeCue(result, sessionBefore, command)?.let { listOf(it) } ?: emptyList()
    }

    private fun resolveWorkflowCues(
        result: GameResult,
        sessionBefore: GameSession,
        context: WorkflowCommandContext,
    ): List<GameplayAudioCue> = when (context) {
        is WorkflowCommandContext.RollEventDice -> resolveLuckyBreakRollCues(result)
        is WorkflowCommandContext.ApplyEvent -> resolveEventApplyCues(result, sessionBefore, context.eventId)
        is WorkflowCommandContext.ResolvePendingEventDraw ->
            resolvePrimaryOutcomeCue(result, sessionBefore)?.let { listOf(it) }
                ?: eventAppliedFallbackCue(result)?.let { listOf(it) }
                ?: emptyList()
        else ->
            resolvePrimaryOutcomeCue(result, sessionBefore)?.let { listOf(it) } ?: emptyList()
    }

    private fun resolveEventApplyCues(
        result: GameResult,
        sessionBefore: GameSession,
        eventId: String,
    ): List<GameplayAudioCue> {
        resolvePrimaryAssetCue(result, sessionBefore)?.let { return listOf(it) }

        if (luckyDrawCreated(sessionBefore, result)) {
            return listOf(GameplayAudioCue.LUCKY_DRAW)
        }

        if (isPlayerMovement(result)) {
            val cues = mutableListOf(GameplayAudioCue.MOVE_PLAYER)
            if (shouldSequenceGoAfterMovement(result)) {
                cues += GameplayAudioCue.GO
            }
            return cues
        }

        if (extraTurnGranted(result)) {
            return listOf(GameplayAudioCue.EVENT_APPLIED)
        }
        if (skipTurnGranted(sessionBefore, result)) {
            return listOf(GameplayAudioCue.EVENT_APPLIED)
        }

        resolvePrimaryOutcomeCue(result, sessionBefore)?.let { return listOf(it) }

        return eventAppliedFallbackCue(result)?.let { listOf(it) } ?: emptyList()
    }

    private fun eventAppliedFallbackCue(result: GameResult): GameplayAudioCue? =
        if (result.transactions.any { it.transactionType == TransactionType.EVENT_APPLIED }) {
            GameplayAudioCue.EVENT_APPLIED
        } else {
            null
        }

    internal fun luckyDrawCreated(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.pendingEventDraw == null && result.session.pendingEventDraw != null

    internal fun isPlayerMovement(result: GameResult): Boolean =
        result.physicalActions.any { action ->
            val instruction = action.instruction
            instruction.contains("Move backward", ignoreCase = true) ||
                (
                    instruction.contains("Move forward to", ignoreCase = true) &&
                        !instruction.contains("Move directly to GO", ignoreCase = true)
                )
        }

    private fun shouldSequenceGoAfterMovement(result: GameResult): Boolean =
        isPlayerMovement(result) &&
            result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT }

    internal fun extraTurnGranted(result: GameResult): Boolean =
        result.transactions.any { it.transactionType == TransactionType.EXTRA_TURN_GRANTED }

    internal fun skipTurnGranted(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.players.any { (playerId, before) ->
            val after = result.session.players[playerId] ?: return@any false
            after.pendingSkipTurnCount > before.pendingSkipTurnCount
        }

    internal fun isGoCollection(result: GameResult, command: GameCommand?): Boolean {
        if (!result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT }) {
            return false
        }
        if (command is GameCommand.PayGoSalary) {
            return true
        }
        return result.physicalActions.any { action ->
            action.targetSpace == "GO" ||
                action.instruction.contains("Move directly to GO", ignoreCase = true) ||
                action.instruction.contains("passing GO", ignoreCase = true)
        }
    }

    internal fun hasNonRentPlayerTransfer(result: GameResult): Boolean =
        result.transactions.any { transaction ->
            transaction.transactionType == TransactionType.EVENT_PLAYER_TRANSFER ||
                (
                    transaction.transactionType == TransactionType.RENT_PAYMENT &&
                        transaction.propertyId == null &&
                        transaction.fromEntity != null &&
                        transaction.toEntity != null &&
                        transaction.fromEntity != EntityRef.BANK &&
                        transaction.toEntity != EntityRef.BANK
                    )
        }

    private fun isNonRentPlayerTransfer(result: GameResult): Boolean = hasNonRentPlayerTransfer(result)

    private fun hasEligibleBankCredit(result: GameResult, command: GameCommand?): Boolean {
        if (!result.transactions.any { it.transactionType == TransactionType.BANK_CREDIT }) {
            return false
        }
        if (result.transactions.any { it.transactionType == TransactionType.GAME_START }) {
            return false
        }
        if (isGoCollection(result, command)) {
            return false
        }
        return true
    }

    private fun hasEligibleBankDebit(result: GameResult, command: GameCommand?): Boolean {
        if (!result.transactions.any { it.transactionType == TransactionType.BANK_DEBIT }) {
            return false
        }
        if (command is GameCommand.PayJailFee) {
            return false
        }
        if (command is GameCommand.PayLocationFee) {
            return false
        }
        return true
    }

    internal fun propertySoldToBank(sessionBefore: GameSession, result: GameResult): Boolean {
        val creditorTransfers = result.transactions
            .filter { it.transactionType == TransactionType.PROPERTY_OWNERSHIP_CHANGE }
            .mapNotNull { it.toEntity }
            .toSet()
        return sessionBefore.properties.any { (propertyId, beforeState) ->
            val previousOwner = beforeState.ownerPlayerId
            val afterOwner = result.session.properties[propertyId]?.ownerPlayerId
            previousOwner != null &&
                afterOwner == null &&
                previousOwner !in creditorTransfers
        }
    }

    internal fun rentLevelsIncreased(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.properties.keys.any { propertyId ->
            val before = sessionBefore.properties[propertyId] ?: return@any false
            val after = result.session.properties[propertyId] ?: return@any false
            if (before.ownerPlayerId != null && after.ownerPlayerId == null) return@any false
            after.currentRentLevel > before.currentRentLevel
        }

    internal fun rentLevelsDecreased(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.properties.keys.any { propertyId ->
            val before = sessionBefore.properties[propertyId] ?: return@any false
            val after = result.session.properties[propertyId] ?: return@any false
            if (before.ownerPlayerId != null && after.ownerPlayerId == null) return@any false
            after.currentRentLevel < before.currentRentLevel
        }

    internal fun playersNewlyInJail(sessionBefore: GameSession, result: GameResult): List<String> =
        sessionBefore.players.filter { (playerId, player) ->
            !player.jailStatus && result.session.players[playerId]?.jailStatus == true
        }.keys.toList()

    internal fun playersReleasedFromJail(sessionBefore: GameSession, result: GameResult): List<String> =
        sessionBefore.players.filter { (playerId, player) ->
            player.jailStatus && result.session.players[playerId]?.jailStatus == false
        }.keys.toList()

    internal fun jailPassGranted(sessionBefore: GameSession, result: GameResult): Boolean =
        sessionBefore.players.any { (playerId, before) ->
            val after = result.session.players[playerId] ?: return@any false
            after.jailPassCount > before.jailPassCount
        }
}
