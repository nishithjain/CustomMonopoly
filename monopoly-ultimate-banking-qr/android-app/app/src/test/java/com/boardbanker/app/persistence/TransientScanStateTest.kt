package com.boardbanker.app.persistence

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientScanStateTest {
    @Test
    fun grSave002RestoresCommittedSessionAndResetsScannerWorkflow() = runTest {
        val repository = FakeGameSessionRepository()
        val store = CommittedGameSessionStore(repository)
        val workflow = TransientScanWorkflowHolder()

        var session = AppTestSupport.newGame(listOf("USR_01", "USR_02"))
        session = AppTestSupport.engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        store.commitGameResult(GameResult(session = session))

        workflow.enterWaitingForPlayer()
        assertEquals(ScanWorkflowState.WAITING_FOR_PLAYER, workflow.workflowState)

        val restartedStore = CommittedGameSessionStore(repository)
        val loadResult = restartedStore.loadLatestCommitted()
        assertTrue(loadResult is com.boardbanker.core.persistence.SavedGameLoadResult.Success)
        val restored = (loadResult as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session
        assertEquals(session, restored)

        workflow.resetToReady()
        assertEquals(ScanWorkflowState.READY, workflow.workflowState)
    }
}
