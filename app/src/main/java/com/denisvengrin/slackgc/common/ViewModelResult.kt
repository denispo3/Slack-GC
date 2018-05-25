package com.denisvengrin.slackgc.common

class ViewModelResult<T>(val status: ViewModelStatus, val throwable: Throwable? = null, val result: T? = null) {

    companion object {
        fun <T> error(t: Throwable) = ViewModelResult<T>(ViewModelStatus.FAIL, throwable = t)
        fun <T> success(result: T) = ViewModelResult<T>(ViewModelStatus.SUCCESS, result = result)
        fun <T> progress() = ViewModelResult<T>(ViewModelStatus.PROGRESS)
    }
}