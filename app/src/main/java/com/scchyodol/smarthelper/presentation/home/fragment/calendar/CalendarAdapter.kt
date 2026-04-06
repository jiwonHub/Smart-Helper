package com.scchyodol.smarthelper.presentation.home.fragment.calendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.CalendarDay

class CalendarAdapter(
    private var days: List<CalendarDay>,
    private val onDayClick: (CalendarDay) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView     = itemView.findViewById(R.id.tvDay)       //  XML ID 맞춤
        val ivIcon1: ImageView  = itemView.findViewById(R.id.ivIcon1)
        val ivIcon2: ImageView  = itemView.findViewById(R.id.ivIcon2)
        val ivIcon3: ImageView  = itemView.findViewById(R.id.ivIcon3)
        val ivIcon4: ImageView  = itemView.findViewById(R.id.ivIcon4)
        val ivIcon5: ImageView  = itemView.findViewById(R.id.ivIcon5)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = days[position]

        // ── 빈 칸 처리 ──────────────────────────────────────────
        if (day.dayNumber == 0) {
            holder.tvDay.text = ""
            holder.tvDay.setTextColor(Color.TRANSPARENT)
            holder.tvDay.background = null
            hideAllIcons(holder)
            holder.itemView.isClickable = false
            holder.itemView.setOnClickListener(null)
            return
        }

        // ── 날짜 텍스트 ──────────────────────────────────────────
        holder.tvDay.text = day.dayNumber.toString()

        // ── 날짜 텍스트 색상 (우선순위: 오늘/선택 > 토/일 > 평일) ──
        when {
            day.isToday || day.isSelected -> {
                holder.tvDay.setTextColor(Color.WHITE)
            }
            day.dayOfWeek == 0 -> {
                holder.tvDay.setTextColor(Color.parseColor("#FF5252"))  // 일요일 빨간색
            }
            day.dayOfWeek == 6 -> {
                holder.tvDay.setTextColor(Color.parseColor("#448AFF"))  // 토요일 파란색
            }
            else -> {
                holder.tvDay.setTextColor(Color.parseColor("#2D2D3A"))  // 평일 기본색
            }
        }

        // ── 오늘 / 선택 배경 원 ─────────────────────────────────
        when {
            day.isToday -> {
                holder.tvDay.setBackgroundResource(R.drawable.bg_circle_blue)
                holder.tvDay.setTextColor(Color.WHITE)
            }
            day.isSelected -> {
                holder.tvDay.setBackgroundResource(R.drawable.bg_circle_gray)
                holder.tvDay.setTextColor(Color.BLACK)
            }
            else -> holder.tvDay.background = null
        }

        // ── 아이콘 표시 ─────────────────────────────────────────
        bindIcons(holder, day)

        // ── 클릭 ────────────────────────────────────────────────
        holder.itemView.isClickable = true
        holder.itemView.setOnClickListener { onDayClick(day) }
    }

    // ── 아이콘 5칸에 순서대로 채우기 ────────────────────────────
    private fun bindIcons(holder: CalendarViewHolder, day: CalendarDay) {
        val iconSlots = listOf(
            holder.ivIcon1,
            holder.ivIcon2,
            holder.ivIcon3,
            holder.ivIcon4,
            holder.ivIcon5
        )

        val iconTypes = mutableListOf<Boolean>() // true=완료(초록), false=예정(주황)
        if (day.hasCheck) iconTypes.add(true)
        if (day.hasClock) iconTypes.add(false)

        iconSlots.forEachIndexed { index, imageView ->
            if (index < iconTypes.size) {
                imageView.visibility = View.VISIBLE
                imageView.setImageResource(
                    if (iconTypes[index]) R.drawable.dot_green else R.drawable.dot_orange
                )
            } else {
                imageView.visibility = View.GONE
            }
        }
    }

    private fun hideAllIcons(holder: CalendarViewHolder) {
        holder.ivIcon1.visibility = View.GONE
        holder.ivIcon2.visibility = View.GONE
        holder.ivIcon3.visibility = View.GONE
        holder.ivIcon4.visibility = View.GONE
        holder.ivIcon5.visibility = View.GONE
    }

    fun updateDays(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }
}
