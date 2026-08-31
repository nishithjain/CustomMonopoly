package com.boardbanker.core

import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EditionIsolationTests {
    @Test
    fun sequentialEditionLoadsDoNotLeakDefinitions() {
        val ukRepository = EditionRepository(FileEditionFileSource(TestFixtures.dataDir))
        val uk = ukRepository.load(EditionIds.UK)
        val india = ukRepository.load(EditionIds.INDIA)
        val custom = EditionRepository(FileEditionFileSource(TestEditionResources.customTestDataDir()))
            .load(TestEditionResources.CUSTOM_TEST_EDITION_ID)

        assertNotEquals(uk.properties["PRP_01"]!!.name, india.properties["PRP_01"]!!.name)
        assertNotEquals(uk.properties.keys, custom.properties.keys)
        assertEquals(EditionIds.UK, uk.editionId)
        assertEquals(EditionIds.INDIA, india.editionId)
        assertEquals(TestEditionResources.CUSTOM_TEST_EDITION_ID, custom.editionId)
    }
}
