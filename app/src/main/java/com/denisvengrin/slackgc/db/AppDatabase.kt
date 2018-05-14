package com.denisvengrin.slackgc.db

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.db.dao.AuthResponseDao

@Database(entities = [(AuthResponse::class)], version = 2)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getAuthResponseDao(): AuthResponseDao
}