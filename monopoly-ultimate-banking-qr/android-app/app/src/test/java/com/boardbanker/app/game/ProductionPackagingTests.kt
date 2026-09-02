package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.TestEditionResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class ProductionPackagingTests {
    @Test
    fun productionCatalogueExcludesCustomTest() {
        val catalog = AppTestSupport.editionRepository.loadEditionCatalog()
        assertFalse(catalog.editions.any { it.editionId == TestEditionResources.CUSTOM_TEST_EDITION_ID })
    }

    @Test
    fun productionCatalogueIncludesIndiaEdition() {
        val catalog = AppTestSupport.editionRepository.loadEditionCatalog()
        val india = catalog.editions.firstOrNull { it.editionId == com.boardbanker.core.model.EditionIds.INDIA }
        assertNotNull(india)
        assertTrue(india!!.enabled)
        assertEquals("India Edition", india.name)
    }

    @Test
    fun syncedAssetsExcludeCustomTestEdition() {
        val assetsRoot = resolveAssetsRoot()
        val customTestEdition = assetsRoot.resolve("editions/custom-test")
        assertFalse(customTestEdition.toFile().exists())
    }

    private fun resolveAssetsRoot(): Path {
        val candidates = listOf(
            Path.of("src/main/assets/game"),
            Path.of("app/src/main/assets/game"),
            Path.of("../app/src/main/assets/game"),
        )
        return candidates.firstOrNull { it.resolve("editions/index.json").toFile().exists() }
            ?: error("Could not locate synced Android game assets")
    }
}
