package com.boardbanker.app.scanner.delivery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.boardbanker.app.scanner.model.ResolvedCard

@Composable
fun CollectScanResults(
    deliverer: ScanResultDeliverer,
    consumer: ScanResultConsumer,
    onCardDelivered: (ResolvedCard) -> Unit,
) {
    LaunchedEffect(deliverer, consumer) {
        suspend fun deliverIfPending(delivery: ScanDeliveryResult) {
            if (delivery.consumer != consumer) return
            val card = deliverer.tryConsume(delivery.scanAttemptId, consumer) ?: return
            ScanDeliveryTrace.log(
                delivery.scanAttemptId,
                ScanDeliveryStage.RESULT_RECEIVED_BY_CALLER,
                "cardId=${card.cardId}",
            )
            onCardDelivered(card)
            ScanDeliveryTrace.log(
                delivery.scanAttemptId,
                ScanDeliveryStage.WORKFLOW_CONSUMED,
                "cardId=${card.cardId}",
            )
        }

        deliverer.peekPendingFor(consumer)?.let { deliverIfPending(it) }

        deliverer.deliveries.collect { delivery ->
            deliverIfPending(delivery)
        }
    }
}
