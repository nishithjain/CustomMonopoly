package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.player.CommonUiIcon
import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.ui.components.DisplayIdentity
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.DebtPropertySettlementSnapshot
import com.boardbanker.core.model.DebtSettlementSnapshot
import com.boardbanker.core.model.EventContributorDebtSnapshot
import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.EventMultiRecipientDebtSnapshot
import com.boardbanker.core.model.DiceGambleMode
import com.boardbanker.core.model.DirectJailEventSnapshot
import com.boardbanker.core.model.EnergyGridDisplayNames
import com.boardbanker.core.model.LuckyBreakEventSnapshot
import com.boardbanker.core.model.LuckyBreakOutcome
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.JailStatusSnapshot
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.PurchaseAssetType
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal sealed interface HistoryDetail {
    data class PlayerTransfer(
        val from: DisplayIdentity,
        val to: DisplayIdentity,
        val amount: String,
    ) : HistoryDetail

    data class RentLevelChange(
        val playerId: String?,
        val playerName: String,
        val propertyName: String,
        val oldLevel: Int?,
        val newLevel: Int,
    ) : HistoryDetail {
        val levelChangeText: String get() = RentLevelChangeSnapshot.levelChangeText(oldLevel, newLevel)
    }

    data class RentWaived(
        val landingPlayerId: String?,
        val landingPlayerName: String,
        val ownerPlayerId: String?,
        val ownerPlayerName: String,
        val propertyName: String,
        val waivedAmount: String,
        val reason: String,
    ) : HistoryDetail

    data class PlayerMention(
        val playerId: String?,
        val playerName: String,
        val suffix: String? = null,
    ) : HistoryDetail {
        val displayText: String
            get() = buildString {
                append(playerName)
                if (!suffix.isNullOrBlank()) {
                    append(": ")
                    append(suffix)
                }
            }
    }

    data class Text(val value: String) : HistoryDetail

    data class LuckyBreakResolution(
        val playerId: String?,
        val playerName: String,
        val diceDetail: String,
        val transfer: PlayerTransfer,
    ) : HistoryDetail

    data class DebtPropertySettlement(
        val propertyName: String,
        val transfer: PlayerTransfer,
        val valueLabel: String,
    ) : HistoryDetail

    data class RentDebtSettled(
        val transfer: PlayerTransfer,
        val amountDue: String,
        val cashUsed: String,
        val propertyValueUsed: String,
        val remainingDue: String,
    ) : HistoryDetail

    data class EventMultiPlayerTransfer(
        val payerPlayerId: String?,
        val payerName: String,
        val summaryText: String,
        val transfers: List<PlayerTransfer>,
        val totalPaid: String,
        val totalLabel: String,
    ) : HistoryDetail

    data class EventBankruptcy(
        val playerId: String?,
        val playerName: String,
        val summaryText: String,
    ) : HistoryDetail
}

internal data class HistoryEntry(
    val title: String,
    val time: String,
    val detail: HistoryDetail,
    val subtitle: String? = null,
    /** True when a later UNDO transaction rolled this action back. */
    val undone: Boolean = false,
    val entryIcon: CommonUiIcon? = null,
)

internal fun HistoryEntry.withResolvedIcon(
    transactionType: TransactionType? = null,
    eventName: String? = null,
): HistoryEntry {
    if (entryIcon != null) return this
    val resolved = transactionType?.let {
        HistoryEntryIcons.forTransactionType(it, eventName, subtitle)
    } ?: HistoryEntryIcons.forDetail(detail)
    return if (resolved != null) copy(entryIcon = resolved) else this
}

/**
 * Turns committed transactions into display entries for RECENT BANKING.
 *
 * One player action can produce several transactions (an event that pays two
 * players records two bank credits plus EVENT_APPLIED), so transactions sharing
 * a timestamp are grouped into a single entry.
 *
 * An UNDO transaction stores only its type and timestamp, so the action it
 * reverted is recovered from the preceding transactions, which stay in the log.
 */
internal object TransactionHistoryEntries {

    const val MAX_ENTRIES = 30
    const val TURN_ENDED_TITLE = "Turn ended"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Types whose `amount` holds currency. Others store rent levels or effect uses. */
    private val moneyTypes = setOf(
        TransactionType.PROPERTY_PURCHASE,
        TransactionType.ENERGY_GRID_PURCHASE,
        TransactionType.RENT_PAYMENT,
        TransactionType.EVENT_PLAYER_TRANSFER,
        TransactionType.EVENT_MULTI_PLAYER_TRANSFER,
        TransactionType.BANK_CREDIT,
        TransactionType.BANK_DEBIT,
        TransactionType.LOCATION_FEE,
        TransactionType.AUCTION_WIN,
        TransactionType.AUCTION_PURCHASE,
        TransactionType.COLOR_SET_COMPLETION_BONUS,
        TransactionType.PROPERTY_OWNERSHIP_CHANGE,
        TransactionType.BANKRUPTCY,
    )

    /** Which transaction in a group names the whole action. */
    private val headlineOrder = listOf(
        TransactionType.BANKRUPTCY,
        TransactionType.RENT_DEBT_SETTLED,
        TransactionType.JAIL_STATUS_CHANGE,
        TransactionType.JAIL_PASS_USED,
        TransactionType.TURN_ADVANCED,
        TransactionType.TURN_SKIPPED,
        TransactionType.EXTRA_TURN_STARTED,
        TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP,
        TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL,
        TransactionType.EVENT_MULTI_PLAYER_TRANSFER,
        TransactionType.RENT_PAYMENT,
        TransactionType.RENT_WAIVED,
        TransactionType.PROPERTY_PURCHASE,
        TransactionType.ENERGY_GRID_PURCHASE,
        TransactionType.AUCTION_WIN,
        TransactionType.AUCTION_PURCHASE,
        TransactionType.LOCATION_FEE,
        TransactionType.PROPERTY_SWAP,
        TransactionType.PROPERTY_OWNERSHIP_CHANGE,
        TransactionType.COLOR_SET_COMPLETION_BONUS,
        TransactionType.BANK_CREDIT,
        TransactionType.BANK_DEBIT,
        TransactionType.GAME_START,
        TransactionType.PROPERTY_RENT_LEVEL_CHANGE,
        TransactionType.TEMPORARY_EFFECT_CREATED,
        TransactionType.TEMPORARY_EFFECT_CONSUMED,
        TransactionType.EVENT_APPLIED,
    )

    fun build(
        session: GameSession,
        definitions: GameDefinitions,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HistoryEntry> {
        val groups = session.transactions.groupIntoActions()
        val undoTargets = mapUndoTargets(groups)
        val revertedGroups = undoTargets.values.toSet()
        return groups
            .flatMapIndexed { index, group ->
                if (group.isUndoOnly()) {
                    listOf(
                        buildUndoEntry(
                            group,
                            undoTargets[index]?.let { groups[it] },
                            session,
                            definitions,
                            zone,
                        ),
                    )
                } else {
                    buildEntries(
                        group,
                        session,
                        definitions,
                        zone,
                        undone = index in revertedGroups,
                    )
                }
            }
            .takeLast(MAX_ENTRIES)
            .reversed()
    }

    fun label(type: TransactionType): String = when (type) {
        TransactionType.GAME_START -> "Game started"
        TransactionType.PROPERTY_PURCHASE -> "Property purchase"
        TransactionType.RENT_PAYMENT -> "Rent payment"
        TransactionType.RENT_WAIVED -> "Rent waived"
        TransactionType.BANK_CREDIT -> "Bank payout"
        TransactionType.BANK_DEBIT -> "Bank charge"
        TransactionType.PROPERTY_RENT_LEVEL_CHANGE -> "Property rent level change"
        TransactionType.PROPERTY_OWNERSHIP_CHANGE -> "Ownership transfer"
        TransactionType.PROPERTY_SWAP -> "Property swap"
        TransactionType.COLOR_SET_COMPLETION_BONUS -> "Color set bonus"
        TransactionType.LOCATION_FEE -> "Location fee"
        TransactionType.AUCTION_WIN -> "Auction win"
        TransactionType.AUCTION_PURCHASE -> "Auction purchase"
        TransactionType.EVENT_APPLIED -> "Event"
        TransactionType.TEMPORARY_EFFECT_CREATED -> "Effect started"
        TransactionType.TEMPORARY_EFFECT_CONSUMED -> "Effect used"
        TransactionType.JAIL_STATUS_CHANGE -> "Jail"
        TransactionType.JAIL_PASS_USED -> "Get Out of Jail pass"
        TransactionType.TURN_SKIPPED -> "Turn skipped"
        TransactionType.TURN_ADVANCED -> "Next turn"
        TransactionType.EXTRA_TURN_STARTED -> "Extra turn"
        TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP -> "Extra turn cancelled"
        TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL -> "Extra turn cancelled"
        TransactionType.EXTRA_TURN_GRANTED -> "Extra turn granted"
        TransactionType.BANKRUPTCY -> "Bankruptcy"
        TransactionType.RENT_DEBT_SETTLED -> "Rent debt settled"
        TransactionType.EVENT_PLAYER_TRANSFER -> "Event transfer"
        TransactionType.EVENT_MULTI_PLAYER_TRANSFER -> "Event transfer"
        TransactionType.ENERGY_GRID_PURCHASE -> "Energy grid purchase"
        TransactionType.ENERGY_GRID_OWNERSHIP_CHANGE -> "Energy grid ownership transfer"
        TransactionType.UNDO -> "Undo"
    }

    /** Maps the index of each UNDO group to the action group it reverted. */
    private fun mapUndoTargets(groups: List<List<Transaction>>): Map<Int, Int> {
        val targets = mutableMapOf<Int, Int>()
        val claimed = mutableSetOf<Int>()
        groups.forEachIndexed { index, group ->
            if (!group.isUndoOnly()) return@forEachIndexed
            val target = (index - 1 downTo 0).firstOrNull { candidate ->
                candidate !in claimed && !groups[candidate].isUndoOnly()
            } ?: return@forEachIndexed
            targets[index] = target
            claimed += target
        }
        return targets
    }

    private fun buildUndoEntry(
        group: List<Transaction>,
        revertedGroup: List<Transaction>?,
        session: GameSession,
        definitions: GameDefinitions,
        zone: ZoneId,
    ): HistoryEntry {
        val revertedPrimary = revertedGroup?.let { buildEntries(it, session, definitions, zone).firstOrNull() }
        val revertedDetail = revertedGroup?.let {
            buildSingleDetail(headline(it), session, definitions)
        } ?: HistoryDetail.Text("Reverted the previous action.")
        return HistoryEntry(
            title = label(TransactionType.UNDO),
            time = formatTime(group.first().timestamp, zone),
            subtitle = revertedPrimary?.let { "Reverted: ${it.title}" }
                ?: "Reverted the previous action.",
            detail = revertedDetail,
        ).withResolvedIcon(TransactionType.UNDO)
    }

    private fun buildEntries(
        group: List<Transaction>,
        session: GameSession,
        definitions: GameDefinitions,
        zone: ZoneId,
        undone: Boolean = false,
    ): List<HistoryEntry> {
        val time = formatTime(group.first().timestamp, zone)
        val luckyBreakTx = group.firstOrNull { LuckyBreakEventSnapshot.isLuckyBreakResolution(it) }
        if (luckyBreakTx != null) {
            val metadata = LuckyBreakEventSnapshot.fromTransaction(luckyBreakTx)!!
            return listOf(buildLuckyBreakEntry(metadata, luckyBreakTx, session, definitions, time, undone))
        }
        val bankruptcyTx = group.firstOrNull { it.transactionType == TransactionType.BANKRUPTCY }
        val eventBankruptcyName = bankruptcyTx?.let { EventMultiRecipientDebtSnapshot.fromBankruptcyTransaction(it)?.eventName }
            ?: bankruptcyTx?.let { EventContributorDebtSnapshot.fromBankruptcyTransaction(it)?.eventName }
        val eventMultiTransferTx = group.firstOrNull {
            it.transactionType == TransactionType.EVENT_MULTI_PLAYER_TRANSFER
        }
        if (bankruptcyTx != null && eventBankruptcyName != null) {
            val playerId = bankruptcyTx.playerId
            val playerName = playerDisplayName(playerId, session, definitions)
            val bankruptcyEntry = HistoryEntry(
                title = "$eventBankruptcyName — Unable to Pay",
                time = time,
                detail = HistoryDetail.EventBankruptcy(
                    playerId = playerId,
                    playerName = playerName,
                    summaryText = "$playerName declared bankrupt",
                ),
                undone = undone,
                entryIcon = CommonUiIcon.EVENT_CARD,
            )
            val settlementEntry = eventMultiTransferTx?.let { summaryTx ->
                EventMultiPlayerTransferSnapshot.fromTransaction(summaryTx)?.let { metadata ->
                    buildEventMultiPlayerTransferEntry(
                        metadata,
                        session,
                        definitions,
                        time,
                        undone,
                    )
                }
            }
            return listOfNotNull(bankruptcyEntry, settlementEntry)
        }
        if (eventMultiTransferTx != null) {
            val metadata = EventMultiPlayerTransferSnapshot.fromTransaction(eventMultiTransferTx)
            if (metadata != null) {
                val propertyEntries = group
                    .filter { DebtPropertySettlementSnapshot.isDebtSettlement(it) }
                    .map { buildDebtPropertySettlementEntry(it, session, definitions, time, undone) }
                return propertyEntries + buildEventMultiPlayerTransferEntry(
                    metadata,
                    session,
                    definitions,
                    time,
                    undone,
                )
            }
        }
        val legacyEventTransfers = group.filter {
            it.transactionType == TransactionType.RENT_PAYMENT &&
                it.propertyId == null &&
                it.eventId != null &&
                it.fromEntity != null &&
                it.toEntity != null &&
                it.fromEntity != EntityRef.BANK &&
                it.toEntity != EntityRef.BANK
        }
        if (legacyEventTransfers.size > 1) {
            val event = legacyEventTransfers.first().eventId?.let { definitions.events[it] }
            if (event != null) {
                return listOf(
                    buildLegacyEventMultiPlayerTransferEntry(
                        eventName = event.name,
                        eventId = event.eventId,
                        transfers = legacyEventTransfers,
                        session = session,
                        definitions = definitions,
                        time = time,
                        undone = undone,
                    ),
                )
            }
        }
        val settlementTx = group.firstOrNull { it.transactionType == TransactionType.RENT_DEBT_SETTLED }
        if (settlementTx != null) {
            return buildDebtSettlementEntries(
                group = group,
                settlementTx = settlementTx,
                session = session,
                definitions = definitions,
                time = time,
                undone = undone,
            )
        }
        val debtPropertyTxs = group.filter { DebtPropertySettlementSnapshot.isDebtSettlement(it) }
        if (debtPropertyTxs.isNotEmpty()) {
            return debtPropertyTxs.map { tx ->
                buildDebtPropertySettlementEntry(tx, session, definitions, time, undone)
            }
        }
        val rentTx = group.firstOrNull { it.transactionType == TransactionType.RENT_PAYMENT }
        val waivedTx = group.firstOrNull { it.transactionType == TransactionType.RENT_WAIVED }
        val levelTx = group.firstOrNull { it.transactionType == TransactionType.PROPERTY_RENT_LEVEL_CHANGE }
        val eventTx = group.firstOrNull {
            it.transactionType == TransactionType.EVENT_APPLIED && it.eventId != null
        } ?: group.firstOrNull { it.eventId != null }
        val event = eventTx?.eventId?.let { definitions.events[it] }

        if (waivedTx != null) {
            return listOf(
                HistoryEntry(
                    title = label(TransactionType.RENT_WAIVED),
                    time = time,
                    detail = buildRentWaivedDetail(waivedTx, session, definitions),
                    undone = undone,
                ).withResolvedIcon(TransactionType.RENT_WAIVED),
            )
        }

        if (rentTx != null) {
            val entries = mutableListOf(
                HistoryEntry(
                    title = label(TransactionType.RENT_PAYMENT),
                    time = time,
                    detail = buildTransferDetail(rentTx, session, definitions),
                    undone = undone,
                ).withResolvedIcon(TransactionType.RENT_PAYMENT),
            )
            if (levelTx != null) {
                entries += HistoryEntry(
                    title = label(TransactionType.PROPERTY_RENT_LEVEL_CHANGE),
                    time = time,
                    detail = buildRentLevelDetail(levelTx, session, definitions),
                    undone = undone,
                ).withResolvedIcon(TransactionType.PROPERTY_RENT_LEVEL_CHANGE)
            }
            return entries
        }

        if (levelTx != null && group.all {
                it.transactionType == TransactionType.PROPERTY_RENT_LEVEL_CHANGE ||
                    it.transactionType == TransactionType.TEMPORARY_EFFECT_CONSUMED
            }
        ) {
            return listOf(
                HistoryEntry(
                    title = label(TransactionType.PROPERTY_RENT_LEVEL_CHANGE),
                    time = time,
                    detail = buildRentLevelDetail(levelTx, session, definitions),
                    undone = undone,
                ).withResolvedIcon(TransactionType.PROPERTY_RENT_LEVEL_CHANGE),
            )
        }

        val purchaseHeadline = headline(group)
        if (purchaseHeadline.transactionType == TransactionType.PROPERTY_PURCHASE ||
            purchaseHeadline.transactionType == TransactionType.ENERGY_GRID_PURCHASE
        ) {
            return listOf(
                buildPurchaseEntry(purchaseHeadline, session, definitions, time, undone),
            )
        }

        val skipTxs = group.filter { it.transactionType == TransactionType.TURN_SKIPPED }
        if (skipTxs.isNotEmpty()) {
            val entries = skipTxs.map { skipTx ->
                HistoryEntry(
                    title = label(TransactionType.TURN_SKIPPED),
                    time = time,
                    detail = buildSingleDetail(skipTx, session, definitions),
                    undone = undone,
                ).withResolvedIcon(TransactionType.TURN_SKIPPED)
            }.toMutableList()
            group.firstOrNull { it.transactionType == TransactionType.TURN_ADVANCED }?.let { advanceTx ->
                entries += buildTurnEndedEntry(advanceTx, session, definitions, time, undone)
                entries += buildNextTurnEntry(advanceTx, session, definitions, time, undone)
            }
            return entries
        }

        val jailStatusTx = group.firstOrNull { it.transactionType == TransactionType.JAIL_STATUS_CHANGE }
        val jailFeeTx = group.firstOrNull {
            it.transactionType == TransactionType.BANK_DEBIT && it.toEntity == EntityRef.BANK
        }
        if (jailStatusTx != null && jailFeeTx != null) {
            val playerId = jailStatusTx.playerId ?: jailFeeTx.playerId
            return listOf(
                HistoryEntry(
                    title = label(TransactionType.JAIL_STATUS_CHANGE),
                    time = time,
                    subtitle = "Get out of Jail fee",
                    detail = HistoryDetail.PlayerTransfer(
                        from = playerIdentity(playerId, session, definitions),
                        to = DisplayIdentity.Bank,
                        amount = jailFeeTx.amount?.let { formatMoney(it, definitions) } ?: "",
                    ),
                    undone = undone,
                ).withResolvedIcon(TransactionType.JAIL_STATUS_CHANGE),
            )
        }
        val jailPassTx = group.firstOrNull { it.transactionType == TransactionType.JAIL_PASS_USED }
        if (jailPassTx != null && jailStatusTx != null) {
            val playerId = jailPassTx.playerId ?: jailStatusTx.playerId
            val subtitle = if (jailPassTx.eventId != null) {
                "Get out of Jail Pass used • No fee charged"
            } else {
                "Get Out of Jail pass used"
            }
            return listOf(
                HistoryEntry(
                    title = "${playerDisplayName(playerId, session, definitions)} got out of Jail",
                    time = time,
                    subtitle = subtitle,
                    detail = HistoryDetail.PlayerTransfer(
                        from = DisplayIdentity.Jail,
                        to = playerIdentity(playerId, session, definitions),
                        amount = "",
                    ),
                    undone = undone,
                ).withResolvedIcon(TransactionType.JAIL_PASS_USED),
            )
        }
        if (jailStatusTx != null && JailStatusSnapshot.enteredJail(jailStatusTx)) {
            val directJailMetadata = eventTx?.let { DirectJailEventSnapshot.fromEventApplied(it) }
            val directJailWithTurnEnd = directJailMetadata?.turnEnded == true ||
                (directJailMetadata == null && event?.actions?.any { it.endsCurrentTurnAfterJail() } == true)
            if (directJailWithTurnEnd) {
                val affectedPlayerId = directJailMetadata?.affectedPlayerId ?: jailStatusTx.playerId
                val entries = mutableListOf(
                    HistoryEntry(
                        title = event?.name ?: label(TransactionType.JAIL_STATUS_CHANGE),
                        time = time,
                        subtitle = DirectJailEventSnapshot.SUBTITLE,
                        detail = HistoryDetail.PlayerTransfer(
                            from = playerIdentity(affectedPlayerId, session, definitions),
                            to = DisplayIdentity.Jail,
                            amount = "",
                        ),
                        undone = undone,
                    ).withResolvedIcon(TransactionType.EVENT_APPLIED, event?.name),
                )
                group.lastOrNull {
                    it.transactionType == TransactionType.TURN_ADVANCED &&
                        it.fromEntity == affectedPlayerId
                }?.let { advanceTx ->
                    entries += buildTurnEndedEntry(advanceTx, session, definitions, time, undone)
                    entries += buildNextTurnEntry(advanceTx, session, definitions, time, undone)
                }
                return entries
            }
            return listOf(
                HistoryEntry(
                    title = label(TransactionType.JAIL_STATUS_CHANGE),
                    time = time,
                    subtitle = "Sent to Jail",
                    detail = HistoryDetail.PlayerTransfer(
                        from = playerIdentity(jailStatusTx.playerId, session, definitions),
                        to = DisplayIdentity.Jail,
                        amount = "",
                    ),
                    undone = undone,
                ).withResolvedIcon(TransactionType.JAIL_STATUS_CHANGE),
            )
        }
        if (jailStatusTx != null && JailStatusSnapshot.releasedFromJail(jailStatusTx)) {
            return listOf(
                HistoryEntry(
                    title = label(TransactionType.JAIL_STATUS_CHANGE),
                    time = time,
                    subtitle = "Released from Jail",
                    detail = HistoryDetail.PlayerTransfer(
                        from = DisplayIdentity.Jail,
                        to = playerIdentity(jailStatusTx.playerId, session, definitions),
                        amount = "",
                    ),
                    undone = undone,
                ).withResolvedIcon(TransactionType.JAIL_STATUS_CHANGE),
            )
        }

        val advanceTx = group.firstOrNull { it.transactionType == TransactionType.TURN_ADVANCED }
        if (advanceTx != null && skipTxs.isEmpty() && group.isTurnTransitionOnly()) {
            val entries = group
                .filter {
                    it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP ||
                        it.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL
                }
                .map { cancelTx ->
                    HistoryEntry(
                        title = label(cancelTx.transactionType),
                        time = time,
                        detail = buildSingleDetail(cancelTx, session, definitions),
                        undone = undone,
                    ).withResolvedIcon(cancelTx.transactionType)
                }
                .toMutableList()
            entries += buildTurnEndedEntry(advanceTx, session, definitions, time, undone)
            entries += buildNextTurnEntry(advanceTx, session, definitions, time, undone)
            return entries
        }

        val title = if (event != null) {
            "Event: ${event.name}"
        } else {
            label(headline(group).transactionType)
        }
        val subtitle = event?.let {
            it.eventSubtitle.takeIf { text -> text.isNotBlank() }
                ?: it.eventDescription.takeIf { text -> text.isNotBlank() }
        }

        val detailTransactions = group.filterNot {
            it.transactionType == TransactionType.EVENT_APPLIED && group.size > 1
        }
        val headlineTx = headline(group)
        val detail = when (headlineTx.transactionType) {
            TransactionType.RENT_PAYMENT,
            TransactionType.PROPERTY_PURCHASE,
            TransactionType.ENERGY_GRID_PURCHASE,
            TransactionType.AUCTION_WIN,
            TransactionType.AUCTION_PURCHASE,
            -> buildTransferDetail(headlineTx, session, definitions)
            TransactionType.PROPERTY_RENT_LEVEL_CHANGE ->
                buildRentLevelDetail(headlineTx, session, definitions)
            else -> buildCombinedDetail(detailTransactions, session, definitions)
        }

        return listOf(
            HistoryEntry(
                title = title,
                time = time,
                subtitle = subtitle,
                detail = detail,
                undone = undone,
            ).withResolvedIcon(headlineTx.transactionType, event?.name),
        )
    }

    private fun buildDebtSettlementEntries(
        group: List<Transaction>,
        settlementTx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): List<HistoryEntry> {
        val metadata = DebtSettlementSnapshot.fromTransaction(settlementTx) ?: return emptyList()
        val propertyEntries = group
            .filter { DebtPropertySettlementSnapshot.isDebtSettlement(it) }
            .map { buildDebtPropertySettlementEntry(it, session, definitions, time, undone) }
        val settlementEntry = HistoryEntry(
            title = "Rent Debt Settled",
            time = time,
            detail = HistoryDetail.RentDebtSettled(
                transfer = HistoryDetail.PlayerTransfer(
                    from = playerIdentity(metadata.debtorPlayerId, session, definitions),
                    to = entityToIdentity(metadata.creditorPlayerId, session, definitions),
                    amount = "",
                ),
                amountDue = formatMoney(metadata.originalAmountDue, definitions),
                cashUsed = formatMoney(metadata.cashAmountUsed, definitions),
                propertyValueUsed = formatMoney(metadata.propertyValueUsed, definitions),
                remainingDue = formatMoney(metadata.remainingDue, definitions),
            ),
            undone = undone,
            entryIcon = CommonUiIcon.RENT,
        )
        return propertyEntries + settlementEntry
    }

    private fun buildDebtPropertySettlementEntry(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val metadata = DebtPropertySettlementSnapshot.fromTransaction(tx)!!
        val title = if (metadata.soldToBank) {
            "Property Sold → ${metadata.propertyName}"
        } else {
            "Property Transferred → ${metadata.propertyName}"
        }
        val valueLabel = if (metadata.soldToBank) {
            "Sale value: ${formatMoney(metadata.settlementValue, definitions)}"
        } else {
            "Settlement value: ${formatMoney(metadata.settlementValue, definitions)}"
        }
        return HistoryEntry(
            title = title,
            time = time,
            detail = HistoryDetail.DebtPropertySettlement(
                propertyName = metadata.propertyName,
                transfer = HistoryDetail.PlayerTransfer(
                    from = entityToIdentity(tx.fromEntity, session, definitions),
                    to = entityToIdentity(metadata.destination, session, definitions),
                    amount = "",
                ),
                valueLabel = valueLabel,
            ),
            undone = undone,
            entryIcon = CommonUiIcon.PROPERTY,
        )
    }

    private fun buildEventMultiPlayerTransferEntry(
        metadata: EventMultiPlayerTransferSnapshot.Metadata,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val payerName = playerDisplayName(metadata.payerPlayerId, session, definitions)
        val formattedTotal = formatMoney(metadata.totalAmount, definitions)
        val transfers = metadata.transfers.map { transfer ->
            HistoryDetail.PlayerTransfer(
                from = playerIdentity(transfer.fromPlayerId, session, definitions),
                to = playerIdentity(transfer.toPlayerId, session, definitions),
                amount = formatMoney(transfer.amount, definitions),
            )
        }
        return HistoryEntry(
            title = metadata.eventName,
            time = time,
            detail = HistoryDetail.EventMultiPlayerTransfer(
                payerPlayerId = metadata.payerPlayerId,
                payerName = payerName,
                summaryText = EventMultiPlayerTransferSnapshot.summaryText(metadata, payerName),
                transfers = transfers,
                totalPaid = formattedTotal,
                totalLabel = EventMultiPlayerTransferSnapshot.totalLabel(metadata, payerName, formattedTotal),
            ),
            undone = undone,
            entryIcon = CommonUiIcon.EVENT_CARD,
        )
    }

    private fun buildLegacyEventMultiPlayerTransferEntry(
        eventName: String,
        eventId: String,
        transfers: List<Transaction>,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val payerId = transfers.first().playerId ?: transfers.first().fromEntity
        val payerName = playerDisplayName(payerId, session, definitions)
        val transferDetails = transfers.map { tx ->
            HistoryDetail.PlayerTransfer(
                from = entityToIdentity(tx.fromEntity, session, definitions),
                to = entityToIdentity(tx.toEntity, session, definitions),
                amount = tx.amount?.let { formatMoney(it, definitions) } ?: "",
            )
        }
        val total = transfers.sumOf { it.amount ?: 0 }
        val direction = if (eventId == "EVT_07") {
            EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER
        } else {
            EventMultiPlayerTransferSnapshot.Direction.PAY_EACH_PLAYER
        }
        val formattedTotal = formatMoney(total, definitions)
        val summary = when (direction) {
            EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER ->
                "$payerName received birthday contributions"
            EventMultiPlayerTransferSnapshot.Direction.PAY_EACH_PLAYER ->
                "$payerName contributed to all other players"
        }
        val totalLabel = when (direction) {
            EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER ->
                "Total received by $payerName: $formattedTotal"
            EventMultiPlayerTransferSnapshot.Direction.PAY_EACH_PLAYER ->
                "Total paid: $formattedTotal"
        }
        return HistoryEntry(
            title = eventName,
            time = time,
            detail = HistoryDetail.EventMultiPlayerTransfer(
                payerPlayerId = payerId,
                payerName = payerName,
                summaryText = summary,
                transfers = transferDetails,
                totalPaid = formattedTotal,
                totalLabel = totalLabel,
            ),
            undone = undone,
            entryIcon = CommonUiIcon.EVENT_CARD,
        )
    }

    private fun buildLuckyBreakEntry(
        metadata: LuckyBreakEventSnapshot.Metadata,
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val amount = formatMoney(metadata.appliedAmount, definitions)
        val transfer = when (metadata.outcome) {
            LuckyBreakOutcome.JACKPOT -> HistoryDetail.PlayerTransfer(
                from = DisplayIdentity.Bank,
                to = playerIdentity(metadata.playerId, session, definitions),
                amount = amount,
            )
            LuckyBreakOutcome.PENALTY -> HistoryDetail.PlayerTransfer(
                from = playerIdentity(metadata.playerId, session, definitions),
                to = DisplayIdentity.Bank,
                amount = amount,
            )
        }
        val diceDetail = luckyBreakDiceDetail(metadata)
        val title = when (metadata.outcome) {
            LuckyBreakOutcome.JACKPOT -> "Lucky Break — Jackpot"
            LuckyBreakOutcome.PENALTY -> "Lucky Break — Penalty"
        }
        val entryIcon = when (metadata.outcome) {
            LuckyBreakOutcome.JACKPOT -> CommonUiIcon.JACKPOT
            LuckyBreakOutcome.PENALTY -> CommonUiIcon.PENALTY
        }
        return HistoryEntry(
            title = title,
            time = time,
            subtitle = diceDetail,
            detail = HistoryDetail.LuckyBreakResolution(
                playerId = metadata.playerId,
                playerName = playerDisplayName(metadata.playerId, session, definitions),
                diceDetail = diceDetail,
                transfer = transfer,
            ),
            undone = undone,
            entryIcon = entryIcon,
        )
    }

    private fun luckyBreakDiceDetail(metadata: LuckyBreakEventSnapshot.Metadata): String =
        when (metadata.mode) {
            DiceGambleMode.IN_APP -> {
                val dieOne = metadata.dieOne
                val dieTwo = metadata.dieTwo
                if (dieOne != null && dieTwo != null) {
                    val rollLabel = if (metadata.outcome == LuckyBreakOutcome.PENALTY &&
                        metadata.attemptNumber >= 3
                    ) {
                        "Final roll: $dieOne + $dieTwo"
                    } else {
                        "In-app dice: $dieOne + $dieTwo"
                    }
                    "$rollLabel • Attempt ${metadata.attemptNumber} of 3"
                } else {
                    "In-app dice • Attempt ${metadata.attemptNumber} of 3"
                }
            }
            DiceGambleMode.PHYSICAL -> when (metadata.outcome) {
                LuckyBreakOutcome.JACKPOT -> "Physical dice • Doubles confirmed"
                LuckyBreakOutcome.PENALTY -> "Physical dice • No doubles after 3 attempts"
            }
        }

    private fun buildPurchaseEntry(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val assetType = resolvePurchaseAssetType(tx)
        val assetName = resolvePurchaseAssetName(tx, assetType, definitions)
        val title = purchaseTitle(tx, assetType, assetName, tx.transactionType)
        val entryIcon = when (assetType) {
            PurchaseAssetType.PROPERTY -> CommonUiIcon.PROPERTY
            PurchaseAssetType.ENERGY_GRID -> CommonUiIcon.ENERGY_GRID
        }
        return HistoryEntry(
            title = title,
            time = time,
            detail = buildPurchaseTransferDetail(tx, session, definitions),
            undone = undone,
            entryIcon = entryIcon,
        )
    }

    private fun buildPurchaseTransferDetail(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail.PlayerTransfer {
        val purchaserId = tx.playerId ?: tx.fromEntity
        val amount = tx.amount?.let { formatMoney(it, definitions) } ?: ""
        return HistoryDetail.PlayerTransfer(
            from = playerIdentity(purchaserId, session, definitions),
            to = DisplayIdentity.Bank,
            amount = amount,
        )
    }

    private fun resolvePurchaseAssetType(tx: Transaction): PurchaseAssetType =
        tx.assetType ?: when (tx.transactionType) {
            TransactionType.ENERGY_GRID_PURCHASE -> PurchaseAssetType.ENERGY_GRID
            else -> PurchaseAssetType.PROPERTY
        }

    private fun resolvePurchaseAssetName(
        tx: Transaction,
        assetType: PurchaseAssetType,
        definitions: GameDefinitions,
    ): String {
        tx.assetName?.takeIf { it.isNotBlank() }?.let { return it }
        val assetId = tx.propertyId ?: return ""
        return when (assetType) {
            PurchaseAssetType.PROPERTY -> PropertyDisplayNames.displayNameWithNumber(assetId, definitions)
            PurchaseAssetType.ENERGY_GRID -> EnergyGridDisplayNames.displayNameWithNumber(assetId, definitions)
        }
    }

    private fun purchaseTitle(
        tx: Transaction,
        assetType: PurchaseAssetType,
        assetName: String,
        transactionType: TransactionType,
    ): String {
        val prefix = when (assetType) {
            PurchaseAssetType.PROPERTY -> "Property Purchase"
            PurchaseAssetType.ENERGY_GRID -> "Energy Grid Purchase"
        }
        val hasStoredName = tx.assetName?.isNotBlank() == true
        val hasResolvedName = assetName.isNotBlank() && assetName != tx.propertyId
        return if (hasStoredName || hasResolvedName) {
            "$prefix → ${tx.assetName?.takeIf { hasStoredName } ?: assetName}"
        } else {
            label(transactionType)
        }
    }

    private fun buildRentWaivedDetail(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail.RentWaived {
        val propertyName = tx.propertyId
            ?.let { PropertyDisplayNames.displayNameWithNumber(it, definitions) }
            ?: "Property"
        val landingPlayerId = tx.playerId ?: tx.fromEntity
        val ownerPlayerId = tx.toEntity
        val reason = tx.eventId?.let { definitions.events[it]?.name } ?: "Rent Relief"
        return HistoryDetail.RentWaived(
            landingPlayerId = landingPlayerId,
            landingPlayerName = landingPlayerId?.let {
                PlayerDisplayNames.displayName(session, it, definitions)
            } ?: "",
            ownerPlayerId = ownerPlayerId,
            ownerPlayerName = ownerPlayerId?.let {
                PlayerDisplayNames.displayName(session, it, definitions)
            } ?: "",
            propertyName = propertyName,
            waivedAmount = tx.amount?.let { formatMoney(it, definitions) } ?: "",
            reason = reason,
        )
    }

    private fun buildTransferDetail(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail.PlayerTransfer {
        val amount = tx.amount?.let { formatMoney(it, definitions) } ?: ""
        return HistoryDetail.PlayerTransfer(
            from = entityToIdentity(tx.fromEntity, session, definitions),
            to = entityToIdentity(tx.toEntity, session, definitions),
            amount = amount,
        )
    }

    private fun buildRentLevelDetail(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail.RentLevelChange {
        val propertyName = tx.propertyId
            ?.let { PropertyDisplayNames.displayNameWithNumber(it, definitions) }
            ?: "Property"
        val newLevel = RentLevelChangeSnapshot.newLevel(tx) ?: 1
        return HistoryDetail.RentLevelChange(
            playerId = tx.playerId,
            playerName = tx.playerId?.let { PlayerDisplayNames.displayName(session, it, definitions) } ?: "",
            propertyName = propertyName,
            oldLevel = RentLevelChangeSnapshot.oldLevel(tx),
            newLevel = newLevel,
        )
    }

    private fun buildCombinedDetail(
        transactions: List<Transaction>,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail {
        if (transactions.size == 1) {
            return buildSingleDetail(transactions.single(), session, definitions)
        }
        val parts = transactions.mapNotNull { tx ->
            when (val detail = buildSingleDetail(tx, session, definitions)) {
                is HistoryDetail.PlayerTransfer ->
                    "${detail.from.label} → ${detail.to.label} ${detail.amount}".trim()
                is HistoryDetail.RentLevelChange ->
                    "${detail.playerName}: ${detail.propertyName} ${detail.levelChangeText}"
                is HistoryDetail.RentWaived ->
                    "${detail.landingPlayerName} • ${detail.propertyName} • ${detail.reason}"
                is HistoryDetail.PlayerMention -> detail.displayText.takeIf { it.isNotBlank() }
                is HistoryDetail.LuckyBreakResolution ->
                    "${detail.diceDetail} ${detail.transfer.from.label} → ${detail.transfer.to.label} ${detail.transfer.amount}".trim()
                is HistoryDetail.DebtPropertySettlement ->
                    "${detail.transfer.from.label} → ${detail.transfer.to.label} • ${detail.valueLabel}"
                is HistoryDetail.RentDebtSettled ->
                    "${detail.transfer.from.label} → ${detail.transfer.to.label} • Amount due: ${detail.amountDue}"
                is HistoryDetail.EventMultiPlayerTransfer ->
                    "${detail.summaryText} • ${detail.totalLabel}"
                is HistoryDetail.EventBankruptcy -> detail.summaryText.takeIf { it.isNotBlank() }
                is HistoryDetail.Text -> detail.value.takeIf { it.isNotBlank() }
            }
        }
        return HistoryDetail.Text(parts.joinToString(" • "))
    }

    private fun buildSingleDetail(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail {
        if (tx.transactionType == TransactionType.RENT_WAIVED) {
            return buildRentWaivedDetail(tx, session, definitions)
        }
        if (tx.fromEntity != null && tx.toEntity != null &&
            tx.transactionType in moneyTypes &&
            tx.amount != null
        ) {
            return buildTransferDetail(tx, session, definitions)
        }
        if (tx.transactionType == TransactionType.PROPERTY_RENT_LEVEL_CHANGE) {
            return buildRentLevelDetail(tx, session, definitions)
        }
        val propertyName = tx.propertyId
            ?.let { PropertyDisplayNames.displayNameWithNumber(it, definitions) }
        val playerName = tx.playerId?.let { PlayerDisplayNames.displayName(session, it, definitions) }
        val detailText = detailText(tx, definitions)
        if (propertyName == null && playerName != null) {
            return HistoryDetail.PlayerMention(
                playerId = tx.playerId,
                playerName = playerName,
                suffix = detailText,
            )
        }
        val value = buildString {
            if (playerName != null) {
                append(playerName)
                if (propertyName != null || detailText != null) append(": ")
            }
            if (propertyName != null) {
                append(propertyName)
                if (detailText != null) append(' ')
            }
            if (detailText != null) append(detailText)
        }.trim()
        return HistoryDetail.Text(value.ifBlank { label(tx.transactionType) })
    }

    private fun formatTime(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    private fun detailText(tx: Transaction, definitions: GameDefinitions): String? {
        return when (tx.transactionType) {
            in moneyTypes -> tx.amount?.let { formatMoney(it, definitions) }
            TransactionType.JAIL_PASS_USED -> "used Get Out of Jail pass"
            TransactionType.TURN_SKIPPED -> "skips this turn"
            TransactionType.EXTRA_TURN_STARTED -> "takes an extra turn"
            TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP -> "extra turn cancelled by skip"
            TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL -> "extra turn cancelled by Jail"
            TransactionType.TEMPORARY_EFFECT_CREATED ->
                tx.amount?.let { "Active for $it ${plural(it, "rent payment")}" }
            TransactionType.TEMPORARY_EFFECT_CONSUMED ->
                tx.amount?.let { if (it == 0) "Effect finished" else "$it ${plural(it, "use")} left" }
            else -> null
        }
    }

    private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"

    private fun headline(group: List<Transaction>): Transaction =
        group.minByOrNull { tx ->
            headlineOrder.indexOf(tx.transactionType).takeIf { it >= 0 } ?: headlineOrder.size
        } ?: group.first()

    private fun entityToIdentity(
        entity: String?,
        session: GameSession,
        definitions: GameDefinitions,
    ): DisplayIdentity = when (entity) {
        EntityRef.BANK -> DisplayIdentity.Bank
        null -> DisplayIdentity.Player(playerId = null, playerName = "")
        else -> playerIdentity(entity, session, definitions)
    }

    private fun playerIdentity(
        playerId: String?,
        session: GameSession,
        definitions: GameDefinitions,
    ): DisplayIdentity.Player =
        DisplayIdentity.Player(
            playerId = playerId,
            playerName = playerDisplayName(playerId, session, definitions),
        )

    private fun playerDisplayName(
        playerId: String?,
        session: GameSession,
        definitions: GameDefinitions,
    ): String = playerId?.let { PlayerDisplayNames.displayName(session, it, definitions) } ?: "Player"

    private fun entityName(
        entity: String,
        session: GameSession,
        definitions: GameDefinitions,
    ): String = if (entity == EntityRef.BANK) {
        "Bank"
    } else {
        PlayerDisplayNames.displayName(session, entity, definitions)
    }

    private fun List<Transaction>.isUndoOnly(): Boolean =
        isNotEmpty() && all { it.transactionType == TransactionType.UNDO }

    private fun List<Transaction>.isTurnTransitionOnly(): Boolean = all { tx ->
        tx.transactionType == TransactionType.TURN_ADVANCED ||
            tx.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_SKIP ||
            tx.transactionType == TransactionType.EXTRA_TURN_CANCELLED_BY_JAIL
    }

    private fun buildTurnEndedEntry(
        advanceTx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val endingPlayerId = advanceTx.fromEntity
        val endingPlayerName = playerDisplayName(endingPlayerId, session, definitions)
        return HistoryEntry(
            title = TURN_ENDED_TITLE,
            time = time,
            detail = HistoryDetail.PlayerMention(
                playerId = endingPlayerId,
                playerName = endingPlayerName,
            ),
            undone = undone,
        )
    }

    private fun buildNextTurnEntry(
        advanceTx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        time: String,
        undone: Boolean,
    ): HistoryEntry {
        val nextPlayerId = advanceTx.toEntity ?: advanceTx.playerId
        val nextPlayerName = playerDisplayName(nextPlayerId, session, definitions)
        return HistoryEntry(
            title = label(TransactionType.TURN_ADVANCED),
            time = time,
            detail = HistoryDetail.PlayerMention(
                playerId = nextPlayerId,
                playerName = nextPlayerName,
            ),
            undone = undone,
        ).withResolvedIcon(TransactionType.TURN_ADVANCED)
    }

    /**
     * Splits the log into one group per player action. Transactions written by a
     * single command share a timestamp. UNDO always starts and ends a group so a
     * fast undo is never merged into the action it reverted.
     */
    private fun List<Transaction>.groupIntoActions(): List<List<Transaction>> {
        val groups = mutableListOf<MutableList<Transaction>>()
        for (tx in this) {
            val current = groups.lastOrNull()
            val belongsToCurrent = current != null &&
                current.first().timestamp == tx.timestamp &&
                tx.transactionType != TransactionType.UNDO &&
                current.last().transactionType != TransactionType.UNDO
            if (belongsToCurrent) {
                current!! += tx
            } else {
                groups += mutableListOf(tx)
            }
        }
        return groups
    }
}
