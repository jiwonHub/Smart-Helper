// com.scchyodol.smarthelper.data.remote.repository.UserMoodRepository.kt

package com.scchyodol.smarthelper.data.remote.repository

import android.util.Log
import com.scchyodol.smarthelper.data.dao.UserMoodDao
import com.scchyodol.smarthelper.data.model.Mood
import com.scchyodol.smarthelper.data.model.UserMood
import kotlinx.coroutines.flow.Flow

class UserMoodRepository(private val dao: UserMoodDao) {

    companion object {
        private const val TAG = "UserMoodRepository"
    }

    // 전체 조회
    fun getAll(): Flow<List<UserMood>> = dao.getAll()

    // 날짜 범위 조회
    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<UserMood>> =
        dao.getByDateRange(startMillis, endMillis)

    // 특정 날짜 단건 조회 (오늘 무드 불러올 때)
    suspend fun getByDate(date: Long): UserMood? = dao.getByDate(date)

    // 저장 (하루에 하나 → REPLACE 전략이므로 같은 date면 덮어씀)
    suspend fun insert(mood: Mood, date: Long): Long {
        // ★ 같은 날짜 기존 무드 먼저 삭제 후 새로 저장 (유니크 제약 전 안전망)
        dao.deleteByDate(date)

        val userMood = UserMood(date = date, mood = mood)
        val id = dao.insert(userMood)

        Log.d(TAG, "무드 저장 완료 - ID: $id, mood: $mood, date: $date")
        return id
    }

    // 삭제
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
