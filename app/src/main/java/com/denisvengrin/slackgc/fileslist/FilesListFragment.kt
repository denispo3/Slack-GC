package com.denisvengrin.slackgc.fileslist

import android.app.ProgressDialog
import android.arch.paging.DataSource
import android.arch.paging.PagedList
import android.arch.paging.PositionalDataSource
import android.arch.paging.RxPagedListBuilder
import android.content.DialogInterface
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.support.v7.app.AlertDialog
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.CheckBox
import android.widget.CompoundButton
import com.denisvengrin.slackgc.R
import com.denisvengrin.slackgc.SlackGCApp
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse
import com.denisvengrin.slackgc.data.SlackFile
import com.denisvengrin.slackgc.fragment.BaseFragment
import com.denisvengrin.slackgc.fragment.LoginFragment
import com.denisvengrin.slackgc.util.startVisibilityAnimation
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.fragment_files_list.*
import java.util.concurrent.TimeUnit

class FilesListFragment : BaseFragment() {

    private var mAdapter: FilesListAdapter? = null
    private var mAuthResponse: AuthResponse? = null

    private val mSelectedFileTypes = mutableSetOf<String>()
    private val mFilesSearchSubject = PublishSubject.create<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_files_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        rvFiles.layoutManager = LinearLayoutManager(activity)
        fabRemove.setOnClickListener { removeSelectedFiles() }

        initTypeCheckBoxes()
        initFilesSearchSubject()
        mFilesSearchSubject.onNext(0)
    }

    private fun initFilesSearchSubject() {
        mFilesSearchSubject
                .debounce(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext {
                    setProgressLoading(true)
                }
                .observeOn(Schedulers.io())
                .flatMapSingle {
                    getFetchFilesObservable()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    setProgressLoading(false)
                    initAdapter(it)
                }, {
                    it.printStackTrace()
                    setProgressLoading(false)
                })
                .addToCompositeDisposable()
    }

    private fun initTypeCheckBoxes() {
        val listener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            val selectedType = when (buttonView.id) {
                R.id.chbSpaces -> FILE_TYPE_SPACES
                R.id.chbSnippets -> FILE_TYPE_SNIPPETS
                R.id.chbImages -> FILE_TYPE_IMAGES
                R.id.chbGdocs -> FILE_TYPE_GDOCS
                R.id.chbZips -> FILE_TYPE_ZIPS
                R.id.chbPdfs -> FILE_TYPE_PDFS
                else -> null
            } ?: return@OnCheckedChangeListener

            if (isChecked) {
                mSelectedFileTypes.add(selectedType)
            } else {
                mSelectedFileTypes.remove(selectedType)
            }

            mFilesSearchSubject.onNext(0)
        }

        for (i in 0 until llCheckboxes.childCount) {
            val child = llCheckboxes.getChildAt(i)
            if (child is CheckBox) {
                child.setOnCheckedChangeListener(listener)
            }
        }
    }

    private fun getFetchFilesObservable(): Single<FilesResponse> {
        val appComponent = SlackGCApp[activity!!].appComponent
        return appComponent.storage()
                .getAuthResponse()
                .flatMap {
                    mAuthResponse = it

                    val queryMap = mapOf(
                            "page" to "1",
                            "count" to "20",
                            "user" to it.userId,
                            "token" to it.token,
                            "types" to mSelectedFileTypes.joinToString(",")
                    )

                    appComponent.api().getFiles(queryMap)
                }
    }

    private fun getProgressDialog(maxProgress: Int) = ProgressDialog(activity).apply {
        max = maxProgress
        isIndeterminate = false
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setTitle(R.string.please_wait)
        setMessage("")
        setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), { _, _ ->
        })
    }

    private fun removeSelectedFiles() {
        val selectedFiles = mAdapter?.selectedItems ?: return

        val progressDialog = getProgressDialog(selectedFiles.size)

        progressDialog.show()

        var successfulCount = 0
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

        progressDialog.setOnDismissListener { disposable.dispose() }
    }

    /**
     * @return an Observable, that emits [Pair] with:
     * 1) [SlackFile] and empty [FilesResponse]  on first step to init progress
     * 2) [SlackFile] and [FilesResponse] received from server
     * 3) Empty [SlackFile] and [FilesResponse] with *success=false* in case of failed request
     */
    private fun getDeleteFileObservable(selectedFiles: List<SlackFile>) =
            Observable.create<Pair<SlackFile, FilesResponse?>>({ emitter ->
                val api = SlackGCApp[activity!!].appComponent.api()

                val filesToRemove = ArrayList(selectedFiles)
                for (file in filesToRemove) {
                    if (!emitter.isDisposed) {
                        emitter.onNext(file to null)
                        //Log.d(LOG_TAG, "delete: ${file.id} ${Thread.currentThread().name}")

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

    private fun showResultsDialog(successfulCount: Int, failedCount: Int) {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.removal_completed)
                .setMessage("\nRemoved: $successfulCount\nFailed: $failedCount")
                .setPositiveButton(android.R.string.ok, { _, _ -> })
                .show()
    }

    private val mDiffUtilCallback = object : DiffUtil.ItemCallback<SlackFile>() {
        override fun areItemsTheSame(oldItem: SlackFile?, newItem: SlackFile?): Boolean {
            return oldItem?.id == newItem?.id
        }

        override fun areContentsTheSame(oldItem: SlackFile?, newItem: SlackFile?): Boolean {
            return oldItem == newItem
        }
    }

    private fun initAdapter(response: FilesResponse) {
        val filesList = response.files
        if (filesList == null || filesList.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
        } else {
            tvNoData.visibility = View.GONE
        }

        val pagedListConfig = PagedList.Config.Builder()
                .setPageSize(10)
                .setInitialLoadSizeHint(10)
                .build()

        val factory = object : DataSource.Factory<Int, SlackFile>() {
            override fun create(): DataSource<Int, SlackFile> {
                return object : PositionalDataSource<SlackFile>() {
                    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<SlackFile>) {
                    }

                    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<SlackFile>) {
                    }
                }
            }
        }

        mAdapter = FilesListAdapter(mDiffUtilCallback, activity!!, mAuthResponse?.token).apply {
            data = filesList?.toMutableList()
            selectionChangedUnit = ::checkRemoveBtnVisibility
        }

        /*RxPagedListBuilder(factory, pagedListConfig).buildObservable()
                .subscribe {
                    mAdapter?.submitList(it)
                }
                .addToCompositeDisposable()*/

        rvFiles.adapter = mAdapter
    }

    private fun checkRemoveBtnVisibility() {
        val hasSelectedItems = mAdapter?.selectedItems?.isNotEmpty() == true
        fabRemove.startVisibilityAnimation(if (hasSelectedItems) View.VISIBLE else View.GONE)
    }

    private fun setProgressLoading(load: Boolean) {
        progressBar.startVisibilityAnimation(if (load) View.VISIBLE else View.GONE)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.menu_files_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.item_logout -> logout()
            R.id.item_filter -> toggleFilter()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleFilter() {
        val behavior = (appBarLayout.layoutParams as CoordinatorLayout.LayoutParams).behavior as AppBarLayout.Behavior
        val expanded = behavior.topAndBottomOffset == 0
        appBarLayout.setExpanded(!expanded)
    }

    private fun logout() {
        val context = activity ?: return

        SlackGCApp[context].appComponent.storage().removeAuthResponse()
                .subscribeOn(Schedulers.io())
                .subscribe { openLoginFragment() }
    }

    private fun openLoginFragment() {
        fragmentManager
                ?.beginTransaction()
                ?.replace(R.id.container, LoginFragment.newInstance())
                ?.commit()
    }

    companion object {

        const val FILE_TYPE_SPACES = "spaces"
        const val FILE_TYPE_SNIPPETS = "snippets"
        const val FILE_TYPE_IMAGES = "images"
        const val FILE_TYPE_GDOCS = "gdocs"
        const val FILE_TYPE_ZIPS = "zips"
        const val FILE_TYPE_PDFS = "pdfs"

        const val LOG_TAG = "FilesListFragment"

        fun newInstance(): FilesListFragment {
            return FilesListFragment()
        }
    }
}