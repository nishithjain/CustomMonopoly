package com.boardbanker.app.persistence.mapper

import com.boardbanker.app.persistence.entity.SavedGameEntity
import com.boardbanker.core.model.GameSession
import com.boardbanker.core.persistence.GameSessionSchema

object SavedGameMapper {
    fun toEntity(
        session: GameSession,
        sessionJson: String,
        nowMillis: Long,
        existing: SavedGameEntity? = null,
    ): SavedGameEntity {
        val createdAt = existing?.createdAt ?: nowMillis
        return SavedGameEntity(
            gameId = session.gameId,
            status = session.status.name,
            createdAt = createdAt,
            updatedAt = nowMillis,
            committedAt = nowMillis,
            schemaVersion = GameSessionSchema.CURRENT_VERSION,
            playerCount = session.players.size,
            transactionCount = session.transactions.size,
            sessionJson = sessionJson,
        )
    }
}
