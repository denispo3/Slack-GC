package com.denisvengrin.slackgc.fileslist

import android.app.ProgressDialog
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.arch.paging.DataSource
import android.arch.paging.PagedList
import android.arch.paging.PositionalDataSource
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
import com.denisvengrin.slackgc.common.ViewModelResult
import com.denisvengrin.slackgc.common.ViewModelStatus
import com.denisvengrin.slackgc.data.AuthResponse
import com.denisvengrin.slackgc.data.FilesResponse
import com.denisvengrin.slackgc.data.SlackFile
import com.denisvengrin.slackgc.fragment.BaseFragment
import com.denisvengrin.slackgc.fragment.LoginFragment
import com.denisvengrin.slackgc.util.startVisibilityAnimation
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_files_list.*

class FilesListFragment : BaseFragment() {

    private var progressDialog: ProgressDialog? = null

    private var mAdapter: FilesListAdapter? = null

    private lateinit var mViewModel: FilesListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initAdapter(savedInstanceState)

        val appComponent = SlackGCApp[activity!!].appComponent

        mViewModel = ViewModelProviders.of(this,
                FileListViewModelFactory(appComponent.api(), appComponent.storage()))
                .get(FilesListViewModel::class.java)

        mViewModel.getFilesResponseLiveData().observe(this, Observer {
            processFilesListResult(it)
        })

        mViewModel.getRemoveFilesLiveData().observe(this, Observer {
            processRemovalResult(it)
        })
    }

    private fun processFilesListResult(it: ViewModelResult<FilesListResult>?) {
        it ?: return

        setProgressLoading(it.status == ViewModelStatus.PROGRESS)

        if (it.status == ViewModelStatus.SUCCESS) {
            setAdapterData(it.result!!.authResponse!!, it.result.filesResponse)
        }
    }

    private fun processRemovalResult(it: ViewModelResult<RemovalResult>?) {
        val removalResult = it?.result ?: return

        val slackFile = removalResult.slackFile
        val filesResponse = removalResult.filesResponse

        when (it.status) {
            ViewModelStatus.PROGRESS -> {
                if (slackFile != null && !slackFile.title.isNullOrEmpty()) {
                    if (progressDialog == null || !progressDialog!!.isShowing) {
                        progressDialog = createProgressDialog(removalResult.totalCount)
                    }
                    progressDialog?.setMessage("Removing \"${slackFile.title}\"")
                    if (filesResponse != null) {
                        if (filesResponse.ok) {
                            mAdapter?.notifyRemoval(slackFile)
                        }

                        progressDialog?.progress = removalResult.successfulCount + removalResult.failedCount
                    }
                }
            }
            ViewModelStatus.SUCCESS -> {
                checkRemoveBtnVisibility()
                checkForEmptyData()
                progressDialog?.dismiss()
                mViewModel.clearRemoveFilesLiveData()
                showResultsDialog(removalResult.successfulCount, removalResult.failedCount)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_files_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        rvFiles.layoutManager = LinearLayoutManager(activity)
        fabRemove.setOnClickListener { removeSelectedFiles() }
        initTypeCheckBoxes()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater?.inflate(R.menu.menu_files_list, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.item_logout -> logout()
            R.id.item_filter -> toggleFilter()
            R.id.item_select_all -> selectAllFiles()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val selectedFiles = mAdapter?.selectedFiles
        if (selectedFiles != null) {
            outState.putParcelableArrayList(ARG_SELECTED_FILES, ArrayList(selectedFiles))
        }
    }

    override fun onDestroyView() {
        progressDialog?.dismiss()
        super.onDestroyView()
    }

    private fun selectAllFiles() {
        mAdapter?.selectAllFiles()
        checkRemoveBtnVisibility()
    }

    private fun removeSelectedFiles() {
        val selectedFiles = mAdapter?.selectedFiles ?: return

        mViewModel.removeSelectedFiles(selectedFiles)
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
                mViewModel.addFilter(selectedType)
            } else {
                mViewModel.removeFilter(selectedType)
            }
        }

        for (i in 0 until llCheckboxes.childCount) {
            val child = llCheckboxes.getChildAt(i)
            if (child is CheckBox) {
                child.setOnCheckedChangeListener(listener)
            }
        }
    }

    private fun createProgressDialog(maxProgress: Int) = ProgressDialog(activity).apply {
        isIndeterminate = false
        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        setTitle(R.string.please_wait)
        setMessage("")
        setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), { _, _ ->
            mViewModel.clearTasks()
        })
        max = maxProgress
        setCancelable(false)
        show()
    }

    private fun showResultsDialog(successfulCount: Int, failedCount: Int) {
        AlertDialog.Builder(activity!!)
                .setTitle(R.string.removal_completed)
                .setMessage("\nRemoved: $successfulCount\nFailed: $failedCount")
                .setPositiveButton(android.R.string.ok, { _, _ -> })
                .show()
    }

    private fun initAdapter(savedInstanceState: Bundle?) {
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

        val mDiffUtilCallback = object : DiffUtil.ItemCallback<SlackFile>() {
            override fun areItemsTheSame(oldItem: SlackFile?, newItem: SlackFile?): Boolean {
                return oldItem?.id == newItem?.id
            }

            override fun areContentsTheSame(oldItem: SlackFile?, newItem: SlackFile?): Boolean {
                return oldItem == newItem
            }
        }

        val selectedFiles = savedInstanceState?.getParcelableArrayList<SlackFile>(ARG_SELECTED_FILES)?.toMutableList()
                ?: mutableListOf()
        mAdapter = FilesListAdapter(mDiffUtilCallback, activity!!, selectedFiles).apply {
            selectionChangedUnit = ::checkRemoveBtnVisibility
        }

        /*RxPagedListBuilder(factory, pagedListConfig).buildObservable()
                .subscribe {
                    mAdapter?.submitList(it)
                }
                .addToCompositeDisposable()*/
    }

    private fun setAdapterData(authResponse: AuthResponse, filesResponse: FilesResponse?) {
        val filesList = filesResponse?.files

        mAdapter?.token = authResponse.token
        mAdapter?.data = filesList?.toMutableList()

        checkForEmptyData()
        checkRemoveBtnVisibility()

        rvFiles.adapter = mAdapter
    }

    private fun checkForEmptyData() {
        val filesList = mAdapter?.data
        if (filesList == null || filesList.isEmpty()) {
            tvNoData.visibility = View.VISIBLE
        } else {
            tvNoData.visibility = View.GONE
        }
    }

    private fun checkRemoveBtnVisibility() {
        val hasSelectedItems = mAdapter?.selectedFiles?.isNotEmpty() == true
        fabRemove.startVisibilityAnimation(if (hasSelectedItems) View.VISIBLE else View.GONE)
    }

    private fun setProgressLoading(load: Boolean) {
        progressBar.startVisibilityAnimation(if (load) View.VISIBLE else View.GONE)
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

        const val ARG_SELECTED_FILES = "arg_selected_files"

        const val LOG_TAG = "FilesListFragment"

        fun newInstance(): FilesListFragment {
            return FilesListFragment()
        }
    }
}