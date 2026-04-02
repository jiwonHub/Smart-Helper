package com.scchyodol.smarthelper.presentation.home.fragment.selfcare

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import java.util.Calendar

class SelfCareFragment : Fragment() {

    private var selectedMoodCard: CardView? = null
    private var selectedMoodLabel: TextView? = null

    private val stampAdapter = StampCalendarAdapter { clickedDay ->

    }
    private val currentCal = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_self_care, container, false)
        setupMoodSelection(view)
        setupCalendar(view)
        return view
    }

    // ── 마음 날씨 선택 ──────────────────────────────
    private fun setupMoodSelection(view: View) {
        val moodHappy  = view.findViewById<CardView>(R.id.moodHappy)
        val moodNormal = view.findViewById<CardView>(R.id.moodNormal)
        val moodTired  = view.findViewById<CardView>(R.id.moodTired)
        val moodSad    = view.findViewById<CardView>(R.id.moodSad)

        val tvHappy  = view.findViewById<TextView>(R.id.tvMoodHappy)
        val tvNormal = view.findViewById<TextView>(R.id.tvMoodNormal)
        val tvTired  = view.findViewById<TextView>(R.id.tvMoodTired)
        val tvSad    = view.findViewById<TextView>(R.id.tvMoodSad)

        listOf(
            moodHappy  to tvHappy,
            moodNormal to tvNormal,
            moodTired  to tvTired,
            moodSad    to tvSad
        ).forEach { (card, label) ->
            card.setOnClickListener { selectMood(card, label) }
        }

        selectMood(moodHappy, tvHappy)
    }

    private fun selectMood(newCard: CardView, newLabel: TextView) {
        selectedMoodCard?.cardElevation = 3f * resources.displayMetrics.density
        selectedMoodCard?.setCardBackgroundColor(0xFFFFFDF7.toInt())
        selectedMoodLabel?.setTextColor(0xFF888888.toInt())
        selectedMoodLabel?.typeface = Typeface.DEFAULT

        newCard.cardElevation = 8f * resources.displayMetrics.density
        newCard.setCardBackgroundColor(0xFFFFEDD5.toInt())
        newLabel.setTextColor(0xFFFF9800.toInt())
        newLabel.setTypeface(null, Typeface.BOLD)

        selectedMoodCard  = newCard
        selectedMoodLabel = newLabel
    }

    // ── 캘린더 ──────────────────────────────────────
    private fun setupCalendar(view: View) {
        val rv       = view.findViewById<RecyclerView>(R.id.rvStampCalendar)
        val tvMonth  = view.findViewById<TextView>(R.id.tvStampMonth)
        val btnPrev  = view.findViewById<View>(R.id.btnStampPrev)
        val btnNext  = view.findViewById<View>(R.id.btnStampNext)

        rv.layoutManager = GridLayoutManager(requireContext(), 7)
        rv.adapter = stampAdapter

        fun render() {
            tvMonth.text = "${currentCal.get(Calendar.MONTH) + 1}월"
            stampAdapter.submitList(buildCalendarItems())
        }

        render()

        btnPrev.setOnClickListener {
            currentCal.add(Calendar.MONTH, -1)
            stampAdapter.clearSelection()  // 선택 초기화
            render()
        }
        btnNext.setOnClickListener {
            currentCal.add(Calendar.MONTH, 1)
            stampAdapter.clearSelection()  // 선택 초기화
            render()
        }

    }

    private fun buildCalendarItems(): List<CalendarDay> {
        val year  = currentCal.get(Calendar.YEAR)
        val month = currentCal.get(Calendar.MONTH)

        val today = Calendar.getInstance()
        val todayYear  = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay   = today.get(Calendar.DAY_OF_MONTH)

        val firstDay = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maxDay   = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDow = firstDay.get(Calendar.DAY_OF_WEEK) - 1

        // 무드 4종 (DB 붙일 때 실제 데이터로 교체)
        val moodEmojis = listOf("😊", "😌", "😩", "😢")
        val stampedDays = (1 until todayDay)
            .filter { it % 3 != 0 }
            .associateWith { moodEmojis[it % moodEmojis.size] }  // 날짜 → 이모지 매핑

        return buildList {
            repeat(startDow) { add(CalendarDay(0, false, false)) }
            for (d in 1..maxDay) {
                val isToday   = year == todayYear && month == todayMonth && d == todayDay
                val isStamped = year == todayYear && month == todayMonth && d in stampedDays
                val emoji     = if (isStamped) stampedDays[d] ?: "" else ""
                add(CalendarDay(d, isStamped, isToday, emoji))
            }
        }
    }

}
