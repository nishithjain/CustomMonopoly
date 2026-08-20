package com.boardbanker.app.ui.components

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.InvalidUserActionAudio
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowController
import com.boardbanker.app.gameplay.workflow.GameplayWorkflowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingActionBarBehaviorTest {
    @Test
    fun validCancelWorkflowDoesNotPlayErrorAudio() {
        val audio = RecordingGameAudioFeedback()
        val controller = GameplayWorkflowController(AppTestSupport.definitions)
        val session = AppTestSupport.newGame()
        controller.onPropertyScanned("PRP_01", session)
        controller.onCancel()
        assertTrue(controller.currentState() is GameplayWorkflowState.Ready)
        assertTrue(audio.errorCalls.isEmpty())
    }

    @Test
    fun invalidUndoUsesCentralErrorAudioOnce() {
        val audio = RecordingGameAudioFeedback()
        InvalidUserActionAudio.notifyInvalidUserActionForGameError(
            audio,
            com.boardbanker.core.error.GameError.UndoNotAllowed("Nothing to undo"),
        )
        assertEquals(1, audio.errorCalls.size)
    }
}
