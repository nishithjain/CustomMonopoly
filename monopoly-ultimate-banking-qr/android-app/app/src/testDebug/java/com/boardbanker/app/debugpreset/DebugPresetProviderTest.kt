package com.boardbanker.app.debugpreset

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.engine.DefaultGameEngine
import com.boardbanker.core.model.EditionIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import android.content.res.AssetManager

class DebugPresetProviderTest {
    private lateinit var provider: DebugPresetProviderImpl
    private lateinit var store: CommittedGameSessionStore
    private lateinit var manager: ActiveGameSessionManager

    @Before
    fun setUp() {
        val repository = FakeGameSessionRepository()
        store = CommittedGameSessionStore(repository)
        manager = ActiveGameSessionManager(
            editionResolver = { AppTestSupport.editionRepository.load(it) },
            committedStore = store,
            repository = repository,
        )
        provider = DebugPresetProviderImpl(
            services = services(),
            presetPathProvider = { listOf("preset.json") },
            presetTextReader = { presetJson },
        )
    }

    private fun services() = object : DebugPresetServices {
        override val assetManager: AssetManager
            get() = error("Asset access is not used by this test")
        override val committedGameSessionStore = store
        override val activeGameSessionManager = manager
        override fun loadDefinitions(editionId: String) = AppTestSupport.editionRepository.load(editionId)
        override fun loadEditionDefinition(editionId: String) = AppTestSupport.editionRepository.loadManifest(editionId)
    }

    @Test
    fun suppliedPresetPreservesOrderTokensBalancesAndOwnership() = runTest {
        val loaded = provider.load("india-four-player-test") as DebugPresetLoadResult.Success
        val session = loaded.session
        assertEquals(listOf("USR_01", "USR_03", "USR_02", "USR_04"), session.playerJoinOrder)
        assertEquals("USR_01", session.turnState!!.activePlayerId)
        assertEquals(listOf("Car", "Ship", "Helicopter", "Aeroplane"), session.playerJoinOrder.map { id ->
            when (id) {
                "USR_01" -> "Car"
                "USR_03" -> "Ship"
                "USR_02" -> "Helicopter"
                else -> "Aeroplane"
            }
        })
        assertEquals(listOf(90000, 40000, 10000, 45000), session.playerJoinOrder.map { session.players[it]!!.balance })
        assertEquals(listOf(3, 6, 2, 7), session.playerJoinOrder.map { id -> session.properties.values.count { it.ownerPlayerId == id } })
        assertEquals(listOf(2, 2, 0, 0), session.playerJoinOrder.map { id -> session.energyGrids.values.count { it.ownerPlayerId == id } })
        assertEquals(18, session.properties.values.count { it.ownerPlayerId != null })
        assertEquals(4, session.energyGrids.values.count { it.ownerPlayerId != null })
        assertEquals(0, session.properties.values.count { state ->
            state.ownerPlayerId != null && session.properties.values.count { it.propertyId == state.propertyId && it.ownerPlayerId != null } > 1
        })
        assertTrue(session.properties.values.filter { it.propertyId in listOf("PRP_19", "PRP_20", "PRP_21", "PRP_22") }.all { it.ownerPlayerId == null })
        assertNull(session.debtResolution)
        assertNull(session.pendingEventExecution)
    }

    @Test
    fun presetCanBeCommittedResumedAndUndoRemainsAvailableAfterAnAction() = runTest {
        val session = (provider.load("india-four-player-test") as DebugPresetLoadResult.Success).session
        val started = provider.start(session) as DebugPresetLoadResult.Success
        assertEquals(started.session.gameId, store.currentSession()!!.gameId)
        assertEquals(started.session.gameId, store.loadLatestCommitted().let { (it as com.boardbanker.core.persistence.SavedGameLoadResult.Success).session.gameId })

        val engine = DefaultGameEngine(AppTestSupport.editionRepository.load(EditionIds.INDIA))
        val ended = engine.process(started.session, GameCommand.EndTurn("USR_01")).session
        val undone = engine.process(ended, GameCommand.UndoLastAction)
        assertTrue(undone.isSuccess)
        assertEquals("USR_01", undone.session.turnState!!.activePlayerId)
    }

    @Test
    fun discoveryFindsBundledPreset() = runTest {
        val presets = provider.discover()
        assertEquals(1, presets.size)
        assertEquals("india-four-player-test", presets.single().presetId)
        assertFalse(provider.hasActiveSavedGame())
    }

    @Test
    fun invalidIdsAndDuplicateOwnershipAreRejected() = runTest {
        val invalid = providerWith(presetJson.replace("PRP_01", "PRP_UNKNOWN"))
        assertTrue((invalid.load("india-four-player-test") as DebugPresetLoadResult.Failure).message.contains("Unknown property"))

        val duplicate = providerWith(presetJson.replace("\"PRP_03\"", "\"PRP_01\""))
        assertTrue((duplicate.load("india-four-player-test") as DebugPresetLoadResult.Failure).message.contains("assigned more than once"))
    }

    private fun providerWith(content: String) = DebugPresetProviderImpl(
        services = services(),
        presetPathProvider = { listOf("preset.json") },
        presetTextReader = { content },
    )

    private val presetJson = """
        {
          "presetId":"india-four-player-test","name":"India - Four Players with Assets","editionId":"india","currentPlayerId":"USR_01",
          "players":[
            {"playerId":"USR_01","name":"A","token":"Car","balance":90000,"ownedPropertyIds":["PRP_01","PRP_02","PRP_03"],"ownedEnergyGridIds":["ENG_01","ENG_02"]},
            {"playerId":"USR_03","name":"B","token":"Ship","balance":40000,"ownedPropertyIds":["PRP_04","PRP_05","PRP_06","PRP_07","PRP_08","PRP_09"],"ownedEnergyGridIds":["ENG_03","ENG_04"]},
            {"playerId":"USR_02","name":"C","token":"Helicopter","balance":10000,"ownedPropertyIds":["PRP_10","PRP_11"],"ownedEnergyGridIds":[]},
            {"playerId":"USR_04","name":"D","token":"Aeroplane","balance":45000,"ownedPropertyIds":["PRP_12","PRP_13","PRP_14","PRP_15","PRP_16","PRP_17","PRP_18"],"ownedEnergyGridIds":[]}
          ]
        }
    """.trimIndent()
}
