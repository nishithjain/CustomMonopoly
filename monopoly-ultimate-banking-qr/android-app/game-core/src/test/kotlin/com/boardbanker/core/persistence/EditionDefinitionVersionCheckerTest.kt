package com.boardbanker.core.persistence

import com.boardbanker.core.TestFixtures
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionDefinitionVersionCheckerTest {
    private val checker = EditionDefinitionVersionChecker { editionId ->
        TestFixtures.loadEdition(editionId).edition!!
    }

    @Test
    fun matchingVersionAllowsResume() {
        val session = GameSession(
            gameId = "MATCH",
            editionId = EditionIds.UK,
            editionDefinitionVersion = 1,
            status = GameStatus.ACTIVE,
        )
        assertNull(checker.check(session))
    }

    @Test
    fun savedVersionOneInstalledVersionTwoIsRejected() {
        val checker = EditionDefinitionVersionChecker { editionId ->
            TestFixtures.loadEdition(editionId).edition!!.copy(definitionVersion = 2)
        }
        val session = GameSession(
            gameId = "MISMATCH",
            editionId = EditionIds.INDIA,
            editionDefinitionVersion = 1,
            status = GameStatus.ACTIVE,
        )
        val result = checker.check(session) as SavedGameLoadResult.IncompatibleEditionVersion
        assertEquals(EditionIds.INDIA, result.editionId)
        assertEquals(1, result.savedVersion)
        assertEquals(2, result.installedVersion)
        assertEquals(GameStatus.ACTIVE, result.gameStatus)
    }

    @Test
    fun savedVersionTwoInstalledVersionOneIsRejected() {
        val checker = EditionDefinitionVersionChecker { editionId ->
            TestFixtures.loadEdition(editionId).edition!!.copy(definitionVersion = 1)
        }
        val session = GameSession(
            gameId = "MISMATCH",
            editionId = EditionIds.UK,
            editionDefinitionVersion = 2,
        )
        val result = checker.check(session) as SavedGameLoadResult.IncompatibleEditionVersion
        assertEquals(2, result.savedVersion)
        assertEquals(1, result.installedVersion)
    }

    @Test
    fun missingEditionReturnsTypedError() {
        val checker = EditionDefinitionVersionChecker { _ ->
            throw IllegalArgumentException("Edition package missing")
        }
        val result = checker.check(
            GameSession(gameId = "MISSING", editionId = "missing-edition", editionDefinitionVersion = 1),
        ) as SavedGameLoadResult.MissingEdition
        assertEquals("missing-edition", result.editionId)
        assertTrue(result.reason.contains("missing"))
    }
}
