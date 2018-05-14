package com.denisvengrin.slackgc.data

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity
class AuthResponse {
    @PrimaryKey
    @SerializedName("user_id")
    var userId: String = ""
    @SerializedName("access_token")
    var token: String = ""
    var scope: String? = null
    @SerializedName("team_name")
    var teamName: String? = null
    @SerializedName("team_id")
    var teamId: String? = null
}