package com.denisvengrin.slackgc.fileslist

import com.denisvengrin.slackgc.data.FilesResponse
import com.denisvengrin.slackgc.data.SlackFile

class RemovalResult(var slackFile: SlackFile? = null,
                    var filesResponse: FilesResponse? = null,
                    var totalCount: Int = 0,
                    var successfulCount: Int = 0,
                    var failedCount: Int = 0) {
}