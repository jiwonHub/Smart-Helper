package com.scchyodol.smarthelper.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.model.CareRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CareRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CareRecord): Long

    // 특정 레코드 삭제
    @Delete
    suspend fun delete(record: CareRecord)

    // id로 삭제
    @Query("DELETE FROM care_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 전체 삭제
    @Query("DELETE FROM care_records")
    suspend fun deleteAll()

    // 전체 조회 (최신순)
    @Query("SELECT * FROM care_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CareRecord>>

    @Query("SELECT * FROM care_records ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<CareRecord>

    // id로 단건 조회
    @Query("SELECT * FROM care_records WHERE id = :id")
    suspend fun getById(id: Long): CareRecord?

    @Query("SELECT * FROM care_records ORDER BY timestamp ASC")
    suspend fun getAllRecords(): List<CareRecord>

    // 날짜 범위로 조회 (특정 날 기록 불러올 때 유용)
    @Query("""
        SELECT * FROM care_records 
        WHERE timestamp BETWEEN :startMillis AND :endMillis 
        ORDER BY timestamp ASC
    """)
    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<CareRecord>>

    // 카테고리별 조회
    @Query("SELECT * FROM care_records WHERE category = :category ORDER BY timestamp DESC")
    fun getByCategory(category: String): Flow<List<CareRecord>>

    // 단일 날짜 기준 범위 조회 (timestamp 범위)
    @Query("""
        SELECT * FROM care_records
        WHERE isRepeat = 0
          AND timestamp >= :startOfDay
          AND timestamp < :endOfDay
        ORDER BY timestamp ASC
    """)
    fun getRecordsByDate(startOfDay: Long, endOfDay: Long): Flow<List<CareRecord>>


    // 카테고리별 수치 히스토리 조회
    @Query("""
    SELECT DISTINCT value FROM care_records 
    WHERE category = :category 
    ORDER BY timestamp DESC
    """)
    fun getValueHistoryByCategory(category: CareCategory): Flow<List<String>>

    // 특정 월의 기록 전체 조회 (timestamp 범위로)
    @Query("SELECT * FROM care_records WHERE timestamp >= :startOfMonth AND timestamp < :endOfMonth AND isRepeat = 0")
    fun getRecordsByMonth(startOfMonth: Long, endOfMonth: Long): Flow<List<CareRecord>>

    @Query("SELECT * FROM care_records WHERE isRepeat = 1")
    suspend fun getAllRepeatRecords(): List<CareRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CareRecord>)

    @Query("SELECT * FROM care_records WHERE isRepeat = 1")
    fun getRepeatRecords(): Flow<List<CareRecord>>

    @Query("SELECT * FROM care_records WHERE isRepeat = 1")
    suspend fun getRepeatRecordsOnce(): List<CareRecord>
}
