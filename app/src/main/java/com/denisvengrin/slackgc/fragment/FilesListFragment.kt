package com.denisvengrin.slackgc.fragment

import android.app.ProgressDialog
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.*
import com.denisvengrin.slackgc.R
import com.denisvengrin.slackgc.SlackGCApp
import com.denisvengrin.slackgc.adapter.FilesListAdapter
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse
import com.denisvengrin.slackgc.data.SlackFile
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_files_list.*

class FilesListFragment : BaseFragment() {

    private var mAdapter: FilesListAdapter? = null
    private var mAuthResponse: AuthResponse? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_files_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        rvFiles.layoutManager = LinearLayoutManager(activity)
        btnRemove.setOnClickListener { removeSelectedFiles() }

        fetchFiles()
    }

    private fun fetchFiles() {
        setProgressLoading(true)

        SlackGCApp[activity!!].appComponent.storage()
                .getAuthResponse()
                .flatMap {
                    mAuthResponse = it

                    val queryMap = mapOf(
                            "user" to it.userId,
                            "token" to it.token
                    )

                    SlackGCApp[activity!!].appComponent.api().getFiles(queryMap)
                }
                .subscribeOn(Schedulers.io())
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

    private fun getProgressDialog(maxProgress: Int) = ProgressDialog(activity).apply {
        max = maxProgress
        isIndeterminate = false
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setTitle(R.string.please_wait)
        setMessage("")
    }

    private fun removeSelectedFiles() {
        val selectedFiles = mAdapter?.selectedItems ?: return

        val progressDialog = getProgressDialog(selectedFiles.size)
        progressDialog.show()

        var successfulCount = 0
        var failedCount = 0

        getDeleteFileObservable(selectedFiles)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    Log.d(LOG_TAG, "onNext: ${Thread.currentThread().name}")
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
                        Log.d(LOG_TAG, "delete: ${file.id} ${Thread.currentThread().name}")

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
                .setTitle("Removal completed")
                .setMessage("\nRemoved: $successfulCount\nFailed: $failedCount")
                .setPositiveButton(android.R.string.ok, { _, _ -> })
                .show()
    }

    private fun initAdapter(response: FilesResponse) {
        mAdapter = FilesListAdapter(activity!!, mAuthResponse?.token).apply {
            data = response.files?.toMutableList()
            selectionChangedUnit = ::checkRemoveBtnVisibility
        }
        rvFiles.adapter = mAdapter
    }

    private fun checkRemoveBtnVisibility() {
        val hasSelectedItems = mAdapter?.selectedItems?.isNotEmpty() == true
        btnRemove.visibility = if (hasSelectedItems) View.VISIBLE else View.GONE
    }

    private fun setProgressLoading(load: Boolean) {
        if (load) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.menu_files_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.item_logout -> logout()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        val context = activity ?: return

        SlackGCApp[context].appComponent.storage().removeAuthResponse()

        openLoginFragment()
    }

    private fun openLoginFragment() {
        fragmentManager
                ?.beginTransaction()
                ?.replace(R.id.container, LoginFragment.newInstance())
                ?.commit()
    }

    companion object {

        const val LOG_TAG = "FilesListFragment"
        fun newInstance(): FilesListFragment {
            return FilesListFragment()
        }
    }
}