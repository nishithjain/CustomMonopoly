package com.boardbanker.app

import android.app.Application
import android.util.Log
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.SoundPoolGameAudioFeedback
import com.boardbanker.app.data.AndroidGameDataLoader
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.scanner.delivery.ScanResultDeliverer
import com.boardbanker.app.persistence.db.BoardBankerDatabaseFactory
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.persistence.repository.RoomGameSessionRepository
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SessionRestoreValidator

class BankingQrApplication : Application() {
    private var cachedDefinitions: GameDefinitions? = null
    private var cachedLoadError: String? = null

    lateinit var gameSessionRepository: GameSessionRepository
        private set

    lateinit var committedGameSessionStore: CommittedGameSessionStore
        private set

    lateinit var activeGameSessionManager: ActiveGameSessionManager
        private set

    val transientScanWorkflow: TransientScanWorkflowHolder = TransientScanWorkflowHolder()

    val scanResultDeliverer: ScanResultDeliverer = ScanResultDeliverer()

    lateinit var gameAudioFeedback: GameAudioFeedback
        private set

    val gameEndAudioCoordinator: GameEndAudioCoordinator = GameEndAudioCoordinator()

    val gameDefinitions: GameDefinitions
        get() = cachedDefinitions ?: throw IllegalStateException(
            cachedLoadError ?: "Game definitions are not loaded yet",
        )

    val definitionsLoadError: String?
        get() = cachedLoadError

    override fun onCreate() {
        super.onCreate()
        gameAudioFeedback = SoundPoolGameAudioFeedback(this)
        try {
            cachedDefinitions = AndroidGameDataLoader(this).load()
            cachedLoadError = null
            initializePersistence(cachedDefinitions!!)
        } catch (ex: Exception) {
            cachedLoadError = ex.message ?: ex.javaClass.simpleName
            Log.e(TAG, "Failed to load game definitions", ex)
        }
    }

    private fun initializePersistence(definitions: GameDefinitions) {
        val database = BoardBankerDatabaseFactory.create(this)
        val serializer = KotlinGameSessionSerializer()
        val restoreValidator = SessionRestoreValidator(definitions)
        gameSessionRepository = RoomGameSessionRepository(
            dao = database.savedGameDao(),
            serializer = serializer,
            restoreValidator = restoreValidator,
        )
        committedGameSessionStore = CommittedGameSessionStore(gameSessionRepository)
        activeGameSessionManager = ActiveGameSessionManager(
            definitions = definitions,
            committedStore = committedGameSessionStore,
            repository = gameSessionRepository,
        )
    }

    companion object {
        private const val TAG = "BankingQrApplication"
    }
}
