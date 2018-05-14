package com.denisvengrin.slackgc.data

import com.denisvengrin.slackgc.data.SlackFile

class FilesResponse {

    var ok: Boolean = false
    var files: List<SlackFile>? = null
    var paging: PagingInfo? = null
}