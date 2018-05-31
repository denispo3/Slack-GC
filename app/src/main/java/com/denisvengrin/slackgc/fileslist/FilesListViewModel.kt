package com.denisvengrin.slackgc.fileslist

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.paging.*
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
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class FilesListViewModel(val api: SlackApi, val storage: SlackStorage) : ViewModel() {

    private var mAuthResponse: AuthResponse? = null
    private var mDeleteDisposable: Disposable? = null
    private val mCompositeDisposable: CompositeDisposable = CompositeDisposable()

    private val mSelectedFileTypes = mutableSetOf<String>()
    private val mFilesSearchSubject = PublishSubject.create<Pair<Boolean, String>>()
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
                    pushFilesSearch()
                }, {
                    it.printStackTrace()
                    mFilesResponseLiveData?.setValue(ViewModelResult.error(it))
                }).addToCompositeDisposable()
    }

    private fun initFilesSearchSubject() {
        mFilesSearchSubject
                .distinctUntilChanged { prevValue, newValue ->
                    !newValue.first && (prevValue.second == newValue.second)
                }
                .debounce(500, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.io())
                .doOnNext {
                    mFilesResponseLiveData?.postValue(ViewModelResult.progress())
                }
                .flatMapSingle { getFilesLoadObservable(STARTING_PAGE) }
                .flatMap({
                    getPagedListObservable(it)
                })
                .subscribe({
                    val result = FilesListResult(mAuthResponse, it)
                    mFilesResponseLiveData?.postValue(ViewModelResult.success(result))
                }, {
                    it.printStackTrace()
                    mFilesResponseLiveData?.postValue(ViewModelResult.error(it))
                })
                .addToCompositeDisposable()
    }

    private fun pushFilesSearch(force: Boolean = false) {
        mFilesSearchSubject.onNext(force to mSelectedFileTypes.joinToString(","))
    }

    private fun getFilesLoadObservable(page: Int): Single<FilesResponse> {
        val queryMap = mapOf(
                "page" to page.toString(),
                "count" to DEFAULT_PAGE_SIZE.toString(),
                "user" to mAuthResponse?.userId,
                "token" to mAuthResponse?.token,
                "types" to mSelectedFileTypes.joinToString(",")
        )
        return api.getFiles(queryMap)
    }

    private fun getPagedListObservable(initialFilesResponse: FilesResponse): Observable<PagedList<SlackFile>> {
        val publishSubject = PublishSubject.create<ChunkLoadParams>()

        publishSubject
                .observeOn(Schedulers.io())
                .flatMapSingle { chunkLoadParams ->
                    val pageToLoad = chunkLoadParams.params.key
                    Log.d(LOG_TAG, "loadPortion: page=$pageToLoad (next=${chunkLoadParams.isNext})")

                    getFilesLoadObservable(pageToLoad).map { chunkLoadParams to it }
                }
                .subscribe({
                    val chunkLoadParams = it.first
                    val filesResponse = it.second
                    val pageToLoad = chunkLoadParams.params.key

                    val neighborPage: Int? = if (chunkLoadParams.isNext) {
                        if (pageToLoad >= filesResponse.paging!!.pages) null else pageToLoad + 1
                    } else {
                        if (pageToLoad <= STARTING_PAGE) null else pageToLoad - 1
                    }
                    Log.d(LOG_TAG, "callback for page $pageToLoad, next page is $neighborPage")

                    chunkLoadParams.callback.onResult(filesResponse.files ?: listOf(), neighborPage)
                }, {
                    it.printStackTrace()
                }).addToCompositeDisposable()

        val factory = object : DataSource.Factory<Int, SlackFile>() {
            override fun create(): DataSource<Int, SlackFile> = object : PageKeyedDataSource<Int, SlackFile>() {
                override fun loadInitial(params: LoadInitialParams<Int>, callback: LoadInitialCallback<Int, SlackFile>) {
                    val pagingInfo = initialFilesResponse.paging!!
                    val filesList = initialFilesResponse.files ?: listOf<SlackFile>()
                    val totalCount = pagingInfo.total
                    Log.d(LOG_TAG, "loadInitial: total=$totalCount")

                    val nextPage = if (pagingInfo.pages > STARTING_PAGE) STARTING_PAGE + 1 else null
                    callback.onResult(filesList, 0, totalCount, null, nextPage)
                }

                override fun loadAfter(params: LoadParams<Int>, callback: LoadCallback<Int, SlackFile>) {
                    publishSubject.onNext(ChunkLoadParams(params, callback, true))
                }

                override fun loadBefore(params: LoadParams<Int>, callback: LoadCallback<Int, SlackFile>) {
                    publishSubject.onNext(ChunkLoadParams(params, callback, false))
                }
            }
        }

        val pagedListConfig = PagedList.Config.Builder()
                .setPageSize(DEFAULT_PAGE_SIZE)
                .setInitialLoadSizeHint(DEFAULT_PAGE_SIZE)
                .build()

        return RxPagedListBuilder(factory, pagedListConfig).buildObservable()
    }

    /**
     * If call during configuration change data won't be propagated
     * */
    fun clearRemoveFilesLiveData() {
        mRemoveFileLiveData.value = null
    }

    fun getRemoveFilesLiveData() = mRemoveFileLiveData

    fun removeSelectedFiles(selectedFiles: List<SlackFile>) {
        var successfulCount = 0
        var failedCount = 0
        val totalCount = selectedFiles.size

        mDeleteDisposable = getDeleteFileObservable(selectedFiles)
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
                }, {
                    it.printStackTrace()
                }, {
                    pushFilesSearch(force = true)

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
                            //val response = FilesResponse().apply { this.ok = true }
                            //Thread.sleep(1000)
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
    * Cancel removal task
    * */
    fun cancelRemoval() {
        mDeleteDisposable?.dispose()
    }

    private fun Disposable.addToCompositeDisposable(): Disposable {
        mCompositeDisposable.add(this)
        return this
    }

    override fun onCleared() {
        super.onCleared()
        mCompositeDisposable.clear()
    }

    data class ChunkLoadParams(val params: PageKeyedDataSource.LoadParams<Int>,
                               val callback: PageKeyedDataSource.LoadCallback<Int, SlackFile>,
                               val isNext: Boolean)

    companion object {
        const val LOG_TAG = "FilesListViewModel"

        const val DEFAULT_PAGE_SIZE = 10
        const val STARTING_PAGE = 1
    }
}