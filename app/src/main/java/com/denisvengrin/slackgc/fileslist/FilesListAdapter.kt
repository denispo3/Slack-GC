package com.denisvengrin.slackgc.fileslist


import android.arch.paging.PagedList
import android.arch.paging.PagedListAdapter
import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.denisvengrin.slackgc.R
import com.denisvengrin.slackgc.data.SlackFile
import kotlinx.android.synthetic.main.fragment_files_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*

class FilesListAdapter(diffCallback: DiffUtil.ItemCallback<SlackFile>,
                       val context: Context,
                       var selectedFiles: MutableList<SlackFile>)
    : PagedListAdapter<SlackFile, FilesListAdapter.ViewHolder>(diffCallback) {

    var token: String? = null
    var selectionChangedUnit: (() -> Unit)? = null
    var currentListChangedUnit: (() -> Unit)? = null

    private val mDateFormat = SimpleDateFormat("dd MMMM ''yy HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_files_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onCurrentListChanged(currentList: PagedList<SlackFile>?) {
        super.onCurrentListChanged(currentList)
        currentListChangedUnit?.invoke()
        updateSelectedFiles()
    }

    private fun updateSelectedFiles() {
        selectedFiles = currentList?.intersect(selectedFiles)?.toMutableList() ?: mutableListOf()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        if (item == null) {
            holder.tvTitle.text = context.getString(R.string.loading)
            holder.tvContent.text = null
            holder.llRootContainer.setOnClickListener(null)
            holder.ivPicture.visibility = View.INVISIBLE
        } else {
            holder.tvTitle.text = item.title
            holder.tvContent.text = mDateFormat.format(Date(item.created * 1000))

            holder.llRootContainer.setOnClickListener {
                toggleItem(item)
                notifyItemChanged(holder.adapterPosition)

                selectionChangedUnit?.invoke()
            }

            val iconImageUrl = item.thumb
            if (iconImageUrl.isNullOrEmpty()) {
                holder.ivPicture.visibility = View.GONE
            } else {
                holder.ivPicture.visibility = View.VISIBLE
                val url = GlideUrl(iconImageUrl, {
                    mapOf("Authorization" to "Bearer $token")
                })
                Glide.with(context).load(url)
                        .apply(RequestOptions().error(R.drawable.ic_image))
                        .transition(DrawableTransitionOptions().crossFade())
                        .into(holder.ivPicture)
            }
        }

        val backgroundRes = if (selectedFiles.contains(item)) {
            R.drawable.background_with_round_corners_filled
        } else {
            R.drawable.background_with_round_corners
        }
        val background = ContextCompat.getDrawable(context, backgroundRes)

        holder.llRootContainer.background = background
    }

    fun notifyRemoval(item: SlackFile) {
        val index = currentList?.indexOf(item) ?: -1
        if (index >= 0) {
            selectedFiles.remove(item)
        }
    }

    private fun toggleItem(item: SlackFile) {
        if (selectedFiles.contains(item)) {
            selectedFiles.remove(item)
        } else {
            selectedFiles.add(item)
        }
    }

    fun selectAllFiles() {
        if (selectedFiles.size == itemCount) {
            selectedFiles.clear()
        } else {
            selectedFiles = currentList?.toMutableList() ?: mutableListOf()
        }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle = view.tvTitle
        val tvContent = view.tvContent
        val llRootContainer = view.llRootContainer
        val ivPicture = view.ivPicture
    }
}
