package com.boardbanker.app.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val gameId: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val committedAt: Long,
    val schemaVersion: Int,
    val playerCount: Int,
    val transactionCount: Int,
    val sessionJson: String,
)
