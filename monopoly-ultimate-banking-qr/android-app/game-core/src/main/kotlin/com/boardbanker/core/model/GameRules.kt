package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class WinnerEndCondition {
    FIRST_PLAYER_BANKRUPT,
}

@Serializable
enum class WinnerDeterminationMode {
    HIGHEST_TOTAL_WEALTH,
    LAST_SOLVENT_PLAYER,
}

@Serializable
enum class WinnerTieBreaker {
    HIGHEST_VALUE_OWNED_PROPERTY,
}

@Serializable
enum class DebtResolutionMode {
    CASH_THEN_PROPERTY_TRANSFER,
    IMMEDIATE_BANKRUPTCY,
}

@Serializable
enum class DebtPropertyValuation {
    PURCHASE_PRICE,
}

@Serializable
enum class AuctionNoBidsBehaviour {
    CANCEL_REMAIN_UNOWNED,
}

@Serializable
enum class GoMovementCollectMode {
    COLLECT,
    DO_NOT_COLLECT,
}

@Serializable
enum class EventIncompleteActionBehaviour {
    DO_NOTHING,
    FAIL,
}

@Serializable
data class PlayerRules(
    val minimumPlayers: Int,
    val maximumPlayers: Int,
    val initialRentLevel: Int = 1,
    val duplicatePlayerCardAllowed: Boolean = false,
)

@Serializable
data class RentRules(
    val maximumRentLevel: Int,
    val minimumRentLevel: Int = 1,
    val jailedOwnerCannotCollectRent: Boolean = true,
    val jailedOwnerLandingDoesNotIncreaseRent: Boolean = true,
    val eventRentChangesAffectJailedOwnerProperties: Boolean = true,
)

@Serializable
data class ColourSetRules(
    val enabled: Boolean = true,
    val oneTimeOnly: Boolean = true,
    val singleOwnerBonus: Int,
    val multiOwnerBonus: Int,
    val clampAtMaximumRentLevel: Boolean = true,
)

@Serializable
data class UndoRules(
    val supported: Boolean = true,
    val undoDepth: Int = 1,
    val eligibleTransactionTypes: List<String> = emptyList(),
    val ineligibleTransactionTypes: List<String> = listOf("EVENT_APPLIED"),
    val blockedDuringDebtResolution: Boolean = true,
)

@Serializable
data class JailRulesConfig(
    val exitByPaymentAtTurnStart: Boolean = true,
    val exitByDoublesMaxAttempts: Int = 3,
    val whileInJailCannotCollectRent: Boolean = true,
    val whileInJailCannotGainLandingRentIncreases: Boolean = true,
    val whileInJailCannotBidInAuction: Boolean = true,
    val whileInJailCannotResolveBoardActions: Boolean = true,
    val totalGridlockJailPlayersRemain: Boolean = true,
)

@Serializable
data class AuctionRules(
    val requiredForUnownedLanding: Boolean = true,
    val arbitraryBidAmountsAllowed: Boolean = false,
    val jailedPlayersCannotBid: Boolean = true,
    val timedAuctionSeconds: Int = 30,
    val lastBidBeforeTimerWins: Boolean = true,
    val winnerInitialRentLevel: Int = 1,
    val noBidsBehaviour: AuctionNoBidsBehaviour = AuctionNoBidsBehaviour.CANCEL_REMAIN_UNOWNED,
)

@Serializable
data class DebtRulesConfig(
    val resolutionMode: DebtResolutionMode = DebtResolutionMode.CASH_THEN_PROPERTY_TRANSFER,
    val propertyValuation: DebtPropertyValuation = DebtPropertyValuation.PURCHASE_PRICE,
    val debtToPlayerTransferOwnership: Boolean = true,
    val debtToPlayerRetainRentLevel: Boolean = true,
    val debtToBankReturnToUnowned: Boolean = true,
    val debtToBankResetRentOnRepurchase: Boolean = true,
    val overpaymentReturnsChange: Boolean = true,
    val blocksUndo: Boolean = true,
)

@Serializable
data class GoRulesConfig(
    val normalDiceMovementCollectsGo: GoMovementCollectMode = GoMovementCollectMode.COLLECT,
    val eventMovementCollectsGo: GoMovementCollectMode = GoMovementCollectMode.DO_NOT_COLLECT,
    val locationMovementCollectsGo: GoMovementCollectMode = GoMovementCollectMode.DO_NOT_COLLECT,
    val goToJailMovementCollectsGo: GoMovementCollectMode = GoMovementCollectMode.DO_NOT_COLLECT,
    val threeDoublesJailMovementCollectsGo: GoMovementCollectMode = GoMovementCollectMode.DO_NOT_COLLECT,
    val suppressGoForTotalGridlock: Boolean = true,
)

@Serializable
data class WinnerRules(
    val endCondition: WinnerEndCondition = WinnerEndCondition.FIRST_PLAYER_BANKRUPT,
    val winnerDetermination: WinnerDeterminationMode = WinnerDeterminationMode.HIGHEST_TOTAL_WEALTH,
    val wealthUsesRentLevel: Boolean = false,
    val tieBreaker: WinnerTieBreaker = WinnerTieBreaker.HIGHEST_VALUE_OWNED_PROPERTY,
)

@Serializable
data class EventEngineRulesConfig(
    val ownedPropertiesOnlyForRentChanges: Boolean = true,
    val incompleteActionBehaviour: EventIncompleteActionBehaviour = EventIncompleteActionBehaviour.DO_NOTHING,
)

@Serializable
data class TemporaryEffectRules(
    val evt13RemainingUses: Int = 2,
    val evt13EffectType: String = "FORCE_LEVEL_1_RENT",
)

@Serializable
data class GameRules(
    val schemaVersion: Int = 2,
    val setup: PlayerRules,
    val rent: RentRules,
    val colourSets: ColourSetRules,
    val undo: UndoRules,
    val jail: JailRulesConfig,
    val auction: AuctionRules,
    val debt: DebtRulesConfig,
    val go: GoRulesConfig,
    val winner: WinnerRules,
    val eventEngine: EventEngineRulesConfig = EventEngineRulesConfig(),
    val temporaryEffects: TemporaryEffectRules = TemporaryEffectRules(),
) {
    val minimumPlayers: Int get() = setup.minimumPlayers
    val maximumPlayers: Int get() = setup.maximumPlayers
    val maximumRentLevel: Int get() = rent.maximumRentLevel
    val minimumRentLevel: Int get() = rent.minimumRentLevel
    val singleOwnerColorBonus: Int get() = colourSets.singleOwnerBonus
    val multiOwnerColorBonus: Int get() = colourSets.multiOwnerBonus
    val undoDepth: Int get() = undo.undoDepth
}
