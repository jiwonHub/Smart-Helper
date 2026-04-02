package com.scchyodol.smarthelper.presentation.home.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.scchyodol.smarthelper.data.db.AppDatabase
import com.scchyodol.smarthelper.data.model.CareRecord
import com.scchyodol.smarthelper.data.model.User
import com.scchyodol.smarthelper.data.remote.dto.WeatherResponse
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository
import com.scchyodol.smarthelper.data.remote.repository.WeatherRepository
import com.scchyodol.smarthelper.util.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // ─── 유저 ───────────────────────────────────────
    private val auth = FirebaseAuth.getInstance()

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    // ─── 날씨 ───────────────────────────────────────
    private val weatherRepository = WeatherRepository(application.applicationContext)

    private val _weatherState = MutableStateFlow<Result<WeatherResponse>>(Result.Loading)
    val weatherState: StateFlow<Result<WeatherResponse>> = _weatherState

    private var weatherJob: Job? = null
    private var periodicWeatherJob: Job? = null

    // ─── 다음 할 일 ──────────────────────────────────
    private val careRecordRepository: CareRecordRepository by lazy {
        val dao = AppDatabase.getInstance(application).careRecordDao()
        CareRecordRepository(dao)
    }

    private val _nextTask = MutableStateFlow<CareRecord?>(null)
    val nextTask: StateFlow<CareRecord?> = _nextTask

    private val _countdown = MutableStateFlow<String>("--")
    val countdown: StateFlow<String> = _countdown

    private var countdownJob: Job? = null

    // ─── init ────────────────────────────────────────
    init {
        loadCurrentUser()
        fetchWeather()
        startPeriodicWeatherUpdates()
        loadNextTask()                  // ✅ 다음 할 일 시작
    }

    // ─── 유저 메서드 ──────────────────────────────────
    private fun loadCurrentUser() {
        val firebaseUser = auth.currentUser
        _user.value = if (firebaseUser != null) {
            User(
                uid         = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "사용자",
                email       = firebaseUser.email ?: "",
                photoUrl    = firebaseUser.photoUrl?.toString() ?: ""
            )
        } else null
    }

    fun refreshUser() { loadCurrentUser() }

    // ─── 날씨 메서드 ──────────────────────────────────
    fun fetchWeather() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            _weatherState.value = Result.Loading
            try {
                _weatherState.value = weatherRepository.getWeatherByCurrentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "날씨 데이터 로딩 중 오류: ${e.message}")
                _weatherState.value = Result.Error(
                    message   = e.message ?: "날씨 정보를 불러오지 못했습니다.",
                    throwable = e
                )
            }
        }
    }

    private fun startPeriodicWeatherUpdates() {
        periodicWeatherJob = viewModelScope.launch {
            while (true) {
                try {
                    val delay = calculateDelayUntilNextHour()
                    Log.d(TAG, "🕐 다음 정시까지 ${delay}ms 대기")
                    delay(delay)
                    Log.d(TAG, "🕐 정시 도달 → 날씨 갱신")
                    fetchWeather()
                } catch (e: CancellationException) {
                    Log.d(TAG, "🕐 정시 갱신 Job 취소됨")
                    break
                }
            }
        }
    }

    private fun calculateDelayUntilNextHour(): Long {
        val calendar       = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.MINUTE)
        val currentSeconds = calendar.get(Calendar.SECOND)
        val currentMillis  = calendar.get(Calendar.MILLISECOND)

        return if (currentMinutes == 0 && currentSeconds == 0) {
            60 * 60 * 1000L
        } else {
            ((59 - currentMinutes) * 60 + (60 - currentSeconds)) * 1000L + (1000 - currentMillis)
        }
    }

    fun refreshWeatherManually() {
        Log.d(TAG, "🔄 수동 날씨 갱신")
        fetchWeather()
    }

    // ─── 다음 할 일 메서드 ────────────────────────────
    private fun loadNextTask() {
        viewModelScope.launch {
            try {
                careRecordRepository.getAll().collect { records ->
                    val now  = System.currentTimeMillis()
                    val next = records
                        .filter { it.timestamp > now }
                        .minByOrNull { it.timestamp }

                    _nextTask.value = next

                    if (next != null) {
                        Log.d(TAG, "✅ 다음 할 일 - category: ${next.category}, timestamp: ${next.timestamp}")
                        startCountdown(next.timestamp)
                    } else {
                        Log.d(TAG, "⚠️ 미래 기록 없음")
                        countdownJob?.cancel()
                        _countdown.value = "✏️ 새로운 일정을 등록해주세요!"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 다음 할 일 조회 실패: ${e.message}", e)
            }
        }
    }

    private fun startCountdown(targetTimestamp: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val diffMillis = targetTimestamp - System.currentTimeMillis()

                if (diffMillis <= 0) {
                    _countdown.value = "지금!"
                    loadNextTask()
                    break
                }

                _countdown.value = formatCountdown(diffMillis)
                delay(1000L)
            }
        }
    }

    private fun formatCountdown(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours        = totalSeconds / 3600
        val minutes      = (totalSeconds % 3600) / 60
        val seconds      = totalSeconds % 60

        return when {
            hours > 0   -> "${hours}시간 ${minutes}분 ${seconds}초 남음"
            minutes > 0 -> "${minutes}분 ${seconds}초 남음"
            else        -> "${seconds}초 남음"
        }
    }

    // ─── 정리 ─────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        weatherJob?.cancel()
        periodicWeatherJob?.cancel()
        countdownJob?.cancel()
        Log.d(TAG, "MainViewModel 정리 완료")
    }
}
