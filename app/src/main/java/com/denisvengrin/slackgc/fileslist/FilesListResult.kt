package com.denisvengrin.slackgc.fileslist

import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse

class FilesListResult (val authResponse: AuthResponse? = null,
                       val filesResponse: FilesResponse? = null)