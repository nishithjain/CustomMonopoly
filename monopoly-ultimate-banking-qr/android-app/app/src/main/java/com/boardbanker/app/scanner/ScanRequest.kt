package com.boardbanker.app.scanner

import com.boardbanker.app.player.PlayerDisplayNames
import com.boardbanker.core.card.CardType
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.model.PropertyDisplayNames

enum class ScanContext {
    ANY_GAME_CARD,
    PLAYER,
    PROPERTY,
    EVENT,
    PLAYER_OR_PROPERTY,
    UNDO_AUTHORIZATION,
}

/**
 * What the QR scanner currently expects. Heading and camera overlay both use [instruction].
 */
data class ScanRequest(
    val acceptedCardTypes: Set<CardType>,
    val specificCardId: String? = null,
    val specificCardName: String? = null,
    val instruction: String,
    val mismatchInstruction: String,
    val context: ScanContext,
) {
    val heading: String get() = instruction
    val overlayInstruction: String get() = instruction
    val singleExpectedType: CardType? get() = acceptedCardTypes.singleOrNull()

    companion object {
        private val allGameTypes = setOf(CardType.USER, CardType.PROPERTY, CardType.EVENT)

        fun gameCard(): ScanRequest = ofTypes(allGameTypes, ScanContext.ANY_GAME_CARD)

        fun player(
            specificCardId: String? = null,
            specificCardName: String? = null,
            useTokenForm: Boolean = false,
        ): ScanRequest {
            val instruction = playerInstruction(specificCardName, useTokenForm)
            return ScanRequest(
                acceptedCardTypes = setOf(CardType.USER),
                specificCardId = specificCardId,
                specificCardName = specificCardName,
                instruction = instruction,
                mismatchInstruction = "Please ${instruction.replaceFirst("Scan", "scan")}.",
                context = ScanContext.PLAYER,
            )
        }

        fun property(
            specificCardId: String? = null,
            specificCardName: String? = null,
        ): ScanRequest {
            val instruction = if (specificCardName.isNullOrBlank()) {
                "Scan a Property Card"
            } else {
                "Scan $specificCardName Property Card"
            }
            return ScanRequest(
                acceptedCardTypes = setOf(CardType.PROPERTY),
                specificCardId = specificCardId,
                specificCardName = specificCardName,
                instruction = instruction,
                mismatchInstruction = "Please ${instruction.replaceFirst("Scan", "scan")}.",
                context = ScanContext.PROPERTY,
            )
        }

        fun event(
            specificCardId: String? = null,
            specificCardName: String? = null,
        ): ScanRequest {
            val instruction = if (specificCardName.isNullOrBlank()) {
                "Scan an Event Card"
            } else {
                "Scan $specificCardName Event Card"
            }
            return ScanRequest(
                acceptedCardTypes = setOf(CardType.EVENT),
                specificCardId = specificCardId,
                specificCardName = specificCardName,
                instruction = instruction,
                mismatchInstruction = "Please ${instruction.replaceFirst("Scan", "scan")}.",
                context = ScanContext.EVENT,
            )
        }

        fun playerOrProperty(): ScanRequest =
            ofTypes(setOf(CardType.USER, CardType.PROPERTY), ScanContext.PLAYER_OR_PROPERTY)

        fun undoAuthorization(remainingPlayers: Int): ScanRequest {
            val remaining = remainingPlayers.coerceAtLeast(1)
            val suffix = if (remaining == 1) "1 player remaining" else "$remaining players remaining"
            val base = player()
            return base.copy(
                instruction = "Scan a Player Card — $suffix",
                mismatchInstruction = "Please scan a Player Card.",
                context = ScanContext.UNDO_AUTHORIZATION,
            )
        }

        fun fromExpectedType(expectedCardType: CardType?): ScanRequest = when (expectedCardType) {
            CardType.USER -> player()
            CardType.PROPERTY -> property()
            CardType.EVENT -> event()
            null -> gameCard()
        }

        fun forPlayerId(
            playerId: String,
            session: GameSession?,
            definitions: GameDefinitions,
        ): ScanRequest {
            val token = PlayerDisplayNames.tokenName(playerId, definitions)
            val display = PlayerDisplayNames.displayName(session, playerId, definitions)
            val useTokenForm = display == token
            return player(
                specificCardId = playerId,
                specificCardName = display,
                useTokenForm = useTokenForm,
            )
        }

        fun forPropertyId(propertyId: String, definitions: GameDefinitions): ScanRequest {
            val name = definitions.properties[propertyId]
                ?.let { PropertyDisplayNames.displayNameWithNumber(it) }
                ?.takeIf { it.isNotBlank() }
            return if (name == null) property() else property(propertyId, name)
        }

        fun forEventId(eventId: String, definitions: GameDefinitions): ScanRequest {
            val name = definitions.events[eventId]?.name?.takeIf { it.isNotBlank() }
            return if (name == null) event() else event(eventId, name)
        }

        private fun playerInstruction(specificCardName: String?, useTokenForm: Boolean): String {
            if (specificCardName.isNullOrBlank()) {
                return "Scan a Player Card"
            }
            return if (useTokenForm) {
                "Scan the $specificCardName Player Card"
            } else {
                "Scan ${possessive(specificCardName)} Player Card"
            }
        }

        private fun ofTypes(types: Set<CardType>, context: ScanContext): ScanRequest {
            val instruction = instructionForTypes(types)
            return ScanRequest(
                acceptedCardTypes = types,
                instruction = instruction,
                mismatchInstruction = "Please ${instruction.replaceFirst("Scan", "scan")}.",
                context = context,
            )
        }

        internal fun instructionForTypes(types: Set<CardType>): String {
            val ordered = listOf(CardType.USER, CardType.PROPERTY, CardType.EVENT).filter { it in types }
            if (ordered.isEmpty() || ordered.size == 3) {
                return "Scan a Game Card"
            }
            val phrases = ordered.map { typePhrase(it) }
            return when (phrases.size) {
                1 -> "Scan ${withArticle(phrases[0])}"
                else -> {
                    val firstNoun = phrases[0].removeSuffix(" Card")
                    val article = if (firstNoun == "Event") "an" else "a"
                    "Scan $article $firstNoun or ${phrases[1]}"
                }
            }
        }

        internal fun typePhrase(type: CardType): String = when (type) {
            CardType.USER -> "Player Card"
            CardType.PROPERTY -> "Property Card"
            CardType.EVENT -> "Event Card"
        }

        private fun withArticle(phrase: String): String =
            if (phrase.startsWith("Event")) "an $phrase" else "a $phrase"

        internal fun possessive(name: String): String =
            if (name.endsWith("s", ignoreCase = true)) "$name'" else "$name's"
    }
}
