// com.scchyodol.smarthelper.data.remote.repository.UserMoodRepository.kt

package com.scchyodol.smarthelper.data.remote.repository

import com.scchyodol.smarthelper.data.dao.UserMoodDao
import com.scchyodol.smarthelper.data.model.Mood
import com.scchyodol.smarthelper.data.model.UserMood
import kotlinx.coroutines.flow.Flow

class UserMoodRepository(private val dao: UserMoodDao) {

    // 전체 조회
    fun getAll(): Flow<List<UserMood>> = dao.getAll()

    // 날짜 범위 조회
    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<UserMood>> =
        dao.getByDateRange(startMillis, endMillis)

    // 특정 날짜 단건 조회 (오늘 무드 불러올 때)
    suspend fun getByDate(date: Long): UserMood? = dao.getByDate(date)

    // 저장 (하루에 하나 → REPLACE 전략이므로 같은 date면 덮어씀)
    suspend fun insert(mood: Mood, date: Long): Long {
        val userMood = UserMood(date = date, mood = mood)
        return dao.insert(userMood)
    }

    // 삭제
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
