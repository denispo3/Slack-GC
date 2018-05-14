package com.denisvengrin.slackgc.data

import com.google.gson.annotations.SerializedName

class SlackFile {

    var id: String? = null
    var created: Long = 0
    var filetype: String? = null
    var title: String? = null
    var user: String? = null
    @SerializedName("thumb_160")
    var thumb: String? = null

}