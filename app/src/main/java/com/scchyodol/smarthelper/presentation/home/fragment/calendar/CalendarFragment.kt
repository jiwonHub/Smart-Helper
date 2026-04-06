package com.scchyodol.smarthelper.presentation.home.fragment.calendar

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.CalendarDay
import com.scchyodol.smarthelper.data.model.ScheduleItem
import com.scchyodol.smarthelper.databinding.FragmentCalendarBinding
import com.scchyodol.smarthelper.presentation.home.main.DeleteState
import com.scchyodol.smarthelper.presentation.home.main.ExportState
import com.scchyodol.smarthelper.presentation.home.main.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class CalendarFragment : Fragment() {

    companion object {
        private const val TAG = "CalendarFragment"
    }

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var scheduleAdapter: ScheduleAdapter

    private val calendar = Calendar.getInstance()
    private var selectedDay: Int = -1
    private var currentScheduleMap: Map<Int, List<ScheduleItem>> = emptyMap()

    private val viewModel: MainViewModel by activityViewModels()

    private var loadingDialog: AlertDialog? = null
    private var currentDetailDialog: AlertDialog? = null

    // ── 생명주기 ─────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendarRecyclerView()
        setupScheduleRecyclerView()
        setupMonthNavigation()
        setupButton()
        observeViewModel()
        loadCurrentMonth()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── ViewModel 옵저빙 ─────────────────────────────────────────
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // 캘린더 스케줄 맵
                launch {
                    viewModel.scheduleMap.collect { scheduleMap ->
                        Log.d(TAG, "scheduleMap 업데이트 - 날짜 수: ${scheduleMap.size}")
                        currentScheduleMap = scheduleMap
                        renderCalendar()
                        if (selectedDay != -1) onDaySelected(selectedDay)
                    }
                }

                // PDF 내보내기 상태
                launch {
                    viewModel.exportState.collect { state ->
                        when (state) {
                            is ExportState.Loading -> showLoadingDialog()
                            is ExportState.Success -> {
                                hideLoadingDialog()
                                showOpenPdfDialog(state.file)
                                viewModel.resetExportState()
                            }
                            is ExportState.Error -> {
                                hideLoadingDialog()
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetExportState()
                            }
                            is ExportState.Idle -> Unit
                        }
                    }
                }

                launch {
                    viewModel.deleteState.collect { state ->
                        when (state) {
                            is DeleteState.Loading -> { /* 필요시 로딩 표시 */ }
                            is DeleteState.Success -> {
                                Toast.makeText(requireContext(), "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                // 다이얼로그가 열려있으면 닫기
                                currentDetailDialog?.dismiss()
                                currentDetailDialog = null
                                viewModel.resetDeleteState()
                            }
                            is DeleteState.Error -> {
                                Toast.makeText(requireContext(), "삭제 실패: ${state.message}", Toast.LENGTH_SHORT).show()
                                viewModel.resetDeleteState()
                            }
                            is DeleteState.Idle -> Unit
                        }
                    }
                }
            }
        }
    }

    // ── 현재 월 로드 ─────────────────────────────────────────────
    private fun loadCurrentMonth() {
        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        selectedDay = getDefaultSelectedDay(year, month)
        renderCalendar()
        viewModel.loadCalendarMonth(year, month)
    }

    private fun getDefaultSelectedDay(year: Int, month: Int): Int {
        val today = Calendar.getInstance()
        return if (year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH))
            today.get(Calendar.DAY_OF_MONTH) else 1
    }

    private fun getLastDayOfMonth(year: Int, month: Int): Int {
        return Calendar.getInstance().apply { set(year, month, 1) }
            .getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // ── 캘린더 RecyclerView ──────────────────────────────────────
    private fun setupCalendarRecyclerView() {
        calendarAdapter = CalendarAdapter(emptyList()) { day ->
            if (day.dayNumber == 0) return@CalendarAdapter
            selectedDay = day.dayNumber
            onDaySelected(day.dayNumber)
            renderCalendar()
        }
        binding.rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.rvCalendar.adapter = calendarAdapter
    }

    // ── 일정 RecyclerView ────────────────────────────────────────
    private fun setupScheduleRecyclerView() {
        scheduleAdapter = ScheduleAdapter(emptyList()) { item, position ->
            this@CalendarFragment.showDeleteDialog(item)
        }
        binding.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedule.adapter = scheduleAdapter
    }

    // ── 이전/다음 달 버튼 ────────────────────────────────────────
    private fun setupMonthNavigation() {
        binding.btnPrevMonth.apply {
            setColorFilter(
                android.graphics.Color.WHITE,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            setOnClickListener {
                calendar.add(Calendar.MONTH, -1)
                val year  = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                selectedDay        = getLastDayOfMonth(year, month)
                currentScheduleMap = emptyMap()
                renderCalendar()
                onDaySelected(selectedDay)
                viewModel.loadCalendarMonth(year, month)
            }
        }

        binding.btnNextMonth.apply {
            setColorFilter(
                android.graphics.Color.WHITE,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            setOnClickListener {
                calendar.add(Calendar.MONTH, 1)
                val year  = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                selectedDay        = 1
                currentScheduleMap = emptyMap()
                renderCalendar()
                onDaySelected(selectedDay)
                viewModel.loadCalendarMonth(year, month)
            }
        }
    }

    // ── 내보내기 버튼 ─────────────────────────────────────────────
    private fun setupButton() {
        binding.dataOtpt.setOnClickListener {
            showExportDialog()
        }
        binding.btnDateDetail.setOnClickListener {
            val schedules = currentScheduleMap[selectedDay] ?: emptyList()
            showDateDetailDialog(selectedDay, schedules)
        }

    }

    // ── 달력 렌더링 ──────────────────────────────────────────────
    private fun renderCalendar() {
        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        binding.tvMonthYear.text = "${year}년 ${month + 1}월"

        val today          = Calendar.getInstance()
        val isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month
        val todayDay       = if (isCurrentMonth) today.get(Calendar.DAY_OF_MONTH) else -1

        val tempCal = Calendar.getInstance().apply { set(year, month, 1) }
        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1
        val maxDay         = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dayList = mutableListOf<CalendarDay>()

        repeat(firstDayOfWeek) { col ->
            dayList.add(CalendarDay(dayNumber = 0, dayOfWeek = col))
        }

        for (d in 1..maxDay) {
            val col       = (firstDayOfWeek + d - 1) % 7
            val schedules = currentScheduleMap[d]
            dayList.add(
                CalendarDay(
                    dayNumber  = d,
                    dayOfWeek  = col,
                    isToday    = (d == todayDay),
                    isSelected = (d == selectedDay),
                    hasCheck   = schedules?.any { it.isDone }  ?: false,
                    hasClock   = schedules?.any { !it.isDone } ?: false
                )
            )
        }

        val remaining = 42 - dayList.size
        repeat(remaining) { dayList.add(CalendarDay(dayNumber = 0)) }

        calendarAdapter.updateDays(dayList)
    }

    // ── 날짜 클릭 → 일정 패널 업데이트 ─────────────────────────
    private fun onDaySelected(day: Int) {
        val month = calendar.get(Calendar.MONTH) + 1
        binding.tvSelectedDate.text = "${month}월 ${day}일"

        val schedules = currentScheduleMap[day] ?: emptyList()
        Log.d(TAG, "날짜 선택 - ${month}월 ${day}일, 기록 수: ${schedules.size}")

        if (schedules.isEmpty()) {
            binding.tvEmptySchedule.visibility = View.VISIBLE
            binding.rvSchedule.visibility      = View.GONE
        } else {
            binding.tvEmptySchedule.visibility = View.GONE
            binding.rvSchedule.visibility      = View.VISIBLE
            scheduleAdapter.updateItems(schedules)
        }
    }

    // ── 내보내기 다이얼로그 ───────────────────────────────────────
    private fun showExportDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_data_export, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnExportPdf).setOnClickListener {
            dialog.dismiss()
            viewModel.exportCurrentMonthToPdf()
        }

        dialogView.findViewById<View>(R.id.btnExportCsv).setOnClickListener {
            Toast.makeText(requireContext(), "준비 중인 기능입니다.", Toast.LENGTH_SHORT).show()
        }

        dialogView.findViewById<View>(R.id.btnExportShare).setOnClickListener {
            Toast.makeText(requireContext(), "준비 중인 기능입니다.", Toast.LENGTH_SHORT).show()
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDateDetailDialog(day: Int, schedules: List<ScheduleItem>) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_date_detail, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // ← 이 부분이 누락되었었음!
        currentDetailDialog = dialog

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 날짜 텍스트
        val month = calendar.get(Calendar.MONTH) + 1
        dialogView.findViewById<TextView>(R.id.tvDialogDate).text = "${month}월 ${day}일 상세 일정"

        // 리사이클러뷰
        val rv    = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDetailSchedule)
        val empty = dialogView.findViewById<TextView>(R.id.tvDetailEmpty)

        if (schedules.isEmpty()) {
            rv.visibility    = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            rv.visibility    = View.VISIBLE
            empty.visibility = View.GONE
            rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
            rv.adapter       = ScheduleDetailAdapter(schedules) { item, _ ->
                showDeleteDialog(item)
            }
        }

        // 닫기 버튼들
        dialogView.findViewById<View>(R.id.btnDialogClose).setOnClickListener {
            dialog.dismiss()
            currentDetailDialog = null
        }
        dialogView.findViewById<View>(R.id.btnDetailClose).setOnClickListener {
            dialog.dismiss()
            currentDetailDialog = null
        }

        // 다이얼로그가 닫힐 때 참조 해제
        dialog.setOnDismissListener {
            currentDetailDialog = null
        }

        dialog.show()
    }

    // ── PDF 열기 확인 팝업 ────────────────────────────────────────
    private fun showOpenPdfDialog(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle("PDF 저장 완료")
            .setMessage("""
            리포트가 다운로드 폴더에 저장되었습니다.
            
            📂 파일명: ${file.name}
            💾 위치: Downloads 폴더
        """.trimIndent())
            .setPositiveButton("확인", null)
            .show()
    }

    // ── 로딩 다이얼로그 ───────────────────────────────────────────
    private fun showLoadingDialog() {
        loadingDialog = AlertDialog.Builder(requireContext())
            .setMessage("PDF 생성 중...")
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun showDeleteDialog(item: ScheduleItem) {
        if (item.isRepeat) {
            showDeleteDialog(item, "반복 일정 삭제", "선택하신 '${item.label}' 일정은 반복 일정이므로 해당 모든 반복 일정이 삭제됩니다. 그래도 삭제하시겠습니까?")
        } else {
            showDeleteDialog(item, "일정 삭제", "'${item.label}' 일정을 삭제하시겠습니까?")
        }
    }

    private fun showDeleteDialog(item: ScheduleItem, title: String, description: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(description)
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteSchedule(item.id)
            }
            .setNegativeButton("취소", null)
            .show()
    }


    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}
