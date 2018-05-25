package com.denisvengrin.slackgc.fileslist

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import com.denisvengrin.slackgc.network.SlackApi
import com.denisvengrin.slackgc.storage.SlackStorage

class FileListViewModelFactory(val api: SlackApi, val storage: SlackStorage) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FilesListViewModel::class.java)) {
            return FilesListViewModel(api, storage) as T
        }
        throw IllegalArgumentException("Passed class can't be instantiated by this factory")
    }
}