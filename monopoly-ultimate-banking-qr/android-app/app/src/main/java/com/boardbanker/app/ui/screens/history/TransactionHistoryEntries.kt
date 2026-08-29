package com.boardbanker.app.ui.screens.history

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.app.util.formatMoney
import com.boardbanker.core.model.EntityRef
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class HistoryLine(
    val label: String,
    val fromPlayerId: String? = null,
    val fromPlayerName: String? = null,
    val toPlayerId: String? = null,
    val toPlayerName: String? = null,
    val playerId: String? = null,
    val playerName: String? = null,
    val propertyName: String? = null,
    val detail: String? = null,
)

internal data class HistoryEntry(
    val title: String,
    val time: String,
    val subtitle: String? = null,
    val propertyName: String? = null,
    val lines: List<HistoryLine> = emptyList(),
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
            .mapIndexed { index, group ->
                if (group.isUndoOnly()) {
                    buildUndoEntry(group, undoTargets[index]?.let { groups[it] }, session, definitions, zone)
                } else {
                    buildEntry(group, session, definitions, zone, undone = index in revertedGroups)
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
        TransactionType.PROPERTY_RENT_LEVEL_CHANGE -> "Rent level change"
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
        val reverted = revertedGroup?.let { buildEntry(it, session, definitions, zone) }
        return HistoryEntry(
            title = label(TransactionType.UNDO),
            time = formatTime(group.first().timestamp, zone),
            subtitle = reverted?.let { "Reverted: ${it.title}" }
                ?: "Reverted the previous action.",
            propertyName = reverted?.propertyName
                ?: reverted?.lines?.mapNotNull { it.propertyName }?.distinct()?.singleOrNull(),
            lines = reverted?.lines ?: emptyList(),
        )
    }

    private fun buildEntry(
        group: List<Transaction>,
        session: GameSession,
        definitions: GameDefinitions,
        zone: ZoneId,
        undone: Boolean = false,
    ): HistoryEntry {
        val eventTx = group.firstOrNull {
            it.transactionType == TransactionType.EVENT_APPLIED && it.eventId != null
        } ?: group.firstOrNull { it.eventId != null }
        val event = eventTx?.eventId?.let { definitions.events[it] }

        val title = if (event != null) {
            "Event: ${event.name}"
        } else {
            label(headline(group).transactionType)
        }
        val subtitle = event?.let {
            it.eventSubtitle.takeIf { text -> text.isNotBlank() }
                ?: it.eventDescription.takeIf { text -> text.isNotBlank() }
        }

        val propertyNames = group
            .mapNotNull { tx -> tx.propertyId?.let { definitions.properties[it]?.name } }
            .distinct()
        val sharedProperty = propertyNames.singleOrNull()

        // The event line adds nothing once its money movements are listed.
        val detailTransactions = group.filterNot {
            it.transactionType == TransactionType.EVENT_APPLIED && group.size > 1
        }
        val lines = detailTransactions.map {
            buildLine(it, session, definitions, hideProperty = sharedProperty != null)
        }

        return HistoryEntry(
            title = title,
            time = formatTime(group.first().timestamp, zone),
            subtitle = subtitle,
            propertyName = sharedProperty,
            lines = lines,
            undone = undone,
        )
    }

    private fun formatTime(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormatter)

    private fun buildLine(
        tx: Transaction,
        session: GameSession,
        definitions: GameDefinitions,
        hideProperty: Boolean,
    ): HistoryLine = HistoryLine(
        label = label(tx.transactionType),
        fromPlayerId = tx.fromEntity?.takeIf { it != EntityRef.BANK },
        fromPlayerName = tx.fromEntity?.let { entityName(it, session, definitions) },
        toPlayerId = tx.toEntity?.takeIf { it != EntityRef.BANK },
        toPlayerName = tx.toEntity?.let { entityName(it, session, definitions) },
        playerId = tx.playerId,
        playerName = tx.playerId?.let { PlayerDisplayNames.displayName(session, it, definitions) },
        propertyName = tx.propertyId
            ?.let { definitions.properties[it]?.name }
            ?.takeIf { !hideProperty },
        detail = detailText(tx, definitions),
    )

    private fun detailText(tx: Transaction, definitions: GameDefinitions): String? {
        val amount = tx.amount ?: return null
        return when (tx.transactionType) {
            in moneyTypes -> formatMoney(amount, definitions)
            TransactionType.PROPERTY_RENT_LEVEL_CHANGE -> "Rent level $amount"
            TransactionType.TEMPORARY_EFFECT_CREATED ->
                "Active for $amount ${plural(amount, "rent payment")}"
            TransactionType.TEMPORARY_EFFECT_CONSUMED ->
                if (amount == 0) "Effect finished" else "$amount ${plural(amount, "use")} left"
            // EVENT_APPLIED stores money for some events and a rent level for
            // others, so its amount is never rendered as currency.
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
