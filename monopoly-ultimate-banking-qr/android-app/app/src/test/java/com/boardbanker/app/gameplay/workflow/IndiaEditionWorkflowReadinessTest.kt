package com.boardbanker.app.gameplay.workflow

import com.boardbanker.app.AppTestSupport
import com.boardbanker.core.model.EditionIds
import org.junit.Assert.assertEquals
import org.junit.Test

class IndiaEditionWorkflowReadinessTest {
    private val india = AppTestSupport.editionRepository.load(EditionIds.INDIA)

    @Test
    fun all25IndiaEvents_haveWorkflowPatterns() {
        val eventIds = india.events.keys.sorted()
        assertEquals(25, eventIds.size)
        val coverage = EventWorkflowPlanner.coverageForAllEvents(
            eventIds,
            india.events.mapValues { it.value.actions.first() },
        )
        assertEquals(25, coverage.size)
        assertEquals(eventIds.toSet(), coverage.keys)
    }

    @Test
    fun indiaEventWorkflowPlans_buildForEveryAction() {
        val failures = mutableListOf<String>()
        for ((eventId, event) in india.events) {
            for (index in event.actions.indices) {
                runCatching {
                    EventWorkflowPlanner.planForEventAtAction(event, index)
                }.onFailure {
                    failures += "$eventId action $index: ${it.message}"
                }
            }
        }
        assertEquals(emptyList<String>(), failures)
    }
}
