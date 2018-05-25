package com.denisvengrin.slackgc.fileslist

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.denisvengrin.slackgc.common.ViewModelResult
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse
import com.denisvengrin.slackgc.data.SlackFile
import com.denisvengrin.slackgc.network.SlackApi
import com.denisvengrin.slackgc.storage.SlackStorage
import io.reactivex.Observable
import io.reactivex.Single
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
    private val mFilesSearchSubject = PublishSubject.create<Int>()
    private var mFilesResponseLiveData: MutableLiveData<ViewModelResult<Pair<AuthResponse, FilesResponse>>>? = null

    fun getFilesResponseLiveData(): LiveData<ViewModelResult<Pair<AuthResponse, FilesResponse>>> {
        if (mFilesResponseLiveData == null) {
            mFilesResponseLiveData = MutableLiveData()
            initAuthResponse()
        }
        return mFilesResponseLiveData!!
    }

    fun addFilter(filter: String) {
        mSelectedFileTypes.add(filter)
        mFilesSearchSubject.onNext(0)
    }

    fun removeFilter(filter: String) {
        mSelectedFileTypes.remove(filter)
        mFilesSearchSubject.onNext(0)
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
                .debounce(300, TimeUnit.MILLISECONDS)
                .doOnNext {
                    mFilesResponseLiveData?.postValue(ViewModelResult.progress())
                }
                .flatMapSingle {
                    val queryMap = mapOf(
                            "page" to "1",
                            "count" to "20",
                            "user" to mAuthResponse?.userId,
                            "token" to mAuthResponse?.token,
                            "types" to mSelectedFileTypes.joinToString(",")
                    )

                    api.getFiles(queryMap)
                }
                .subscribe({
                    mFilesResponseLiveData?.postValue(ViewModelResult.success(mAuthResponse!! to it))
                }, {
                    it.printStackTrace()
                    mFilesResponseLiveData?.postValue(ViewModelResult.error(it))
                })
                .addToCompositeDisposable()

        mFilesSearchSubject.onNext(0)
    }

    fun removeSelectedFiles(selectedFiles: List<SlackFile>) {
/*        var successfulCount = 0
        var failedCount = 0

        val disposable = getDeleteFileObservable(selectedFiles)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    //Log.d(LOG_TAG, "onNext: ${Thread.currentThread().name}")
                    val slackFile = it.first

                    if (!slackFile.title.isNullOrEmpty()) {
                        progressDialog.setMessage("Removing \"${slackFile.title}\"")
                    }

                    val filesResponse = it.second

                    if (filesResponse != null) {
                        if (filesResponse.ok) {
                            successfulCount++

                            mAdapter?.notifyRemoval(slackFile)
                        } else {
                            failedCount++
                        }

                        progressDialog.progress = successfulCount + failedCount
                    }
                }, {
                    it.printStackTrace()
                }, {
                    checkRemoveBtnVisibility()
                    progressDialog.dismiss()
                    showResultsDialog(successfulCount, failedCount)
                }).addToCompositeDisposable()

        progressDialog.setOnDismissListener { disposable.dispose() }*/
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

                            emitter.onNext(file to response!!)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            emitter.onNext(SlackFile() to FilesResponse().apply { ok = false })
                        }
                    }
                }
                emitter.onComplete()
            })


    private fun Disposable.addToCompositeDisposable(): Disposable {
        mCompositeDisposable.add(this)
        return this
    }

    override fun onCleared() {
        super.onCleared()
        mCompositeDisposable.clear()
    }
}