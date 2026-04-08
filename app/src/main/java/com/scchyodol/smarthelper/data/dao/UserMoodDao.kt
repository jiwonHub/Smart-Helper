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

    @Delete
    suspend fun delete(userMood: UserMood)

    @Query("DELETE FROM user_moods WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ★ 날짜 기준 삭제 추가
    @Query("DELETE FROM user_moods WHERE date = :date")
    suspend fun deleteByDate(date: Long)

    @Query("DELETE FROM user_moods")
    suspend fun deleteAll()

    @Query("SELECT * FROM user_moods ORDER BY date DESC")
    fun getAll(): Flow<List<UserMood>>

    @Query("SELECT * FROM user_moods WHERE id = :id")
    suspend fun getById(id: Long): UserMood?

    @Query("""
        SELECT * FROM user_moods 
        WHERE date BETWEEN :startMillis AND :endMillis 
        ORDER BY date ASC
    """)
    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<UserMood>>

    @Query("SELECT * FROM user_moods WHERE mood = :mood ORDER BY date DESC")
    fun getByMood(mood: String): Flow<List<UserMood>>

    @Query("SELECT * FROM user_moods WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: Long): UserMood?
}
