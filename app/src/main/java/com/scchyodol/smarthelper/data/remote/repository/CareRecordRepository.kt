package com.scchyodol.smarthelper.data.remote.repository

import android.util.Log
import com.scchyodol.smarthelper.data.dao.CareRecordDao
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.model.CareRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    suspend fun getAllRecords(): List<CareRecord> {
        return dao.getAllRecords()
    }

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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

        Log.d(TAG, "=== Repository 월별 조회 ===")
        Log.d(TAG, "startOfMonth: $startOfMonth (${dateFormat.format(startOfMonth)})")
        Log.d(TAG, "endOfMonth: $endOfMonth (${dateFormat.format(endOfMonth)})")

        return dao.getRecordsByMonth(startOfMonth, endOfMonth).onEach { records ->
            Log.d(TAG, "=== DAO에서 반환된 데이터 ===")
            Log.d(TAG, "레코드 수: ${records.size}")

            records.forEach { record ->
                val timeStr = dateFormat.format(record.timestamp)
                Log.d(TAG, "  - id=${record.id}, time=$timeStr, cat=${record.category}, val='${record.value}'")
            }
        }
    }

    // 1회 조회용 (buildScheduleMap 내부에서 사용)
    suspend fun getRepeatRecordsOnce(): List<CareRecord> {
        return dao.getRepeatRecordsOnce()
    }

    suspend fun getAllOnce(): List<CareRecord> = dao.getAllOnce()


}
