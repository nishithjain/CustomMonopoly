package com.boardbanker.core.persistence

import com.boardbanker.core.model.GameSession
import kotlinx.serialization.json.Json

interface GameSessionSerializer {
    fun serialize(session: GameSession): String
    fun deserialize(json: String): GameSession
}

class KotlinGameSessionSerializer(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : GameSessionSerializer {
    override fun serialize(session: GameSession): String =
        json.encodeToString(GameSession.serializer(), session)

    override fun deserialize(json: String): GameSession =
        this.json.decodeFromString(GameSession.serializer(), json)
}

object GameSessionSchema {
    const val CURRENT_VERSION: Int = 2
}
