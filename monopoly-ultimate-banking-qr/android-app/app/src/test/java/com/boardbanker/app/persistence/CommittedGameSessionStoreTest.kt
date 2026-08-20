package com.boardbanker.app.persistence

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameOutcome
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedGameSessionStoreTest {
    private val repository = FakeGameSessionRepository()
    private val store = CommittedGameSessionStore(repository)

    @Test
    fun successfulGameResultTriggersSave() = runTest {
        val session = AppTestSupport.newGame()
        val result = GameResult(session = session)
        val commit = store.commitGameResult(result)
        assertTrue(commit is CommitResult.Persisted)
        assertEquals(1, repository.saveCallCount)
    }

    @Test
    fun rejectedGameResultDoesNotSave() = runTest {
        val session = AppTestSupport.newGame()
        val owned = session.copy(
            properties = session.properties + (
                "PRP_07" to com.boardbanker.core.model.PropertyState("PRP_07", "USR_02", 1)
            ),
        )
        val rejected = AppTestSupport.engine.process(
            owned,
            GameCommand.PurchaseProperty("USR_01", "PRP_07"),
        )
        val commit = store.commitGameResult(rejected)
        assertTrue(commit is CommitResult.NotPersisted)
        assertEquals(0, repository.saveCallCount)
    }

    @Test
    fun validationFailureDoesNotSave() = runTest {
        val session = AppTestSupport.newGame()
        val result = GameResult(
            session = session,
            outcome = GameOutcome.REJECTED,
            error = com.boardbanker.core.error.GameError.Validation("invalid"),
        )
        val commit = store.commitGameResult(result)
        assertTrue(commit is CommitResult.NotPersisted)
        assertEquals(0, repository.saveCallCount)
    }
}
