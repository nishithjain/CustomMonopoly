package com.boardbanker.app.persistence.db

import android.content.Context
import androidx.room.Room
import com.boardbanker.app.persistence.entity.SavedGameEntity

object BoardBankerDatabaseFactory {
    private const val DATABASE_NAME = "boardbanker.db"

    fun create(context: Context): BoardBankerDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            BoardBankerDatabase::class.java,
            DATABASE_NAME,
        ).build()

    fun createInMemory(context: Context): BoardBankerDatabase =
        Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            BoardBankerDatabase::class.java,
        ).build()
}
