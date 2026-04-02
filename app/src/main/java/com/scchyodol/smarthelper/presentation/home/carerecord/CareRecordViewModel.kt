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
        data class Success(val id: Long) : SaveState()
        data class Error(val message: String) : SaveState()
    }

    private val _saveState = MutableLiveData<SaveState>(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    fun saveRecord(
        timestamp : Long,
        category  : String,
        value     : String,
        memo      : String
    ) {
        // CareCategory.name (대문자) 기준으로 변환
        val careCategory = runCatching {
            CareCategory.valueOf(category.uppercase())
        }.getOrElse {
            Log.w(TAG, "알 수 없는 카테고리: $category, OTHER 처리")
            CareCategory.OTHER
        }

        val record = CareRecord(
            timestamp = timestamp,
            category  = careCategory,
            value     = value,
            memo      = memo
        )

        Log.d(TAG, "저장 시도 - category: $careCategory (name=${careCategory.name}), value: $value")

        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val id = repository.insert(record)
                _saveState.value = SaveState.Success(id)
            } catch (e: Exception) {
                Log.e(TAG, "저장 실패: ${e.message}", e)
                _saveState.value = SaveState.Error(e.message ?: "저장 중 오류 발생")
            }
        }
    }

    fun getValueHistory(category: String): Flow<List<String>> {
        // String -> CareCategory enum 변환 후 쿼리
        val careCategory = runCatching {
            CareCategory.valueOf(category.uppercase())
        }.getOrElse {
            Log.w(TAG, "getValueHistory: 알 수 없는 카테고리: $category, OTHER 처리")
            CareCategory.OTHER
        }

        Log.d(TAG, "getValueHistory 호출 - category string: '$category', enum: $careCategory")
        return repository.getValueHistoryByCategory(careCategory)
    }

    // SharedPreferences 기반 기본값 조회
    fun getDefaultValue(context: Context, category: String): String {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return pref.getString(category, getHardcodedDefault(category))
            ?: getHardcodedDefault(category)
    }

    // SharedPreferences 기반 기본값 저장
    fun saveDefaultValue(context: Context, category: String, value: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(category, value)
            .apply()
        Log.d(TAG, "기본값 저장 완료 - category: '$category', value: '$value'")
    }

    // 앱 최초 실행 시 하드코딩 초기값 (CareCategory.name 기준)
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
