package com.boardbanker.app.persistence.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.boardbanker.app.persistence.entity.SavedGameEntity

@Database(
    entities = [SavedGameEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BoardBankerDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao
}
