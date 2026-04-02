package com.scchyodol.smarthelper.presentation.home.carerecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R

data class ValueHistoryItem(
    val value: String,
    val isCurrentDefault: Boolean   // 현재 기본값 여부 (배지 표시용)
)

class ValueHistoryAdapter(
    private var selectedValue: String,
    private val onItemSelected: (String) -> Unit
) : ListAdapter<ValueHistoryItem, ValueHistoryAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ValueHistoryItem>() {
            override fun areItemsTheSame(a: ValueHistoryItem, b: ValueHistoryItem) = a.value == b.value
            override fun areContentsTheSame(a: ValueHistoryItem, b: ValueHistoryItem) = a == b
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvValue: TextView       = itemView.findViewById(R.id.tvValue)
        val tvBadge: TextView       = itemView.findViewById(R.id.tvDefaultBadge)
        val ivSelector: ImageView   = itemView.findViewById(R.id.ivSelector)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_value_history, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx  = holder.itemView.context

        holder.tvValue.text = item.value

        // ✅ 현재 기본값 배지 표시
        holder.tvBadge.visibility = if (item.isCurrentDefault) View.VISIBLE else View.GONE

        // ✅ 선택 상태에 따라 circle 토글
        val isSelected = item.value == selectedValue
        holder.ivSelector.background = ContextCompat.getDrawable(
            ctx,
            if (isSelected) R.drawable.bg_circle_filled else R.drawable.bg_circle_empty
        )

        // ✅ 아이템 전체 클릭
        holder.itemView.setOnClickListener {
            val prev = selectedValue
            selectedValue = item.value
            onItemSelected(item.value)

            // 이전 선택 & 현재 선택 아이템만 갱신
            val prevIdx = currentList.indexOfFirst { it.value == prev }
            if (prevIdx != -1) notifyItemChanged(prevIdx)
            notifyItemChanged(holder.adapterPosition)
        }
    }

    fun getSelectedValue(): String = selectedValue
}
