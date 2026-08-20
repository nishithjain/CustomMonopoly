package com.boardbanker.app.persistence.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boardbanker.app.persistence.db.BoardBankerDatabase
import com.boardbanker.app.persistence.entity.SavedGameEntity
import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.GameResult
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameLoadResult
import com.boardbanker.core.persistence.SessionRestoreValidator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomGameSessionRepositoryInstrumentedTest {
    private lateinit var database: BoardBankerDatabase
    private lateinit var repository: RoomGameSessionRepository
    private val serializer = KotlinGameSessionSerializer()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoardBankerDatabase::class.java).build()
        repository = RoomGameSessionRepository(
            dao = database.savedGameDao(),
            serializer = serializer,
            restoreValidator = SessionRestoreValidator(AppTestSupport.definitions),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveLoadUpdateDeleteRoundTrip() = runTest {
        var session = buildSession()
        assertTrue(repository.save(session) is SaveSessionResult.Success)

        val loaded = repository.load(session.gameId)
        assertTrue(loaded is SavedGameLoadResult.Success)
        assertEquals(session, (loaded as SavedGameLoadResult.Success).session)

        session = AppTestSupport.engine.process(session, GameCommand.PurchaseProperty("USR_01", "PRP_01")).session
        assertTrue(repository.save(session) is SaveSessionResult.Success)
        val updated = repository.load(session.gameId) as SavedGameLoadResult.Success
        assertEquals(session, updated.session)

        repository.delete(session.gameId)
        assertTrue(repository.load(session.gameId) is SavedGameLoadResult.NotFound)
    }

    @Test
    fun corruptedJsonReturnsCorruptedResult() = runTest {
        database.savedGameDao().upsertGame(
            SavedGameEntity(
                gameId = "BAD",
                status = "ACTIVE",
                createdAt = 1L,
                updatedAt = 1L,
                committedAt = 1L,
                schemaVersion = 1,
                playerCount = 0,
                transactionCount = 0,
                sessionJson = "{not valid json",
            ),
        )
        val result = repository.load("BAD")
        assertTrue(result is SavedGameLoadResult.Corrupted)
    }

    @Test
    fun unsupportedSchemaVersionReturnsIncompatibleVersion() = runTest {
        database.savedGameDao().upsertGame(
            SavedGameEntity(
                gameId = "OLD",
                status = "ACTIVE",
                createdAt = 1L,
                updatedAt = 1L,
                committedAt = 1L,
                schemaVersion = 99,
                playerCount = 0,
                transactionCount = 0,
                sessionJson = serializer.serialize(buildSession()),
            ),
        )
        val result = repository.load("OLD")
        assertTrue(result is SavedGameLoadResult.IncompatibleVersion)
    }

    @Test
    fun finishedGameCanBePersisted() = runTest {
        val session = buildSession().copy(
            status = com.boardbanker.core.model.GameStatus.FINISHED,
            winnerPlayerId = "USR_01",
        )
        repository.save(session)
        val loaded = repository.load(session.gameId) as SavedGameLoadResult.Success
        assertEquals(com.boardbanker.core.model.GameStatus.FINISHED, loaded.session.status)
    }

    private fun buildSession(): GameSession {
        var result = AppTestSupport.engine.process(GameSession(gameId = "ROOM_TEST"), GameCommand.CreateGame("ROOM_TEST"))
        result = AppTestSupport.engine.process(result.session, GameCommand.RegisterPlayer("USR_01", "Nishith"))
        result = AppTestSupport.engine.process(result.session, GameCommand.RegisterPlayer("USR_02", "Aditya"))
        result = AppTestSupport.engine.process(result.session, GameCommand.StartGame)
        return result.session
    }
}
