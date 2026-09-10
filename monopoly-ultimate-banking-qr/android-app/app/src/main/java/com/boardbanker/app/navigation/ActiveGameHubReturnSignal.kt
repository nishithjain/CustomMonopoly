package com.boardbanker.app.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Signals sub-screens (e.g. auction) to reset the Active Game hub when returning from navigation.
 */
class ActiveGameHubReturnSignal {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun requestReturnToActiveGameHub() {
        _requests.tryEmit(Unit)
    }
}
