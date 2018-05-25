package com.denisvengrin.slackgc.di

import android.arch.persistence.room.Room
import android.content.Context
import com.denisvengrin.slackgc.db.AppDatabase
import com.denisvengrin.slackgc.storage.SlackStorage
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PersistenceModule(private val context: Context) {

    @Singleton
    @Provides
    fun provideStorage(): SlackStorage {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()

        return SlackStorage(db)
    }

    companion object {
        private const val DB_NAME = "slack_gc"
    }
}