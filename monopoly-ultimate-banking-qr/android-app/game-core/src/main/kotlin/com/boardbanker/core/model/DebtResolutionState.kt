package com.boardbanker.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DebtReason {
    RENT,
    JAIL,
    PURCHASE,
    LOCATION,
    EVENT,
    EVENT_CONTRIBUTOR,
    GENERIC,
}
@Serializable
enum class DebtAssetSettlementMethod {
    TRANSFER_TO_CREDITOR,
    SELL_TO_BANK,
}

@Serializable
data class DebtResolutionState(
    val debtorPlayerId: String,
    val creditorPlayerId: String,
    val amountRemaining: Int,
    val reason: DebtReason = DebtReason.GENERIC,
    val propertyId: String? = null,
    val originalAmountDue: Int = amountRemaining,
    val cashAmountUsed: Int = 0,
    val assetSettlementMethod: DebtAssetSettlementMethod? = null,
    val eventDebt: EventMultiRecipientDebtSnapshot? = null,
    val eventContributorDebt: EventContributorDebtSnapshot? = null,
    val eventBankDebit: EventBankDebitDebtSnapshot? = null,
)
