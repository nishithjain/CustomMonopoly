package com.boardbanker.core.rules

import com.boardbanker.core.model.EventMultiPlayerTransferSnapshot
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.Transaction
import com.boardbanker.core.model.TransactionType
import com.boardbanker.core.transaction.TransactionFactory

class EventMultiPlayerTransferExecutor(
    private val definitions: GameDefinitions,
    private val transactionFactory: TransactionFactory,
) {
    data class TransferResult(
        val session: GameSession,
        val transactions: List<Transaction>,
        val metadata: EventMultiPlayerTransferSnapshot.Metadata,
    )

    fun execute(
        session: GameSession,
        eventId: String,
        eventName: String,
        payerPlayerId: String,
        recipientPlayerIds: List<String>,
        amountPerRecipient: Int,
        direction: EventMultiPlayerTransferSnapshot.Direction,
        timestamp: Long,
        transferIdPrefix: String = "${session.gameId}_EVENT_TRANSFER",
    ): TransferResult {
        require(recipientPlayerIds.isNotEmpty()) { "No recipients" }
        require(amountPerRecipient > 0) { "Invalid amount" }

        val totalDue = amountPerRecipient * recipientPlayerIds.size
        var updatedSession = session
        val payer = updatedSession.players[payerPlayerId]!!
        if (payer.balance < totalDue) {
            throw IllegalStateException("Insufficient balance for event transfer")
        }

        val transactions = mutableListOf<Transaction>()
        val completedTransfers = mutableListOf<EventMultiPlayerTransferSnapshot.Transfer>()

        for (recipientId in recipientPlayerIds) {
            val (fromPlayerId, toPlayerId) = when (direction) {
                EventMultiPlayerTransferSnapshot.Direction.PAY_EACH_PLAYER ->
                    payerPlayerId to recipientId
                EventMultiPlayerTransferSnapshot.Direction.COLLECT_FROM_EACH_PLAYER ->
                    recipientId to payerPlayerId
            }
            val currentPayer = updatedSession.players[fromPlayerId]!!
            val currentReceiver = updatedSession.players[toPlayerId]!!
            updatedSession = updatedSession.copy(
                players = updatedSession.players +
                    (fromPlayerId to currentPayer.copy(balance = currentPayer.balance - amountPerRecipient)) +
                    (toPlayerId to currentReceiver.copy(balance = currentReceiver.balance + amountPerRecipient)),
            )
            val (tx, sessionAfter) = transactionFactory.create(
                session = updatedSession,
                type = TransactionType.EVENT_PLAYER_TRANSFER,
                timestamp = timestamp,
                fromEntity = fromPlayerId,
                toEntity = toPlayerId,
                playerId = payerPlayerId,
                eventId = eventId,
                amount = amountPerRecipient,
                reversible = true,
            )
            updatedSession = sessionAfter
            transactions += tx
            completedTransfers += EventMultiPlayerTransferSnapshot.Transfer(
                fromPlayerId = fromPlayerId,
                toPlayerId = toPlayerId,
                amount = amountPerRecipient,
            )
        }

        val transferId = "${transferIdPrefix}_${updatedSession.transactionCounter + 1}"
        val metadata = EventMultiPlayerTransferSnapshot.Metadata(
            transferId = transferId,
            eventId = eventId,
            eventName = eventName,
            payerPlayerId = payerPlayerId,
            direction = direction,
            transfers = completedTransfers,
            totalAmount = completedTransfers.sumOf { it.amount },
        )
        val (summaryTx, sessionAfterSummary) = transactionFactory.create(
            session = updatedSession,
            type = TransactionType.EVENT_MULTI_PLAYER_TRANSFER,
            timestamp = timestamp,
            fromEntity = payerPlayerId,
            playerId = payerPlayerId,
            eventId = eventId,
            amount = metadata.totalAmount,
            stateAfter = EventMultiPlayerTransferSnapshot.stateAfter(metadata),
            reversible = true,
        )
        return TransferResult(
            session = sessionAfterSummary,
            transactions = transactions + summaryTx,
            metadata = metadata,
        )
    }
}
