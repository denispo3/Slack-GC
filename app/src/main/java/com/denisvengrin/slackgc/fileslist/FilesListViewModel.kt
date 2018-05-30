package com.denisvengrin.slackgc.fileslist

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import com.denisvengrin.slackgc.common.ReplayLiveData
import com.denisvengrin.slackgc.common.ViewModelResult
import com.denisvengrin.slackgc.common.ViewModelStatus
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse
import com.denisvengrin.slackgc.data.SlackFile
import com.denisvengrin.slackgc.network.SlackApi
import com.denisvengrin.slackgc.storage.SlackStorage
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class FilesListViewModel(val api: SlackApi, val storage: SlackStorage) : ViewModel() {

    private var mAuthResponse: AuthResponse? = null
    private val mCompositeDisposable: CompositeDisposable = CompositeDisposable()

    private val mSelectedFileTypes = mutableSetOf<String>()
    private val mFilesSearchSubject = PublishSubject.create<String>()
    private var mFilesResponseLiveData: MutableLiveData<ViewModelResult<FilesListResult>>? = null
    private val mRemoveFileLiveData: MutableLiveData<ViewModelResult<RemovalResult>> = ReplayLiveData()

    fun getFilesResponseLiveData(): LiveData<ViewModelResult<FilesListResult>> {
        if (mFilesResponseLiveData == null) {
            mFilesResponseLiveData = MutableLiveData()
            initAuthResponse()
        }
        return mFilesResponseLiveData!!
    }

    fun addFilter(filter: String) {
        mSelectedFileTypes.add(filter)
        pushFilesSearch()
    }

    fun removeFilter(filter: String) {
        mSelectedFileTypes.remove(filter)
        pushFilesSearch()
    }

    private fun initAuthResponse() {
        storage.getAuthResponse()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    mAuthResponse = it
                    initFilesSearchSubject()
                }, {
                    it.printStackTrace()
                    mFilesResponseLiveData?.setValue(ViewModelResult.error(it))
                }).addToCompositeDisposable()
    }

    private fun initFilesSearchSubject() {
        mFilesSearchSubject
                .distinctUntilChanged()
                .debounce(500, TimeUnit.MILLISECONDS)
                .doOnNext {
                    mFilesResponseLiveData?.postValue(ViewModelResult.progress())
                }
                .flatMapSingle { selectedTypes ->
                    val queryMap = mapOf(
                            "page" to "1",
                            "count" to "20",
                            "user" to mAuthResponse?.userId,
                            "token" to mAuthResponse?.token,
                            "types" to selectedTypes
                    )

                    api.getFiles(queryMap)
                }
                .subscribe({
                    val result = FilesListResult(mAuthResponse, it)
                    mFilesResponseLiveData?.postValue(ViewModelResult.success(result))
                }, {
                    it.printStackTrace()
                    mFilesResponseLiveData?.postValue(ViewModelResult.error(it))
                })
                .addToCompositeDisposable()

        pushFilesSearch()
    }

    private fun pushFilesSearch() {
        mFilesSearchSubject.onNext(mSelectedFileTypes.joinToString(","))
    }

    /**
     * If call during configuration change data won't be propagated
     * */
    fun clearRemoveFilesLiveData() {
        mRemoveFileLiveData.setValue(null)
    }

    fun getRemoveFilesLiveData() = mRemoveFileLiveData

    fun removeSelectedFiles(selectedFiles: List<SlackFile>) {
        var successfulCount = 0
        var failedCount = 0
        val totalCount = selectedFiles.size

        getDeleteFileObservable(selectedFiles)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    val slackFile = it.first
                    val filesResponse = it.second

                    if (filesResponse != null) {
                        if (filesResponse.ok) {
                            successfulCount++
                        } else {
                            failedCount++
                        }
                    }

                    val removalResult = RemovalResult(slackFile, filesResponse, totalCount, successfulCount, failedCount)
                    val viewModelResult = ViewModelResult(ViewModelStatus.PROGRESS, result = removalResult)
                    mRemoveFileLiveData.setValue(viewModelResult)

                    mFilesResponseLiveData?.value?.result?.filesResponse?.files?.remove(slackFile)
                }, {
                    it.printStackTrace()
                }, {
                    mRemoveFileLiveData.setValue(ViewModelResult(ViewModelStatus.SUCCESS,
                            result = RemovalResult(totalCount = totalCount,
                                    successfulCount = successfulCount,
                                    failedCount = failedCount)))
                }).addToCompositeDisposable()
    }

    /**
     * @return an Observable, that emits [Pair] with:
     * 1) [SlackFile] and empty [FilesResponse]  on first step to init progress
     * 2) [SlackFile] and [FilesResponse] received from server
     * 3) Empty [SlackFile] and [FilesResponse] with *success=false* in case of failed request
     */
    private fun getDeleteFileObservable(selectedFiles: List<SlackFile>) =
            Observable.create<Pair<SlackFile, FilesResponse?>>({ emitter ->
                val filesToRemove = ArrayList(selectedFiles)
                for (file in filesToRemove) {
                    if (!emitter.isDisposed) {
                        emitter.onNext(file to null)

                        val query = mapOf("file" to file.id,
                                "token" to mAuthResponse?.token)

                        val call = api.deleteFile(query = query)

                        emitter.setCancellable { call.cancel() }

                        try {
                            val response = call.execute().body()
                            /*val response = FilesResponse().apply {
                                this.ok = true
                            }
                            Thread.sleep(1000)*/
                            emitter.onNext(file to response!!)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            emitter.onNext(SlackFile() to FilesResponse().apply { ok = false })
                        }
                    }
                }
                emitter.onComplete()
            })

    /*
    * Cancel all running tasks
    * */
    fun clearTasks() {
        mCompositeDisposable.clear()
    }

    private fun Disposable.addToCompositeDisposable(): Disposable {
        mCompositeDisposable.add(this)
        return this
    }

    override fun onCleared() {
        super.onCleared()
        mCompositeDisposable.clear()
    }
}