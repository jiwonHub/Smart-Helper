package com.scchyodol.smarthelper.presentation.home.fragment.selfcare

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R

data class CalendarDay(
    val day: Int,
    val isStamped: Boolean,
    val isToday: Boolean,
    val moodEmoji: String = ""
)

class StampCalendarAdapter(
    private val onDayClick: (CalendarDay) -> Unit = {}
) : ListAdapter<CalendarDay, StampCalendarAdapter.DayViewHolder>(DiffCallback) {

    // 선택된 포지션 추적
    private var selectedPosition: Int = -1

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cellRoot: ConstraintLayout = itemView.findViewById(R.id.cellRoot)
        val tvDay: TextView            = itemView.findViewById(R.id.tvStampDay)
        val tvMood: TextView           = itemView.findViewById(R.id.tvStampMood)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stamp_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val item = getItem(position)

        // ── 빈 칸 ───────────────────────────────────────────
        if (item.day == 0) {
            holder.tvDay.text = ""
            holder.tvDay.background = null
            holder.tvMood.visibility = View.INVISIBLE
            holder.cellRoot.setBackgroundResource(0) // 빈 칸은 배경 없음
            holder.cellRoot.isClickable = false
            return
        }

        holder.cellRoot.isClickable = true

        // ── 선택 여부에 따라 셀 배경 ────────────────────────
        if (position == selectedPosition) {
            holder.cellRoot.setBackgroundResource(R.drawable.bg_stamp_cell_selected)
        } else {
            holder.cellRoot.setBackgroundResource(R.drawable.bg_stamp_cell)
        }

        // ── 날짜 텍스트 ─────────────────────────────────────
        holder.tvDay.text = item.day.toString()

        // 오늘만 원 표시, 나머지는 배경 없음
        if (item.isToday) {
            holder.tvDay.setBackgroundResource(R.drawable.bg_stamp_day_today)
            holder.tvDay.setTextColor(0xFFFFFFFF.toInt())
            holder.tvDay.setTypeface(null, Typeface.BOLD)
        } else {
            holder.tvDay.background = null
            holder.tvDay.setTextColor(0xFF3D2C1E.toInt())
            holder.tvDay.typeface = Typeface.DEFAULT
        }

        // ── 이모지 ──────────────────────────────────────────
        if (item.isStamped && item.moodEmoji.isNotEmpty()) {
            holder.tvMood.text = item.moodEmoji
            holder.tvMood.visibility = View.VISIBLE
        } else {
            holder.tvMood.text = ""
            holder.tvMood.visibility = View.INVISIBLE
        }

        // ── 클릭 리스너 ─────────────────────────────────────
        holder.cellRoot.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            // 이전 선택 해제, 새 선택 갱신
            if (prev != -1) notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onDayClick(item)
        }
    }

    fun clearSelection() {
        val prev = selectedPosition
        selectedPosition = -1
        if (prev != -1) notifyItemChanged(prev)
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CalendarDay>() {
            override fun areItemsTheSame(old: CalendarDay, new: CalendarDay): Boolean =
                old.day == new.day && old.isToday == new.isToday

            override fun areContentsTheSame(old: CalendarDay, new: CalendarDay): Boolean =
                old == new
        }
    }
}
