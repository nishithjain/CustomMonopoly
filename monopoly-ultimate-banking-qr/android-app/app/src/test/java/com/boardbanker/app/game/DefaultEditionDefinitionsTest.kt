package com.boardbanker.app.game

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DefaultEditionDefinitionsTest {
    @Test
    fun catalogueDefaultEditionLoadsWithoutActiveSession() {
        val catalog = AppTestSupport.editionRepository.loadEditionCatalog()
        val definitions = AppTestSupport.editionRepository.load(catalog.defaultEditionId)
        assertEquals(EditionIds.UK, definitions.editionId)
        assertEquals("Old Kent Road", definitions.properties["PRP_01"]!!.name)
    }

    @Test
    fun indiaDefinitionsRemainDistinctFromDefaultUk() {
        val catalog = AppTestSupport.editionRepository.loadEditionCatalog()
        val uk = AppTestSupport.editionRepository.load(catalog.defaultEditionId)
        val india = AppTestSupport.editionRepository.load(EditionIds.INDIA)
        assertNotEquals(uk.properties["PRP_01"]!!.name, india.properties["PRP_01"]!!.name)
    }

    @Test
    fun boundDefinitionsReflectSetupEditionBeforeSessionCommit() {
        val sessionManager = AppTestSupport.sessionManager()
        sessionManager.bindEditionForSetup(EditionIds.INDIA)
        val bound = sessionManager.boundDefinitionsOrNull()
        assertEquals(EditionIds.INDIA, bound?.editionId)
        assertEquals("Mehrangarh Fort", bound?.properties?.get("PRP_14")?.name)
    }
}
