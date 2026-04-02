package com.scchyodol.smarthelper.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scchyodol.smarthelper.data.model.UserMood
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(userMood: UserMood): Long

    // 특정 기록 삭제
    @Delete
    suspend fun delete(userMood: UserMood)

    // id로 삭제
    @Query("DELETE FROM user_moods WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 전체 삭제
    @Query("DELETE FROM user_moods")
    suspend fun deleteAll()

    // 전체 조회 (최신순)
    @Query("SELECT * FROM user_moods ORDER BY date DESC")
    fun getAll(): Flow<List<UserMood>>

    // id로 단건 조회
    @Query("SELECT * FROM user_moods WHERE id = :id")
    suspend fun getById(id: Long): UserMood?

    // 날짜 범위로 조회
    @Query("""
        SELECT * FROM user_moods 
        WHERE date BETWEEN :startMillis AND :endMillis 
        ORDER BY date ASC
    """)
    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<UserMood>>

    // 기분별 조회
    @Query("SELECT * FROM user_moods WHERE mood = :mood ORDER BY date DESC")
    fun getByMood(mood: String): Flow<List<UserMood>>

    // 특정 날짜 기록 단건 조회 (하루에 하나만 저장할 경우 유용)
    @Query("SELECT * FROM user_moods WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: Long): UserMood?
}
