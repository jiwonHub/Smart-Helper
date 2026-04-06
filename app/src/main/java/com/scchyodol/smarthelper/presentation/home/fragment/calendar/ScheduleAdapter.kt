package com.scchyodol.smarthelper.presentation.home.fragment.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.ScheduleItem

class ScheduleAdapter(
    private var items: List<ScheduleItem>,
    private val onDeleteClick: (ScheduleItem, Int) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorBar   : View      = itemView.findViewById(R.id.viewColorBar)
        val ivIcon     : ImageView = itemView.findViewById(R.id.ivScheduleIcon)
        val tvTime     : TextView  = itemView.findViewById(R.id.tvScheduleTime)
        val tvText     : TextView  = itemView.findViewById(R.id.tvScheduleText)
        val tvRepeatTag: TextView  = itemView.findViewById(R.id.tvRepeatTag)
        val btnDelete    : ImageView = itemView.findViewById(R.id.btnDeleteSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val item = items[position]
        holder.tvTime.text = item.time
        holder.tvText.text = item.label

        // 완료/예정 상태
        if (item.isDone) {
            holder.colorBar.setBackgroundResource(R.drawable.bg_schedule_bar_done)
            holder.ivIcon.setImageResource(R.drawable.circle)
        } else {
            holder.colorBar.setBackgroundResource(R.drawable.bg_schedule_bar_pending)
            holder.ivIcon.setImageResource(R.drawable.clock)
        }

        // 반복 태그 표시/숨김
        holder.tvRepeatTag.visibility = if (item.isRepeat) View.VISIBLE else View.GONE


        holder.btnDelete.setOnClickListener {
            onDeleteClick(item, holder.adapterPosition)
        }
    }

    fun updateItems(newItems: List<ScheduleItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}

