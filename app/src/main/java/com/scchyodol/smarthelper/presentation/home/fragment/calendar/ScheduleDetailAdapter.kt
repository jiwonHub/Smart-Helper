package com.scchyodol.smarthelper.presentation.home.fragment.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.ScheduleItem

class ScheduleDetailAdapter(
    private var items: List<ScheduleItem>,
    private val onEditClick: (ScheduleItem, Int) -> Unit,
    private val onDeleteClick: (ScheduleItem, Int) -> Unit
) : RecyclerView.Adapter<ScheduleDetailAdapter.DetailViewHolder>() {

    inner class DetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorBar   : View     = itemView.findViewById(R.id.viewDetailColorBar)
        val tvTime     : TextView = itemView.findViewById(R.id.tvDetailTime)
        val tvCategory : TextView = itemView.findViewById(R.id.tvDetailCategory)
        val tvRepeatTag: TextView = itemView.findViewById(R.id.tvDetailRepeatTag)
        val tvDoneTag  : TextView = itemView.findViewById(R.id.tvDetailDoneTag)
        val tvLabel    : TextView = itemView.findViewById(R.id.tvDetailLabel)
        val tvValue    : TextView = itemView.findViewById(R.id.tvDetailValue)
        val tvMemo     : TextView = itemView.findViewById(R.id.tvDetailMemo)
        val btnEdit   : ImageView = itemView.findViewById(R.id.btnEditDetail)
        val btnDelete  : ImageView = itemView.findViewById(R.id.btnDeleteDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_detail, parent, false)
        return DetailViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        val item = items[position]

        // 시간
        holder.tvTime.text = item.time

        // 카테고리
        holder.tvCategory.text = item.category

        // 이름
        holder.tvLabel.text = item.label

        // 수치
        if (!item.value.isNullOrBlank()) {
            holder.tvValue.text = "수치: ${item.value}"
            holder.tvValue.visibility = View.VISIBLE
        } else {
            holder.tvValue.visibility = View.GONE
        }

        // 메모
        if (!item.memo.isNullOrBlank()) {
            holder.tvMemo.text = "📝 ${item.memo}"
            holder.tvMemo.visibility = View.VISIBLE
        } else {
            holder.tvMemo.visibility = View.GONE
        }

        // 반복 태그
        holder.tvRepeatTag.visibility = if (item.isRepeat) View.VISIBLE else View.GONE

        // 수행 태그 (완료/예정)
        if (item.isDone) {
            holder.tvDoneTag.text = "완료"
            holder.tvDoneTag.setTextColor(android.graphics.Color.parseColor("#27AE60"))
            holder.tvDoneTag.setBackgroundResource(R.drawable.bg_done_tag)
            holder.colorBar.setBackgroundResource(R.drawable.bg_schedule_bar_done)
        } else {
            holder.tvDoneTag.text = "예정"
            holder.tvDoneTag.setTextColor(android.graphics.Color.parseColor("#F39C12"))
            holder.tvDoneTag.setBackgroundResource(R.drawable.bg_pending_tag)
            holder.colorBar.setBackgroundResource(R.drawable.bg_schedule_bar_pending)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(item, holder.adapterPosition)
        }

        // 삭제 버튼
        holder.btnDelete.setOnClickListener {
            onDeleteClick(item, holder.adapterPosition)
        }
    }

    fun updateItems(newItems: List<ScheduleItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
