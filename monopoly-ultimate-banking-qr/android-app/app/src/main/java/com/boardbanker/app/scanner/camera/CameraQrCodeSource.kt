package com.boardbanker.app.scanner.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.boardbanker.app.scanner.QrCodeSource
import com.boardbanker.app.scanner.QrDetectionEvent
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraQrCodeSource(
    context: Context,
) : QrCodeSource {
    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var barcodeScanner: BarcodeScanner? = null

    private val _detections = MutableSharedFlow<QrDetectionEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val detections: SharedFlow<QrDetectionEvent> = _detections.asSharedFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: QrImageAnalyzer? = null
    private var released = false

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraReady: () -> Unit,
        onCameraError: (String) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val scanner = barcodeScanner ?: createBarcodeScanner().also { barcodeScanner = it }
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                analyzer = QrImageAnalyzer(scanner) { event ->
                    _detections.tryEmit(event)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor, analyzer!!)
                    }

                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                onCameraReady()
            } catch (ex: Exception) {
                onCameraError(ex.message ?: "Camera initialization failed")
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun start() = Unit

    override fun stop() {
        if (released) return
        cameraProvider?.unbindAll()
    }

    fun release() {
        if (released) return
        released = true
        stop()
        analyzer?.close()
        analyzer = null
        barcodeScanner?.close()
        barcodeScanner = null
        cameraExecutor.shutdown()
    }

    private fun createBarcodeScanner(): BarcodeScanner {
        MlKitInitializer.ensureInitialized(appContext)
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
}
