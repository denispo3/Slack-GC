package com.denisvengrin.slackgc.data

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SlackFile(var id: String? = null,
                     var created: Long = 0,
                     var filetype: String? = null,
                     var title: String? = null,
                     var user: String? = null,
                     @SerializedName("thumb_160") var thumb: String? = null) : Parcelable