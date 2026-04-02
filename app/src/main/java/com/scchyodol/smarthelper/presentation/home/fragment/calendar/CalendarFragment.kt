package com.scchyodol.smarthelper.presentation.home.fragment.calendar

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.db.AppDatabase
import com.scchyodol.smarthelper.data.model.CalendarDay
import com.scchyodol.smarthelper.data.model.ScheduleItem
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class CalendarFragment : Fragment() {

    companion object {
        private const val TAG = "CalendarFragment"
    }

    private lateinit var rvCalendar: RecyclerView
    private lateinit var rvSchedule: RecyclerView
    private lateinit var tvMonthYear: TextView
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvEmptySchedule: TextView
    private lateinit var btnPrevMonth: ImageView
    private lateinit var btnNextMonth: ImageView

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var scheduleAdapter: ScheduleAdapter

    private val calendar = Calendar.getInstance()
    private var selectedDay: Int = -1

    // 현재 로드된 scheduleMap (ViewModel → Fragment 캐시)
    private var currentScheduleMap: Map<Int, List<ScheduleItem>> = emptyMap()

    private val viewModel: CalendarViewModel by viewModels {
        val dao        = AppDatabase.getInstance(requireContext()).careRecordDao()
        val repository = CareRecordRepository(dao)
        CalendarViewModelFactory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)
        bindViews(view)
        setupCalendarRecyclerView()
        setupScheduleRecyclerView()
        setupMonthNavigation()
        observeViewModel()
        loadCurrentMonth()
        return view
    }

    // ── View 바인딩 ──────────────────────────────────────────────
    private fun bindViews(view: View) {
        rvCalendar      = view.findViewById(R.id.rvCalendar)
        rvSchedule      = view.findViewById(R.id.rvSchedule)
        tvMonthYear     = view.findViewById(R.id.tvMonthYear)
        tvSelectedDate  = view.findViewById(R.id.tvSelectedDate)
        tvEmptySchedule = view.findViewById(R.id.tvEmptySchedule)
        btnPrevMonth    = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth    = view.findViewById(R.id.btnNextMonth)

        btnPrevMonth.setColorFilter(
            android.graphics.Color.WHITE,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        btnNextMonth.setColorFilter(
            android.graphics.Color.WHITE,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    // ── ViewModel 옵저빙 ─────────────────────────────────────────
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scheduleMap.collect { scheduleMap ->
                Log.d(TAG, "scheduleMap 업데이트 - 날짜 수: ${scheduleMap.size}")
                currentScheduleMap = scheduleMap
                renderCalendar()

                // 선택된 날짜가 있으면 패널도 갱신
                if (selectedDay != -1) {
                    onDaySelected(selectedDay)
                }
            }
        }
    }

    // ── 현재 월 데이터 로드 + 디폴트 날짜 선택 ─────────────────
    private fun loadCurrentMonth() {
        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        // 디폴트 날짜 선택 로직
        selectedDay = getDefaultSelectedDay(year, month)

        renderCalendar()

        // 데이터 로드 (비동기)
        loadMonthData()
    }


    /**
     * 현재 보고 있는 달의 디폴트 선택 날짜 계산
     * - 현재 월: 오늘 날짜
     * - 다른 월: 해당 월의 1일
     */
    private fun getDefaultSelectedDay(year: Int, month: Int): Int {
        val today = Calendar.getInstance()
        val todayYear  = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay   = today.get(Calendar.DAY_OF_MONTH)

        return if (year == todayYear && month == todayMonth) {
            // 현재 월 → 오늘 날짜 선택
            todayDay
        } else {
            // 다른 월 → 1일 선택
            1
        }
    }

    /**
     * 해당 월의 마지막 날짜 구하기
     */
    private fun getLastDayOfMonth(year: Int, month: Int): Int {
        val tempCal = Calendar.getInstance()
        tempCal.set(year, month, 1)
        return tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // ── 캘린더 RecyclerView 셋업 ─────────────────────────────────
    private fun setupCalendarRecyclerView() {
        calendarAdapter = CalendarAdapter(emptyList()) { day ->
            if (day.dayNumber == 0) return@CalendarAdapter
            selectedDay = day.dayNumber
            onDaySelected(day.dayNumber)
            renderCalendar()
        }
        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = calendarAdapter
    }

    // ── 일정 RecyclerView 셋업 ───────────────────────────────────
    private fun setupScheduleRecyclerView() {
        scheduleAdapter = ScheduleAdapter(emptyList())
        rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        rvSchedule.adapter = scheduleAdapter
    }

    // ── 이전/다음 달 버튼 ────────────────────────────────────────
    private fun setupMonthNavigation() {
        btnPrevMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)

            // 이전 달 → 마지막 날 선택
            val year  = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            selectedDay = getLastDayOfMonth(year, month)

            currentScheduleMap = emptyMap()
            renderCalendar()
            onDaySelected(selectedDay)
            loadMonthData()
        }

        btnNextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)

            // 다음 달 → 1일 선택
            selectedDay = 1
            currentScheduleMap = emptyMap()
            renderCalendar()
            onDaySelected(selectedDay)
            loadMonthData()
        }
    }

    private fun loadMonthData() {
        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        viewModel.loadMonthRecords(year, month)
    }

    // ── 달력 날짜 리스트 생성 및 렌더링 ─────────────────────────
    private fun renderCalendar() {
        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        tvMonthYear.text = "${year}년 ${month + 1}월"

        val today = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == year
                && today.get(Calendar.MONTH) == month
        val todayDay = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        val tempCal = Calendar.getInstance()
        tempCal.set(year, month, 1)
        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1
        val maxDay         = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dayList = mutableListOf<CalendarDay>()

        // 앞 빈 칸
        repeat(firstDayOfWeek) { col ->
            dayList.add(CalendarDay(dayNumber = 0, dayOfWeek = col))
        }

        // 날짜 채우기 - DB 데이터 기반
        for (d in 1..maxDay) {
            val col       = (firstDayOfWeek + d - 1) % 7
            val schedules = currentScheduleMap[d]
            val hasCheck  = schedules?.any { it.isDone }  ?: false
            val hasClock  = schedules?.any { !it.isDone } ?: false

            dayList.add(
                CalendarDay(
                    dayNumber  = d,
                    dayOfWeek  = col,
                    isToday    = (d == todayDay),
                    isSelected = (d == selectedDay),
                    hasCheck   = hasCheck,
                    hasClock   = hasClock
                )
            )
        }

        // 뒷 빈 칸 (42칸 고정)
        val remaining = 42 - dayList.size
        repeat(remaining) { dayList.add(CalendarDay(dayNumber = 0)) }

        calendarAdapter.updateDays(dayList)
    }

    // ── 날짜 클릭 → 우측 패널 업데이트 ─────────────────────────
    private fun onDaySelected(day: Int) {
        val month     = calendar.get(Calendar.MONTH) + 1
        tvSelectedDate.text = "${month}월 ${day}일"

        val schedules = currentScheduleMap[day] ?: emptyList()

        Log.d(TAG, "날짜 선택 - ${month}월 ${day}일, 기록 수: ${schedules.size}")

        if (schedules.isEmpty()) {
            tvEmptySchedule.visibility = View.VISIBLE
            rvSchedule.visibility      = View.GONE
        } else {
            tvEmptySchedule.visibility = View.GONE
            rvSchedule.visibility      = View.VISIBLE
            scheduleAdapter.updateItems(schedules)
        }
    }
}
