package com.scchyodol.smarthelper.presentation.home.fragment.selfcare

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.Mood
import com.scchyodol.smarthelper.data.model.UserMood
import com.scchyodol.smarthelper.presentation.home.main.MainViewModel
import com.scchyodol.smarthelper.presentation.home.main.MoodSaveState
import kotlinx.coroutines.launch
import java.util.Calendar

fun Mood.toEmoji(): String = when (this) {
    Mood.HAPPY  -> "😊"
    Mood.NORMAL -> "😌"
    Mood.TIRED  -> "😩"
    Mood.SAD    -> "😢"
}

class SelfCareFragment : Fragment() {

    companion object {
        private const val TAG = "SelfCareFragment"
    }

    private val viewModel: MainViewModel by activityViewModels()

    private var selectedMoodCard: CardView? = null
    private var selectedMoodLabel: TextView? = null
    private lateinit var moodCardMap: Map<Mood, Pair<CardView, TextView>>

    private val currentCal = Calendar.getInstance()

    //  현재 월별 무드 맵 (캘린더 렌더링에 사용)
    private var currentMonthMoodMap: Map<Int, UserMood> = emptyMap()

    private val stampAdapter = StampCalendarAdapter { clickedDay ->
        // 날짜 클릭 시 처리 (필요시 확장)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_self_care, container, false)
        setupMoodSelection(view)
        setupCalendar(view)
        observeViewModel()
        return view
    }

    // ── ViewModel 관찰 ──────────────────────────────
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 오늘 무드 → 해당 카드 자동 선택
                launch {
                    viewModel.todayMood.collect { mood ->
                        if (mood != null) {
                            val (card, label) = moodCardMap[mood] ?: return@collect
                            selectMood(card, label)
                        }
                    }
                }

                //  월별 무드 맵 → 캘린더 갱신
                launch {
                    viewModel.monthMoodMap.collect { moodMap ->
                        currentMonthMoodMap = moodMap
                        renderCalendar()  // 무드 데이터 바뀔 때마다 캘린더 재렌더링
                        Log.d(TAG, "캘린더 무드 갱신: ${moodMap.size}건")
                    }
                }

                // 저장 상태
                launch {
                    viewModel.moodSaveState.collect { state ->
                        when (state) {
                            is MoodSaveState.Error ->
                                Toast.makeText(requireContext(), "저장 실패: ${state.message}", Toast.LENGTH_SHORT).show()
                            else -> Unit
                        }
                    }
                }
            }
        }
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

        moodCardMap = mapOf(
            Mood.HAPPY  to Pair(moodHappy,  tvHappy),
            Mood.NORMAL to Pair(moodNormal, tvNormal),
            Mood.TIRED  to Pair(moodTired,  tvTired),
            Mood.SAD    to Pair(moodSad,    tvSad)
        )

        listOf(
            moodHappy  to Mood.HAPPY,
            moodNormal to Mood.NORMAL,
            moodTired  to Mood.TIRED,
            moodSad    to Mood.SAD
        ).forEach { (card, mood) ->
            val label = moodCardMap[mood]!!.second
            card.setOnClickListener {
                selectMood(card, label)
                viewModel.saveMood(mood)
            }
        }
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
    private lateinit var tvMonth: TextView

    private fun setupCalendar(view: View) {
        val rv      = view.findViewById<RecyclerView>(R.id.rvStampCalendar)
        tvMonth     = view.findViewById(R.id.tvStampMonth)
        val btnPrev = view.findViewById<View>(R.id.btnStampPrev)
        val btnNext = view.findViewById<View>(R.id.btnStampNext)

        rv.layoutManager = GridLayoutManager(requireContext(), 7)
        rv.adapter = stampAdapter

        renderCalendar()

        btnPrev.setOnClickListener {
            currentCal.add(Calendar.MONTH, -1)
            stampAdapter.clearSelection()
            //  이전 달 무드 데이터 요청
            viewModel.loadMonthMoods(
                currentCal.get(Calendar.YEAR),
                currentCal.get(Calendar.MONTH)
            )
            renderCalendar()
        }

        btnNext.setOnClickListener {
            currentCal.add(Calendar.MONTH, 1)
            stampAdapter.clearSelection()
            //  다음 달 무드 데이터 요청
            viewModel.loadMonthMoods(
                currentCal.get(Calendar.YEAR),
                currentCal.get(Calendar.MONTH)
            )
            renderCalendar()
        }
    }

    //  캘린더 렌더링 (currentMonthMoodMap 기반)
    private fun renderCalendar() {
        tvMonth.text = "${currentCal.get(Calendar.MONTH) + 1}월"
        stampAdapter.submitList(buildCalendarItems())
    }

    private fun buildCalendarItems(): List<CalendarDay> {
        val year  = currentCal.get(Calendar.YEAR)
        val month = currentCal.get(Calendar.MONTH)

        val today      = Calendar.getInstance()
        val todayYear  = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay   = today.get(Calendar.DAY_OF_MONTH)

        val firstDay = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val maxDay   = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
        val startDow = firstDay.get(Calendar.DAY_OF_WEEK) - 1

        return buildList {
            // 빈 셀 (월 시작 전 공백)
            repeat(startDow) { add(CalendarDay(0, false, false)) }

            for (d in 1..maxDay) {
                val isToday = year == todayYear && month == todayMonth && d == todayDay
                //  DB에서 가져온 무드 맵 기반으로 이모지 결정
                val userMood  = currentMonthMoodMap[d]
                val isStamped = userMood != null
                val emoji     = userMood?.mood?.toEmoji() ?: ""

                add(CalendarDay(d, isStamped, isToday, emoji))
            }
        }
    }
}
