package com.scchyodol.smarthelper.presentation.home.fragment.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scchyodol.smarthelper.data.model.CareRecord
import com.scchyodol.smarthelper.data.model.ScheduleItem
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarViewModel(
    private val repository: CareRecordRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CalendarViewModel"
    }

    // 날짜(일) -> ScheduleItem 리스트 맵
    private val _scheduleMap = MutableStateFlow<Map<Int, List<ScheduleItem>>>(emptyMap())
    val scheduleMap: StateFlow<Map<Int, List<ScheduleItem>>> = _scheduleMap

    /**
     * 특정 연/월의 기록 로드
     * isDone 기준: record의 timestamp < 현재시각 → true(완료), 아니면 → false(예정)
     */
    fun loadMonthRecords(year: Int, month: Int) {
        // 해당 월의 시작 ~ 끝 timestamp 계산
        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }

        val startOfMonth = startCal.timeInMillis
        val endOfMonth   = endCal.timeInMillis

        Log.d(TAG, "loadMonthRecords - year: $year, month: ${month + 1}, start: $startOfMonth, end: $endOfMonth")

        viewModelScope.launch {
            repository.getRecordsByMonth(startOfMonth, endOfMonth)
                .catch { e ->
                    Log.e(TAG, "월별 기록 조회 실패: ${e.message}", e)
                    _scheduleMap.value = emptyMap()
                }
                .collect { records ->
                    Log.d(TAG, "조회된 기록 수: ${records.size}")
                    _scheduleMap.value = buildScheduleMap(records)
                }
        }
    }

    /**
     * CareRecord 리스트 → Map<일, List<ScheduleItem>> 변환
     */
    private fun buildScheduleMap(records: List<CareRecord>): Map<Int, List<ScheduleItem>> {
        val now       = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
        val dayCal    = Calendar.getInstance()

        return records
            .groupBy { record ->
                // timestamp → 일(day) 추출
                dayCal.timeInMillis = record.timestamp
                dayCal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { (day, dayRecords) ->
                dayRecords.map { record ->
                    val timeStr = timeFormat.format(record.timestamp)
                    val isDone  = record.timestamp <= now  // 현재 이전이면 완료
                    val label   = buildLabel(record)

                    Log.d(TAG, "  day=$day, time=$timeStr, category=${record.category}, isDone=$isDone")

                    ScheduleItem(
                        time   = timeStr,
                        label  = label,
                        isDone = isDone
                    )
                }
            }
    }

    /**
     * CareRecord → 표시 라벨 생성
     * 예: "투약 - 1정", "체온 - 36.5°C"
     */
    private fun buildLabel(record: CareRecord): String {
        val categoryName = record.category.displayName
        return if (record.value.isNullOrBlank()) {
            categoryName
        } else {
            "$categoryName - ${record.value}"
        }
    }
}
