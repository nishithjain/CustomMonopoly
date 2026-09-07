package com.boardbanker.core.model

/**
 * Edition-aware helpers for Get out of Jail Pass Event Cards.
 * Identifies jail pass events by action type, not by hard-coded event IDs.
 */
fun GameDefinitions.jailPassEventIds(): Set<String> =
    events.filterValues { event ->
        event.actions.any { action ->
            action.parsedActionType() == EventActionType.GET_OUT_OF_JAIL_PASS
        }
    }.keys

fun GameDefinitions.supportsJailPassScan(): Boolean = jailPassEventIds().isNotEmpty()

fun GameDefinitions.isJailPassEvent(eventId: String): Boolean =
    eventId in jailPassEventIds()
