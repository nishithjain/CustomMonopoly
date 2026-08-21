package com.boardbanker.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrencyDefinition(
    val code: String,
    val symbol: String,
    val scale: Int,
)

@Serializable
data class EventAmounts(
    @SerialName("M50") val m50: Int,
    @SerialName("M200") val m200: Int,
)

@Serializable
data class BankingValues(
    val schemaVersion: Int = 1,
    val currency: CurrencyDefinition,
    val startingBalance: Int,
    val goSalary: Int,
    val locationFee: Int,
    val jailReleaseFee: Int,
    val auctionBidIncrement: Int,
    val eventAmounts: EventAmounts,
)
