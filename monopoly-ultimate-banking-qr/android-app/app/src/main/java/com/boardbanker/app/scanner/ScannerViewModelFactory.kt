package com.boardbanker.app.scanner

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.scanner.delivery.ScanResultDeliverer
import com.boardbanker.core.model.GameDefinitions

class ScannerViewModelFactory(
    private val application: Application,
    private val scanRequest: ScanRequest = ScanRequest.gameCard(),
    private val gameAudioFeedback: GameAudioFeedback? = null,
    private val scanResultDeliverer: ScanResultDeliverer? = null,
    private val definitions: GameDefinitions? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            return ScannerViewModel(
                application,
                scanRequest,
                gameAudioFeedback,
                scanResultDeliverer,
                definitions,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
