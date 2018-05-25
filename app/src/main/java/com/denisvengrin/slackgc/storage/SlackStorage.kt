package com.denisvengrin.slackgc.storage

import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.db.AppDatabase
import io.reactivex.Completable
import io.reactivex.Single

class SlackStorage(private val db: AppDatabase) {

    fun getAuthResponse(): Single<AuthResponse> {
        return db.getAuthResponseDao().getAuthResponse()
    }

    fun setAuthResponse(authResponse: AuthResponse): Completable {
        return Completable.fromAction { db.getAuthResponseDao().insert(authResponse) }
    }

    fun removeAuthResponse(): Completable {
        return Completable.fromAction { db.getAuthResponseDao().delete() }
    }
}