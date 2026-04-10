package com.scchyodol.smarthelper.presentation.home.carerecord

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.model.CareRecord
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CareRecordViewModel(
    private val repository: CareRecordRepository
) : ViewModel() {

    companion object {
        private const val TAG       = "CareRecordViewModel"
        private const val PREF_NAME = "care_defaults"
    }

    sealed class SaveState {
        object Idle    : SaveState()
        object Loading : SaveState()
        data class Success(val id: Long)      : SaveState()
        data class Error(val message: String) : SaveState()
    }

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    // ─── 단건 저장 ─────────────────────────────────────
    fun saveRecord(
        timestamp : Long,
        category  : String,
        value     : String,
        memo      : String
    ) {
        val adjustedTimestamp = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val careCategory = toCareCategory(category)
        val record = CareRecord(
            timestamp = adjustedTimestamp,
            category  = careCategory,
            value     = value,
            memo      = memo,
            isRepeat  = false,
            repeatDays = ""
        )

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        Log.d(TAG, "=== 단건 저장 시도 ===")
        Log.d(TAG, "timestamp: $timestamp (${fmt.format(timestamp)}), category: $careCategory")

        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val id = repository.insert(record)
                Log.d(TAG, "단건 저장 성공 - ID: $id")
                _saveState.value = SaveState.Success(id)
            } catch (e: Exception) {
                Log.e(TAG, "단건 저장 실패: ${e.message}", e)
                _saveState.value = SaveState.Error(e.message ?: "저장 중 오류 발생")
            }
        }
    }

    // ─── 반복 저장: 레코드 딱 1건만 저장 ──────────────
    // timestamp = 사용자가 설정한 시작 날짜+시각 (이 시각 이후부터 반복)
    // repeatDays = 선택한 요일 (예: "0,2,4")
    // 캘린더 조회 시 repeatDays 기반으로 동적으로 펼쳐서 표시
    fun saveRepeatRecord(
        baseTimestamp  : Long,
        category       : String,
        value          : String,
        memo           : String,
        repeatDaysList : List<Int>  // 0=월 ~ 6=일
    ) {
        if (repeatDaysList.isEmpty()) {
            _saveState.value = SaveState.Error("반복할 요일을 선택해주세요.")
            return
        }

        val adjustedTimestamp = Calendar.getInstance().apply {
            timeInMillis = baseTimestamp
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val careCategory  = toCareCategory(category)
        val repeatDaysStr = repeatDaysList.sorted().joinToString(",")

        // 레코드 1건만 저장
        // timestamp = 반복 시작 기준 시각 (이 시각 이후의 해당 요일에만 표시)
        val record = CareRecord(
            timestamp  = adjustedTimestamp,
            category   = careCategory,
            value      = value,
            memo       = memo,
            isRepeat   = true,
            repeatDays = repeatDaysStr
        )

        Log.d(TAG, "=== 반복 저장 (1건) ===")
        Log.d(TAG, "repeatDays: $repeatDaysStr, baseTimestamp: $baseTimestamp")

        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val id = repository.insert(record)
                Log.d(TAG, "반복 저장 성공 - ID: $id, repeatDays: $repeatDaysStr")
                _saveState.value = SaveState.Success(id)
            } catch (e: Exception) {
                Log.e(TAG, "반복 저장 실패: ${e.message}", e)
                _saveState.value = SaveState.Error(e.message ?: "저장 중 오류 발생")
            }
        }
    }

    fun updateRecord(
        id        : Long,
        timestamp : Long,
        category  : String,
        value     : String,
        memo      : String
    ) {
        val careCategory = toCareCategory(category)
        val record = CareRecord(
            id        = id,
            timestamp = timestamp,
            category  = careCategory,
            value     = value,
            memo      = memo,
            isRepeat  = false,
            repeatDays = ""
        )

        Log.d(TAG, "=== 일반 기록 수정 ===")
        Log.d(TAG, "id: $id, category: $careCategory, value: '$value'")

        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val updatedId = repository.insert(record) // Room @Insert에서 REPLACE 정책
                Log.d(TAG, "수정 완료 - ID: $updatedId")
                _saveState.value = SaveState.Success(updatedId)
            } catch (e: Exception) {
                Log.e(TAG, "수정 실패: ${e.message}", e)
                _saveState.value = SaveState.Error(e.message ?: "수정 중 오류 발생")
            }
        }
    }

    fun updateRepeatRecord(
        id             : Long,
        baseTimestamp  : Long,
        category       : String,
        value          : String,
        memo           : String,
        repeatDaysList : List<Int>
    ) {
        if (repeatDaysList.isEmpty()) {
            _saveState.value = SaveState.Error("반복할 요일을 선택해주세요.")
            return
        }

        val careCategory  = toCareCategory(category)
        val repeatDaysStr = repeatDaysList.sorted().joinToString(",")

        val record = CareRecord(
            id         = id,
            timestamp  = baseTimestamp,
            category   = careCategory,
            value      = value,
            memo       = memo,
            isRepeat   = true,
            repeatDays = repeatDaysStr
        )

        Log.d(TAG, "=== 반복 기록 수정 ===")
        Log.d(TAG, "id: $id, repeatDays: $repeatDaysStr")

        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val updatedId = repository.insert(record)
                Log.d(TAG, "반복 수정 완료 - ID: $updatedId")
                _saveState.value = SaveState.Success(updatedId)
            } catch (e: Exception) {
                Log.e(TAG, "반복 수정 실패: ${e.message}", e)
                _saveState.value = SaveState.Error(e.message ?: "수정 중 오류 발생")
            }
        }
    }

    // ─── 공통 유틸 ─────────────────────────────────────
    private fun toCareCategory(category: String): CareCategory {
        return runCatching {
            CareCategory.valueOf(category.uppercase())
        }.getOrElse {
            Log.w(TAG, "알 수 없는 카테고리: $category → OTHER")
            CareCategory.OTHER
        }
    }

    fun getValueHistory(category: String): Flow<List<String>> {
        val careCategory = toCareCategory(category)
        return repository.getValueHistoryByCategory(careCategory)
    }

    fun getDefaultValue(context: Context, category: String): String {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return pref.getString(category, getHardcodedDefault(category))
            ?: getHardcodedDefault(category)
    }

    fun saveDefaultValue(context: Context, category: String, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(category, value)
            .apply()
        Log.d(TAG, "기본값 저장 - category: '$category', value: '$value'")
    }

    fun getHardcodedDefault(category: String): String {
        return when (category.uppercase()) {
            "MEDICATION"  -> "1정"
            "SLEEP"       -> "8시간"
            "MEAL"        -> "200g"
            "EXCRETION"   -> "1회"
            "TEMPERATURE" -> "36.5°C"
            else          -> ""
        }
    }
}
