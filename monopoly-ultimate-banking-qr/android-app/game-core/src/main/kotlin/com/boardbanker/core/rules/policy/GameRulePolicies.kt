package com.boardbanker.core.rules.policy

import com.boardbanker.core.model.AuctionRules
import com.boardbanker.core.model.DebtRulesConfig
import com.boardbanker.core.model.GameRules
import com.boardbanker.core.model.GoRulesConfig
import com.boardbanker.core.model.JailRulesConfig
import com.boardbanker.core.model.PlayerRules
import com.boardbanker.core.model.RentRules
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.model.UndoRules
import com.boardbanker.core.model.WinnerRules

class PlayerPolicy(private val rules: PlayerRules) {
    fun minimumPlayers(): Int = rules.minimumPlayers
    fun maximumPlayers(): Int = rules.maximumPlayers
    fun initialRentLevel(): Int = rules.initialRentLevel
    fun duplicatePlayerCardAllowed(): Boolean = rules.duplicatePlayerCardAllowed
}

class RentPolicy(private val rules: RentRules) {
    fun maximumRentLevel(): Int = rules.maximumRentLevel
    fun minimumRentLevel(): Int = rules.minimumRentLevel
    fun jailedOwnerCannotCollectRent(): Boolean = rules.jailedOwnerCannotCollectRent
    fun jailedOwnerLandingDoesNotIncreaseRent(): Boolean = rules.jailedOwnerLandingDoesNotIncreaseRent
    fun eventRentChangesAffectJailedOwnerProperties(): Boolean = rules.eventRentChangesAffectJailedOwnerProperties
}

class ColourSetPolicy(private val rules: com.boardbanker.core.model.ColourSetRules) {
    fun enabled(): Boolean = rules.enabled
    fun oneTimeOnly(): Boolean = rules.oneTimeOnly
    fun singleOwnerBonus(): Int = rules.singleOwnerBonus
    fun multiOwnerBonus(): Int = rules.multiOwnerBonus
    fun clampAtMaximumRentLevel(): Boolean = rules.clampAtMaximumRentLevel
}

class UndoPolicy(private val rules: UndoRules) {
    private val eligibleTypes = rules.eligibleTransactionTypes.map { TransactionType.valueOf(it) }.toSet()
    private val ineligibleTypes = rules.ineligibleTransactionTypes.map { TransactionType.valueOf(it) }.toSet()

    fun supported(): Boolean = rules.supported
    fun undoDepth(): Int = rules.undoDepth
    fun blockedDuringDebtResolution(): Boolean = rules.blockedDuringDebtResolution
    fun isEligible(type: TransactionType): Boolean = eligibleTypes.contains(type)
    fun isIneligible(type: TransactionType): Boolean = ineligibleTypes.contains(type)
}

class JailPolicy(private val rules: JailRulesConfig) {
    fun exitByPaymentAtTurnStart(): Boolean = rules.exitByPaymentAtTurnStart
    fun exitByDoublesMaxAttempts(): Int = rules.exitByDoublesMaxAttempts
    fun whileInJailCannotCollectRent(): Boolean = rules.whileInJailCannotCollectRent
    fun whileInJailCannotGainLandingRentIncreases(): Boolean = rules.whileInJailCannotGainLandingRentIncreases
    fun whileInJailCannotBidInAuction(): Boolean = rules.whileInJailCannotBidInAuction
    fun totalGridlockJailPlayersRemain(): Boolean = rules.totalGridlockJailPlayersRemain
}

class AuctionPolicy(private val rules: AuctionRules) {
    fun requiredForUnownedLanding(): Boolean = rules.requiredForUnownedLanding
    fun arbitraryBidAmountsAllowed(): Boolean = rules.arbitraryBidAmountsAllowed
    fun jailedPlayersCannotBid(): Boolean = rules.jailedPlayersCannotBid
    fun timedAuctionSeconds(): Int = rules.timedAuctionSeconds
    fun lastBidBeforeTimerWins(): Boolean = rules.lastBidBeforeTimerWins
    fun winnerInitialRentLevel(): Int = rules.winnerInitialRentLevel
}

class DebtPolicy(private val rules: DebtRulesConfig) {
    fun overpaymentReturnsChange(): Boolean = rules.overpaymentReturnsChange
    fun debtToPlayerTransferOwnership(): Boolean = rules.debtToPlayerTransferOwnership
    fun debtToPlayerRetainRentLevel(): Boolean = rules.debtToPlayerRetainRentLevel
    fun debtToBankReturnToUnowned(): Boolean = rules.debtToBankReturnToUnowned
    fun debtToBankResetRentOnRepurchase(): Boolean = rules.debtToBankResetRentOnRepurchase
    fun blocksUndo(): Boolean = rules.blocksUndo
}

class GoPolicy(private val rules: GoRulesConfig) {
    fun collectsGoForNormalDice(): Boolean = rules.normalDiceMovementCollectsGo.collects()
    fun collectsGoForEventMovement(): Boolean = rules.eventMovementCollectsGo.collects()
    fun collectsGoForLocationMovement(): Boolean = rules.locationMovementCollectsGo.collects()
    fun suppressGoForTotalGridlock(): Boolean = rules.suppressGoForTotalGridlock
}

class WinnerPolicy(private val rules: WinnerRules) {
    fun wealthUsesRentLevel(): Boolean = rules.wealthUsesRentLevel
}

class GameRulePolicies(rules: GameRules) {
    val player = PlayerPolicy(rules.setup)
    val rent = RentPolicy(rules.rent)
    val colourSets = ColourSetPolicy(rules.colourSets)
    val undo = UndoPolicy(rules.undo)
    val jail = JailPolicy(rules.jail)
    val auction = AuctionPolicy(rules.auction)
    val debt = DebtPolicy(rules.debt)
    val go = GoPolicy(rules.go)
    val winner = WinnerPolicy(rules.winner)
    val temporaryEffects = rules.temporaryEffects
    val eventEngine = rules.eventEngine
}

private fun com.boardbanker.core.model.GoMovementCollectMode.collects(): Boolean =
    this == com.boardbanker.core.model.GoMovementCollectMode.COLLECT
