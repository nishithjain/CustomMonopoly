package com.boardbanker.app

import android.app.Application
import android.util.Log
import com.boardbanker.app.audio.GameAudioFeedback
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.SoundPoolGameAudioFeedback
import com.boardbanker.app.data.AndroidGameDataLoader
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.TransientScanWorkflowHolder
import com.boardbanker.app.gameplay.location.LocationWorkflowHolder
import com.boardbanker.app.scanner.delivery.ScanResultDeliverer
import com.boardbanker.app.persistence.db.BoardBankerDatabaseFactory
import com.boardbanker.app.persistence.repository.EditionAwareGameSessionRepository
import com.boardbanker.app.persistence.repository.GameSessionRepository
import com.boardbanker.app.persistence.repository.RoomGameSessionRepository
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.persistence.KotlinGameSessionSerializer
import com.boardbanker.core.persistence.SavedGameRestoreOrchestrator

class BankingQrApplication : Application() {
    private var startupError: String? = null

    lateinit var editionRepository: EditionRepository
        private set

    lateinit var gameSessionRepository: GameSessionRepository
        private set

    lateinit var committedGameSessionStore: CommittedGameSessionStore
        private set

    lateinit var activeGameSessionManager: ActiveGameSessionManager
        private set

    val transientScanWorkflow: TransientScanWorkflowHolder = TransientScanWorkflowHolder()

    val locationWorkflowHolder: LocationWorkflowHolder = LocationWorkflowHolder()

    val scanResultDeliverer: ScanResultDeliverer = ScanResultDeliverer()

    lateinit var gameAudioFeedback: GameAudioFeedback
        private set

    val gameEndAudioCoordinator: GameEndAudioCoordinator = GameEndAudioCoordinator()

    private var defaultDefinitionsCache: GameDefinitions? = null

    val defaultGameDefinitions: GameDefinitions
        get() {
            defaultDefinitionsCache?.let { return it }
            ensureEditionServicesReady()
            val catalog = editionRepository.loadEditionCatalog()
            return editionRepository.load(catalog.defaultEditionId).also {
                defaultDefinitionsCache = it
            }
        }

    val gameDefinitions: GameDefinitions
        get() = activeGameSessionManager.currentSession()?.let { session ->
            editionRepository.load(session.editionId)
        } ?: error(startupError ?: "No active game session is bound to an edition")

    fun definitionsForActiveSessionOrDefault(): GameDefinitions {
        ensureEditionServicesReady()
        activeGameSessionManager.currentSession()?.let { session ->
            return editionRepository.load(session.editionId)
        }
        activeGameSessionManager.boundDefinitionsOrNull()?.let { return it }
        return defaultGameDefinitions
    }

    val definitionsLoadError: String?
        get() = startupError

    override fun onCreate() {
        super.onCreate()
        gameAudioFeedback = SoundPoolGameAudioFeedback(this)
        try {
            val dataLoader = AndroidGameDataLoader(this)
            editionRepository = dataLoader.editionRepository
            initializePersistence(dataLoader)
            startupError = null
        } catch (ex: Exception) {
            startupError = ex.message ?: ex.javaClass.simpleName
            Log.e(TAG, "Failed to initialize application services", ex)
        }
    }

    private fun initializePersistence(dataLoader: AndroidGameDataLoader) {
        val database = BoardBankerDatabaseFactory.create(this)
        val serializer = KotlinGameSessionSerializer()
        val roomRepository = RoomGameSessionRepository(
            dao = database.savedGameDao(),
            serializer = serializer,
        )
        val restoreOrchestrator = SavedGameRestoreOrchestrator(
            serializer = serializer,
            editionLoader = { editionId -> dataLoader.load(editionId) },
            manifestLoader = { editionId -> editionRepository.loadManifest(editionId) },
        )
        gameSessionRepository = EditionAwareGameSessionRepository(
            rawReader = roomRepository,
            storage = roomRepository,
            restoreOrchestrator = restoreOrchestrator,
        )
        committedGameSessionStore = CommittedGameSessionStore(gameSessionRepository)
        activeGameSessionManager = ActiveGameSessionManager(
            editionResolver = { editionId -> dataLoader.load(editionId) },
            committedStore = committedGameSessionStore,
            repository = gameSessionRepository,
        )
    }

    private fun ensureEditionServicesReady() {
        if (startupError != null) {
            error(startupError ?: "Edition data is unavailable")
        }
        if (!::editionRepository.isInitialized) {
            error("Edition repository is not initialized")
        }
    }

    companion object {
        private const val TAG = "BankingQrApplication"
    }
}
