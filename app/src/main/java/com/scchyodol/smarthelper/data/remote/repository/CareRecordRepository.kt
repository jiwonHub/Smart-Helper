package com.scchyodol.smarthelper.data.remote.repository

import android.util.Log
import com.scchyodol.smarthelper.data.dao.CareRecordDao
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.model.CareRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.util.Calendar

class CareRecordRepository(
    private val dao: CareRecordDao
) {

    companion object {
        private const val TAG = "CareRecordRepository"
    }

    // ── 저장 ──
    suspend fun insert(record: CareRecord): Long {
        val id = dao.insert(record)
        Log.d(TAG, "DB 저장 완료 - id: $id, category: ${record.category}, value: ${record.value}, isRepeat: ${record.isRepeat}, repeatDays: ${record.repeatDays}")
        return id
    }

    // ── 특정 레코드 삭제 ──
    suspend fun delete(record: CareRecord) = dao.delete(record)

    // ── id로 삭제 ──
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    // ── 전체 삭제 ──
    suspend fun deleteAll() = dao.deleteAll()

    // ── 전체 조회 ──
    fun getAll(): Flow<List<CareRecord>> = dao.getAll()

    // ── id로 단건 조회 ──
    suspend fun getById(id: Long): CareRecord? = dao.getById(id)

    // ── 카테고리별 조회 ──
    fun getByCategory(category: String): Flow<List<CareRecord>> =
        dao.getByCategory(category)

    // ── 카테고리별 수치 히스토리 ──
    fun getValueHistoryByCategory(category: CareCategory): Flow<List<String>> {
        Log.d(TAG, "getValueHistoryByCategory 호출 - category: '${category.name}'")
        return dao.getValueHistoryByCategory(category).onEach { values ->
            Log.d(TAG, "DB 조회 결과 - category: '${category.name}', count: ${values.size}")
            values.forEachIndexed { index, value ->
                Log.d(TAG, "  [$index] '$value'")
            }
        }
    }

    // ── 월별 단일 기록 조회 (Flow) ──
    fun getRecordsByMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<CareRecord>> {
        Log.d(TAG, "getRecordsByMonth 호출 - start: $startOfMonth, end: $endOfMonth")
        return dao.getRecordsByMonth(startOfMonth, endOfMonth)
    }

    fun getAllRepeatRecordsFlow(): Flow<List<CareRecord>> = dao.getRepeatRecords()

    // ────────────────────────────────────────────────
    // ── 신규: 특정 날짜의 기록 (단일 + 반복 통합) ──
    // ────────────────────────────────────────────────
    fun getRecordsForDate(dateStr: String): Flow<List<CareRecord>> {
        val (startOfDay, endOfDay) = getDateRange(dateStr)
        val dayOfWeek = getDayOfWeek(dateStr)

        Log.d(TAG, "getRecordsForDate - date: $dateStr, dayOfWeek: $dayOfWeek")

        val singleFlow = dao.getRecordsByDate(startOfDay, endOfDay)
        val repeatFlow = dao.getRepeatRecords()

        return combine(singleFlow, repeatFlow) { singles, repeats ->
            // 해당 요일에 해당하는 반복 기록만 필터
            val filteredRepeats = repeats.filter { record ->
                if (record.repeatDays.isBlank()) return@filter false
                val days = record.repeatDays
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                dayOfWeek in days
            }

            Log.d(TAG, "통합 결과 - singles: ${singles.size}, filteredRepeats: ${filteredRepeats.size}")

            // 시간(시:분) 기준으로 정렬
            (singles + filteredRepeats).sortedBy {
                it.timestamp % (24L * 60 * 60 * 1000)
            }
        }
    }

    // ────────────────────────────────────────────────
    // ── 신규: 달력 월별 이벤트 점 표시용 Map 반환 ──
    // ────────────────────────────────────────────────
    suspend fun getEventDotsForMonth(year: Int, month: Int): Map<String, Boolean> {
        val (monthStart, monthEnd) = getMonthRange(year, month)

        // suspend 버전 사용 (getAllRepeatRecords)
        val repeats = dao.getAllRepeatRecords()
        Log.d(TAG, "getEventDotsForMonth - year: $year, month: $month, repeatCount: ${repeats.size}")

        val cal         = Calendar.getInstance()
        val result      = mutableMapOf<String, Boolean>()

        cal.set(year, month - 1, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (day in 1..daysInMonth) {
            cal.set(year, month - 1, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val dateStr      = "%04d-%02d-%02d".format(year, month, day)
            val startOfDay   = cal.timeInMillis
            val endOfDay     = startOfDay + 24L * 60 * 60 * 1000
            val dayOfWeek    = convertDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))

            // 해당 날짜에 단일 기록 있는지 (suspend 대신 범위 계산으로 직접 확인)
            val hasSingle = dao.getRecordsByDate(startOfDay, endOfDay)
                .let { false } // Flow라서 아래 별도 처리

            // 반복 기록 중 해당 요일 포함 여부
            val hasRepeat = repeats.any { record ->
                if (record.repeatDays.isBlank()) return@any false
                val days = record.repeatDays
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                dayOfWeek in days
            }

            result[dateStr] = hasRepeat
        }

        // 단일 기록은 월별 Flow에서 별도 merge (아래 참고)
        // 반복 기록 점 표시는 result에 이미 반영됨
        return result
    }

    // ────────────────────────────────────────────────
    // ── 신규: 달력용 완전한 이벤트 점 Map (단일+반복) ──
    // Flow로 실시간 반영
    // ────────────────────────────────────────────────
    fun getEventDotsFlow(year: Int, month: Int): Flow<Map<String, Boolean>> {
        val (monthStart, monthEnd) = getMonthRange(year, month)

        val monthFlow  = dao.getRecordsByMonth(monthStart, monthEnd)
        val repeatFlow = dao.getRepeatRecords()

        return combine(monthFlow, repeatFlow) { singles, repeats ->
            val cal         = Calendar.getInstance()
            val result      = mutableMapOf<String, Boolean>()

            cal.set(year, month - 1, 1)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            for (day in 1..daysInMonth) {
                cal.set(year, month - 1, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val dateStr    = "%04d-%02d-%02d".format(year, month, day)
                val startOfDay = cal.timeInMillis
                val endOfDay   = startOfDay + 24L * 60 * 60 * 1000
                val dayOfWeek  = convertDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))

                // 단일 기록 있는지
                val hasSingle = singles.any { record ->
                    record.timestamp in startOfDay until endOfDay
                }

                // 반복 기록 중 해당 요일 포함 여부
                val hasRepeat = repeats.any { record ->
                    if (record.repeatDays.isBlank()) return@any false
                    val days = record.repeatDays
                        .split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                    dayOfWeek in days
                }

                result[dateStr] = hasSingle || hasRepeat
            }

            Log.d(TAG, "getEventDotsFlow 계산 완료 - year: $year, month: $month")
            result
        }
    }

    // ─────────────────────
    // ── private 유틸 ──
    // ─────────────────────

    // "yyyy-MM-dd" → (startOfDay, endOfDay) 밀리초
    private fun getDateRange(dateStr: String): Pair<Long, Long> {
        val parts = dateStr.split("-")
        val cal   = Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end   = start + 24L * 60 * 60 * 1000
        return Pair(start, end)
    }

    // "yyyy-MM-dd" → 요일 (0=월 ~ 6=일)
    private fun getDayOfWeek(dateStr: String): Int {
        val parts = dateStr.split("-")
        val cal   = Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        return convertDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))
    }

    // Calendar.DAY_OF_WEEK → 0=월 ~ 6=일
    private fun convertDayOfWeek(calDay: Int): Int {
        return when (calDay) {
            Calendar.MONDAY    -> 0
            Calendar.TUESDAY   -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY  -> 3
            Calendar.FRIDAY    -> 4
            Calendar.SATURDAY  -> 5
            Calendar.SUNDAY    -> 6
            else               -> 0
        }
    }

    // year/month → (monthStart, monthEnd) 밀리초
    private fun getMonthRange(year: Int, month: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }.timeInMillis

        return Pair(start, end)
    }

    fun getRepeatRecordsFlow(): Flow<List<CareRecord>> {
        Log.d(TAG, "getRepeatRecordsFlow 호출")
        return dao.getRepeatRecords()  // 기존 DAO 메서드 그대로 사용
    }

}
