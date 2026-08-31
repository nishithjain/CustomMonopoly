package com.boardbanker.app.ui.screens.setup

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.audio.GameEndAudioCoordinator
import com.boardbanker.app.audio.RecordingGameAudioFeedback
import com.boardbanker.app.game.ActiveGameSessionManager
import com.boardbanker.app.persistence.CommittedGameSessionStore
import com.boardbanker.app.persistence.FakeGameSessionRepository
import com.boardbanker.app.scanner.model.ResolvedCard
import com.boardbanker.core.card.CardType
import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameStatus
import com.boardbanker.core.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.readText

class PlayerSetupUserCardFrontTest {
    private val definitions = AppTestSupport.definitions
    private lateinit var sessionManager: ActiveGameSessionManager
    private lateinit var projectRoot: Path
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        projectRoot = listOf(
            Path.of("../../"),
            Path.of("../../../"),
            Path.of("../../../../monopoly-ultimate-banking-qr"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr"),
        ).first { it.resolve("data/cards/common/android_card_front_manifest.json").toFile().exists() }
        val repository = FakeGameSessionRepository()
        sessionManager = AppTestSupport.sessionManager(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun manifestMapsAllUserFrontsAsLandscapeWithoutRotation() {
        val manifest = loadManifestCards()
        mapOf(
            "USR_01" to "Car",
            "USR_02" to "Helicopter",
            "USR_03" to "Ship",
            "USR_04" to "Aeroplane",
        ).forEach { (cardId, name) ->
            val entry = manifest.getValue(cardId)
            assertEquals("USER", entry["cardType"]!!.jsonPrimitive.content)
            assertEquals(name, entry["name"]!!.jsonPrimitive.content)
            assertEquals("LANDSCAPE", entry["orientation"]!!.jsonPrimitive.content)
            assertFalse(entry["rotationApplied"]!!.jsonPrimitive.content.toBooleanStrict())
            val source = entry["sourceFrontPath"]!!.jsonPrimitive.content.lowercase()
            assertTrue(source.endsWith(".jpg") || source.endsWith(".jpeg") || source.endsWith(".png"))
            val width = entry["width"]!!.jsonPrimitive.content.toInt()
            val height = entry["height"]!!.jsonPrimitive.content.toInt()
            assertTrue("$cardId should remain landscape", width > height)
            val assetRel = entry["asset"]!!.jsonPrimitive.content
            assertTrue(
                projectRoot.resolve("android-app/app/src/main/assets").resolve(assetRel).toFile().exists(),
            )
        }
    }

    @Test
    fun jpgUserFrontSourcesAreAcceptedInManifest() {
        val manifest = loadManifestCards()
        listOf("USR_01", "USR_02", "USR_03", "USR_04").forEach { cardId ->
            val source = manifest.getValue(cardId)["sourceFrontPath"]!!.jsonPrimitive.content.lowercase()
            assertTrue(source.endsWith(".jpg"))
        }
    }

    @Test
    fun scanningUserCardStagesNameEntryWithFrontMetadata() = runTest {
        sessionManager.createNewGame(EditionIds.UK)
        val viewModel = GameSetupViewModel(
            sessionManager = sessionManager,
            activeDefinitions = definitions,
            createNewGame = false,
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
            editionRepository = AppTestSupport.editionRepository,
        )
        advanceUntilIdle()
        viewModel.onPlayerCardScanned(
            ResolvedCard(
                cardId = "USR_01",
                cardType = CardType.USER,
                displayName = "Car",
                qrPayload = "MUB:PL:CAR",
            ),
        )
        advanceUntilIdle()
        val pending = viewModel.uiState.value.pendingRegistration
        assertNotNull(pending)
        assertEquals("USR_01", pending!!.playerId)
        assertEquals("Car", pending.tokenName)
        val entry = loadManifestCards().getValue("USR_01")
        assertEquals("LANDSCAPE", entry["orientation"]!!.jsonPrimitive.content)
        assertTrue(entry["asset"]!!.jsonPrimitive.content == "cards/common/user/usr_01.png")
    }

    @Test
    fun cancelClearsPendingRegistrationWithoutMutation() = runTest {
        sessionManager.createNewGame(EditionIds.UK)
        val viewModel = GameSetupViewModel(
            sessionManager = sessionManager,
            activeDefinitions = definitions,
            createNewGame = false,
            gameAudioFeedback = RecordingGameAudioFeedback(),
            gameEndAudioCoordinator = GameEndAudioCoordinator(),
            editionRepository = AppTestSupport.editionRepository,
        )
        advanceUntilIdle()
        viewModel.onPlayerIdScanned("USR_04")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pendingRegistration)
        viewModel.cancelPendingRegistration()
        assertNull(viewModel.uiState.value.pendingRegistration)
        assertTrue(sessionManager.currentSession()?.players?.isEmpty() == true)
        assertEquals(GameStatus.SETUP, sessionManager.currentSession()?.status)
    }

    private fun loadManifestCards(): Map<String, kotlinx.serialization.json.JsonObject> {
        val payload = Json.parseToJsonElement(
            projectRoot.resolve("data/cards/common/android_card_front_manifest.json").readText(),
        ).jsonObject
        return payload["cards"]!!.jsonObject.mapValues { it.value.jsonObject }
    }
}
