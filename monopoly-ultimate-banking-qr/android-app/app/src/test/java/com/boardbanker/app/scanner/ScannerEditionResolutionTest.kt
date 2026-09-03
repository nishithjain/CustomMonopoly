package com.boardbanker.app.scanner

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.game.ProcessCommitResult
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.scanner.ScanProcessorResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerEditionResolutionTest {
    @Test
    fun indiaSession_resolvesIndianPropertyNames() = runTest {
        val (sessionManager, _) = AppTestSupport.sessionManagerWithStore()
        val result = sessionManager.createNewGame(EditionIds.INDIA)
        assertTrue(result is ProcessCommitResult.Committed)

        val indiaDefinitions = sessionManager.boundDefinitionsOrNull()
            ?: error("India edition should be bound after createNewGame")
        val controller = ScannerController(indiaDefinitions)

        val resolved = controller.onQrPayload("MUB:P:14") as ScanProcessorResult.CardResolved
        assertEquals("PRP_14", resolved.resolution.cardId)
        assertEquals("[14] Mehrangarh Fort", resolved.resolution.displayName)
    }

    @Test
    fun ukSession_resolvesUkPropertyNames() = runTest {
        val (sessionManager, _) = AppTestSupport.sessionManagerWithStore()
        val result = sessionManager.createNewGame(EditionIds.UK)
        assertTrue(result is ProcessCommitResult.Committed)

        val ukDefinitions = sessionManager.boundDefinitionsOrNull()
            ?: error("UK edition should be bound after createNewGame")
        val controller = ScannerController(ukDefinitions)

        val resolved = controller.onQrPayload("MUB:P:14") as ScanProcessorResult.CardResolved
        assertEquals("PRP_14", resolved.resolution.cardId)
        assertEquals("[14] Trafalgar Square", resolved.resolution.displayName)
    }
}
