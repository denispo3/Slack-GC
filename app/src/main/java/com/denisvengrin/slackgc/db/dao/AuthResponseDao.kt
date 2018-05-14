package com.denisvengrin.slackgc.db.dao

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import com.denisvengrin.slackgc.data.AuthResponse
import io.reactivex.Single

@Dao
interface AuthResponseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(response: AuthResponse)

    @Query("DELETE FROM AuthResponse")
    fun delete()

    @Query("SELECT * FROM AuthResponse")
    fun getAuthResponse(): Single<AuthResponse>
}