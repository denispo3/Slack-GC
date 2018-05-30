package com.denisvengrin.slackgc.fileslist

import android.arch.paging.PagedList
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.SlackFile

class FilesListResult (val authResponse: AuthResponse? = null,
                       val pagedList: PagedList<SlackFile>? = null)