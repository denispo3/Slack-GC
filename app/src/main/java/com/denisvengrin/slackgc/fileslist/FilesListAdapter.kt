package com.denisvengrin.slackgc.fileslist


import android.arch.paging.PagedListAdapter
import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.denisvengrin.slackgc.R
import com.denisvengrin.slackgc.data.SlackFile
import kotlinx.android.synthetic.main.fragment_files_list_item.view.*
import java.text.SimpleDateFormat
import java.util.*

class FilesListAdapter( diffCallback: DiffUtil.ItemCallback<SlackFile>, val context: Context, val token: String?)
    : RecyclerView.Adapter<FilesListAdapter.ViewHolder>() {

    var selectionChangedUnit: (() -> Unit)? = null
    var data: MutableList<SlackFile>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }
    val selectedItems = mutableListOf<SlackFile>()

    private val mDateFormat = SimpleDateFormat("dd MMMM ''yy HH:mm", Locale.getDefault())
    private val mGlideHeaders = LazyHeaders.Builder()
            .addHeader("Authorization", "Bearer $token")
            .build()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_files_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = data?.get(position) ?: return

        holder.tvTitle.text = item.title
        holder.tvContent.text = mDateFormat.format(Date(item.created * 1000))

        holder.llRootContainer.setOnClickListener {
            toggleItem(item)
            notifyItemChanged(holder.adapterPosition)

            selectionChangedUnit?.invoke()
        }

        val backgroundRes = if (selectedItems.contains(item)) {
            R.drawable.background_with_round_corners_filled
        } else {
            R.drawable.background_with_round_corners
        }
        val background = ContextCompat.getDrawable(context, backgroundRes)

        holder.llRootContainer.background = background

        if (item.thumb.isNullOrEmpty()) {
            holder.ivPicture.visibility = View.GONE
        } else {
            holder.ivPicture.visibility = View.VISIBLE
            val url = GlideUrl(item.thumb, mGlideHeaders)
            Glide.with(context).load(url)
                    .transition(DrawableTransitionOptions().crossFade())
                    .into(holder.ivPicture)
        }
    }

    fun notifyRemoval(item: SlackFile) {
        val index = data?.indexOf(item) ?: -1
        if (index >= 0) {
            selectedItems.remove(item)
            data?.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    private fun toggleItem(item: SlackFile) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
        } else {
            selectedItems.add(item)
        }
    }

    override fun getItemCount(): Int = data?.size ?: 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle = view.tvTitle
        val tvContent = view.tvContent
        val llRootContainer = view.llRootContainer
        val ivPicture = view.ivPicture
    }
}
