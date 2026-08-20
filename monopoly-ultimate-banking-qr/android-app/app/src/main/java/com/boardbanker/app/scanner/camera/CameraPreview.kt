package com.boardbanker.app.scanner.camera

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraPreview(
    cameraSource: CameraQrCodeSource,
    onCameraReady: () -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, cameraSource) {
        onDispose {
            cameraSource.stop()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                cameraSource.bindCamera(
                    lifecycleOwner = lifecycleOwner,
                    previewView = this,
                    onCameraReady = onCameraReady,
                    onCameraError = onCameraError,
                )
            }
        },
    )
}
