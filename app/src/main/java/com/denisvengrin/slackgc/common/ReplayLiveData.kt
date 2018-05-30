package com.denisvengrin.slackgc.common

import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer

/**
 * Gathering events into buffer while observer is inactive
 * and delivers all bunch when it becomes active again
 */
class ReplayLiveData<T> : MutableLiveData<T>() {

    private val mPendingBuffer = mutableListOf<T>()

    override fun postValue(value: T) {
        addEventToBufferIfNotActive(value)
        super.postValue(value)
    }

    override fun setValue(value: T?) {
        addEventToBufferIfNotActive(value)
        super.setValue(value)
    }

    override fun observe(owner: LifecycleOwner, observer: Observer<T>) {
        super.observe(owner, Observer {
            // Fire up all the pending values
            val iterator = mPendingBuffer.iterator()

            while (iterator.hasNext()) {
                val bufferValue = iterator.next()
                if (bufferValue !== it) {
                    observer.onChanged(bufferValue)
                }
                iterator.remove()
            }

            observer.onChanged(it)
        })
    }

    private fun addEventToBufferIfNotActive(value: T?) {
        if (!hasActiveObservers()) {
            if (value != null) {
                mPendingBuffer.add(value)
            }
        }
    }
}