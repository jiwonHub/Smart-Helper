package com.scchyodol.smarthelper.presentation.home.fragment.lovemessage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R

data class LoveMessageItem(
    val title: String,
    val duration: String
)

class LoveMessageAdapter(
    private val items: MutableList<LoveMessageItem>
) : RecyclerView.Adapter<LoveMessageAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView    = itemView.findViewById(R.id.tvMessageTitle)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val btnPlay: View        = itemView.findViewById(R.id.btnPlay)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_love_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text    = item.title
        holder.tvDuration.text = item.duration

        // 재생 버튼 (기능은 나중에)
        holder.btnPlay.setOnClickListener { /* TODO */ }

        // 삭제 버튼 (기능은 나중에)
        holder.btnDelete.setOnClickListener { /* TODO */ }
    }

    override fun getItemCount() = items.size
}
