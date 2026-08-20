package com.boardbanker.app.scanner.camera

import androidx.camera.core.ImageProxy
import com.boardbanker.app.scanner.QrDetectionEvent
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

internal class QrImageAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val onEvent: (QrDetectionEvent) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {
    @Volatile
    private var closed = false
    @Volatile
    private var processing = false

    override fun analyze(imageProxy: ImageProxy) {
        if (closed) {
            imageProxy.close()
            return
        }
        if (processing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onEvent(QrDetectionEvent.NoQrDetected)
            return
        }

        processing = true
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val payload = barcodes
                    .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    ?.rawValue
                if (payload != null) {
                    onEvent(QrDetectionEvent.QrDetected(payload))
                } else {
                    onEvent(QrDetectionEvent.NoQrDetected)
                }
            }
            .addOnFailureListener {
                onEvent(QrDetectionEvent.NoQrDetected)
            }
            .addOnCompleteListener {
                processing = false
                imageProxy.close()
            }
    }

    fun close() {
        closed = true
    }
}
