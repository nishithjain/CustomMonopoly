package com.boardbanker.app.persistence.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.boardbanker.app.persistence.entity.SavedGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGame(entity: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE gameId = :gameId LIMIT 1")
    suspend fun getGame(gameId: String): SavedGameEntity?

    @Query(
        """
        SELECT * FROM saved_games
        WHERE status IN ('ACTIVE', 'SETUP')
        ORDER BY committedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestActiveGame(): SavedGameEntity?

    @Query(
        """
        SELECT * FROM saved_games
        WHERE status IN ('ACTIVE', 'SETUP')
        ORDER BY committedAt DESC
        LIMIT 1
        """,
    )
    fun observeLatestActiveGame(): Flow<SavedGameEntity?>

    @Query("SELECT * FROM saved_games ORDER BY committedAt DESC")
    fun observeSavedGames(): Flow<List<SavedGameEntity>>

    @Query("SELECT * FROM saved_games ORDER BY committedAt DESC")
    suspend fun getAllGames(): List<SavedGameEntity>

    @Query("DELETE FROM saved_games WHERE gameId = :gameId")
    suspend fun deleteGame(gameId: String)

    @Query("DELETE FROM saved_games")
    suspend fun deleteAllGames()
}
