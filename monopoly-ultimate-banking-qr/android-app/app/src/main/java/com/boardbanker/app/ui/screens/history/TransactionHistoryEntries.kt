package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.RentLevelChangeSnapshot
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal sealed interface HistoryDetail {
    data class PlayerTransfer(
        val fromPlayerId: String?,
        val fromPlayerName: String,
        val toPlayerId: String?,
        val toPlayerName: String,
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

    data class Text(val value: String) : HistoryDetail
}

internal data class HistoryEntry(
    val title: String,
    val time: String,
    val detail: HistoryDetail,
    val subtitle: String? = null,
    /** True when a later UNDO transaction rolled this action back. */
    val undone: Boolean = false,
)

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

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Types whose `amount` holds currency. Others store rent levels or effect uses. */
    private val moneyTypes = setOf(
        TransactionType.PROPERTY_PURCHASE,
        TransactionType.RENT_PAYMENT,
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
        TransactionType.JAIL_STATUS_CHANGE,
        TransactionType.RENT_PAYMENT,
        TransactionType.PROPERTY_PURCHASE,
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
        TransactionType.BANKRUPTCY -> "Bankruptcy"
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
        )
    }

    private fun buildEntries(
        group: List<Transaction>,
        session: GameSession,
        definitions: GameDefinitions,
        zone: ZoneId,
        undone: Boolean = false,
    ): List<HistoryEntry> {
        val time = formatTime(group.first().timestamp, zone)
        val rentTx = group.firstOrNull { it.transactionType == TransactionType.RENT_PAYMENT }
        val levelTx = group.firstOrNull { it.transactionType == TransactionType.PROPERTY_RENT_LEVEL_CHANGE }
        val eventTx = group.firstOrNull {
            it.transactionType == TransactionType.EVENT_APPLIED && it.eventId != null
        } ?: group.firstOrNull { it.eventId != null }
        val event = eventTx?.eventId?.let { definitions.events[it] }

        if (rentTx != null) {
            val entries = mutableListOf(
                HistoryEntry(
                    title = label(TransactionType.RENT_PAYMENT),
                    time = time,
                    detail = buildTransferDetail(rentTx, session, definitions),
                    undone = undone,
                ),
            )
            if (levelTx != null) {
                entries += HistoryEntry(
                    title = label(TransactionType.PROPERTY_RENT_LEVEL_CHANGE),
                    time = time,
                    detail = buildRentLevelDetail(levelTx, session, definitions),
                    undone = undone,
                )
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
                ),
            )
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
            ),
        )
    }

    private fun buildTransferDetail(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
    ): HistoryDetail.PlayerTransfer {
        val amount = tx.amount?.let { formatMoney(it, definitions) } ?: ""
        return HistoryDetail.PlayerTransfer(
            fromPlayerId = tx.fromEntity?.takeIf { it != EntityRef.BANK },
            fromPlayerName = tx.fromEntity?.let { entityName(it, session, definitions) } ?: "",
            toPlayerId = tx.toEntity?.takeIf { it != EntityRef.BANK },
            toPlayerName = tx.toEntity?.let { entityName(it, session, definitions) } ?: "",
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
                    "${detail.fromPlayerName} → ${detail.toPlayerName} ${detail.amount}".trim()
                is HistoryDetail.RentLevelChange ->
                    "${detail.playerName}: ${detail.propertyName} ${detail.levelChangeText}"
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
        val amount = tx.amount ?: return null
        return when (tx.transactionType) {
            in moneyTypes -> formatMoney(amount, definitions)
            TransactionType.TEMPORARY_EFFECT_CREATED ->
                "Active for $amount ${plural(amount, "rent payment")}"
            TransactionType.TEMPORARY_EFFECT_CONSUMED ->
                if (amount == 0) "Effect finished" else "$amount ${plural(amount, "use")} left"
            else -> null
        }
    }

    private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"

    private fun headline(group: List<Transaction>): Transaction =
        group.minByOrNull { tx ->
            headlineOrder.indexOf(tx.transactionType).takeIf { it >= 0 } ?: headlineOrder.size
        } ?: group.first()

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
