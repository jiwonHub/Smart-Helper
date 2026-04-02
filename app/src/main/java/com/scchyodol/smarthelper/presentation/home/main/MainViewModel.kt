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
import com.scchyodol.smarthelper.data.model.ScheduleItem
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
import java.text.SimpleDateFormat
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

    // ─── CareRecord Repository (공용) ────────────────
    private val careRecordRepository: CareRecordRepository by lazy {
        val dao = AppDatabase.getInstance(application).careRecordDao()
        CareRecordRepository(dao)
    }

    // ─── 다음 할 일 ──────────────────────────────────
    private val _nextTask = MutableStateFlow<CareRecord?>(null)
    val nextTask: StateFlow<CareRecord?> = _nextTask

    private val _countdown = MutableStateFlow<String>("--")
    val countdown: StateFlow<String> = _countdown

    private var countdownJob: Job? = null

    // ─── 캘린더 ──────────────────────────────────────
    // CalendarFragment에서 현재 보고 있는 연/월
    private val _calendarYear  = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _calendarMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))

    // 날짜(일) → ScheduleItem 리스트 맵
    private val _scheduleMap = MutableStateFlow<Map<Int, List<ScheduleItem>>>(emptyMap())
    val scheduleMap: StateFlow<Map<Int, List<ScheduleItem>>> = _scheduleMap

    // 현재 캘린더 로드 Job
    private var calendarLoadJob: Job? = null

    // ─── init ────────────────────────────────────────
    init {
        loadCurrentUser()
        fetchWeather()
        startPeriodicWeatherUpdates()
        loadNextTask()
        // 초기 캘린더 데이터 로드
        loadCalendarMonth(
            _calendarYear.value,
            _calendarMonth.value
        )
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
                    delay(delay)
                    fetchWeather()
                } catch (e: CancellationException) {
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
        fetchWeather()
    }

    // ─── 다음 할 일 메서드 ────────────────────────────
    private fun loadNextTask() {
        viewModelScope.launch {
            try {
                careRecordRepository.getAll().collect { records ->
                    val now = System.currentTimeMillis()

                    // ✅ 일반 레코드: timestamp가 미래인 것
                    val nextNormal = records
                        .filter { !it.isRepeat && it.timestamp > now }
                        .minByOrNull { it.timestamp }

                    // ✅ 반복 레코드: repeatDays 기반으로 다음 발생 시각 계산
                    val nextRepeatPair = records
                        .filter { it.isRepeat && it.repeatDays.isNotBlank() }
                        .mapNotNull { record ->
                            val nextOccurrence = getNextOccurrence(record, now)
                            if (nextOccurrence != null) Pair(record, nextOccurrence) else null
                        }
                        .minByOrNull { it.second }

                    // 둘 중 더 가까운 것 선택
                    val next: CareRecord?
                    val nextTimestamp: Long

                    when {
                        nextNormal == null && nextRepeatPair == null -> {
                            next          = null
                            nextTimestamp = 0L
                        }
                        nextNormal == null -> {
                            next          = nextRepeatPair!!.first
                            nextTimestamp = nextRepeatPair.second
                        }
                        nextRepeatPair == null -> {
                            next          = nextNormal
                            nextTimestamp = nextNormal.timestamp
                        }
                        else -> {
                            if (nextNormal.timestamp <= nextRepeatPair.second) {
                                next          = nextNormal
                                nextTimestamp = nextNormal.timestamp
                            } else {
                                next          = nextRepeatPair.first
                                nextTimestamp = nextRepeatPair.second
                            }
                        }
                    }

                    _nextTask.value = next

                    if (next != null) {
                        startCountdown(nextTimestamp)
                    } else {
                        countdownJob?.cancel()
                        _countdown.value = "✏️ 새로운 일정을 등록해주세요!"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 다음 할 일 조회 실패: ${e.message}", e)
            }
        }
    }

    private fun getNextOccurrence(record: CareRecord, now: Long): Long? {
        val dayOfWeekMap = mapOf(
            0 to Calendar.MONDAY,
            1 to Calendar.TUESDAY,
            2 to Calendar.WEDNESDAY,
            3 to Calendar.THURSDAY,
            4 to Calendar.FRIDAY,
            5 to Calendar.SATURDAY,
            6 to Calendar.SUNDAY
        )

        val repeatDayInts = record.repeatDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }

        val targetCalendarDays = repeatDayInts
            .mapNotNull { dayOfWeekMap[it] }
            .toSet()

        if (targetCalendarDays.isEmpty()) return null

        // 기준 시:분 추출
        val baseCal    = Calendar.getInstance().apply { timeInMillis = record.timestamp }
        val baseHour   = baseCal.get(Calendar.HOUR_OF_DAY)
        val baseMinute = baseCal.get(Calendar.MINUTE)

        // 오늘부터 7일 이내에서 다음 발생일 탐색
        val iterCal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        repeat(7) {
            val dayOfWeek = iterCal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek in targetCalendarDays) {
                val targetCal = Calendar.getInstance().apply {
                    timeInMillis = iterCal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, baseHour)
                    set(Calendar.MINUTE, baseMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                // 현재 시각 이후이고, 반복 시작 시각 이후인 것만
                if (targetCal.timeInMillis > now && targetCal.timeInMillis >= record.timestamp) {
                    return targetCal.timeInMillis
                }
            }
            iterCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return null
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

    // ─── 캘린더 메서드 ────────────────────────────────

    /**
     * CalendarFragment에서 호출 - 이전/다음 달 이동 시
     * Room Flow가 연결된 상태에서 월만 바꿔서 다시 collect
     */
    fun loadCalendarMonth(year: Int, month: Int) {
        calendarLoadJob?.cancel()

        _calendarYear.value  = year
        _calendarMonth.value = month

        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }

        val startOfMonth = startCal.timeInMillis
        val endOfMonth   = endCal.timeInMillis

        Log.d(TAG, "loadCalendarMonth - year=$year, month=${month + 1}")

        calendarLoadJob = viewModelScope.launch {
            // 일반 레코드 + 반복 레코드 동시에 collect
            careRecordRepository.getRecordsByMonth(startOfMonth, endOfMonth)
                .collect { normalRecords ->
                    // 반복 레코드는 별도 1회 조회
                    val repeatRecords = careRecordRepository.getRepeatRecordsOnce()

                    Log.d(TAG, "일반 기록: ${normalRecords.size}건, 반복 기록: ${repeatRecords.size}건")

                    _scheduleMap.value = buildScheduleMap(
                        normalRecords  = normalRecords,
                        repeatRecords  = repeatRecords,
                        year           = year,
                        month          = month,
                        startOfMonth   = startOfMonth,
                        endOfMonth     = endOfMonth
                    )
                }
        }
    }
    private fun buildScheduleMap(
        normalRecords  : List<CareRecord>,
        repeatRecords  : List<CareRecord>,
        year           : Int,
        month          : Int,
        startOfMonth   : Long,
        endOfMonth     : Long
    ): Map<Int, List<ScheduleItem>> {

        val now        = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
        val dayCal     = Calendar.getInstance()

        // 0=월~6=일 → Calendar.DAY_OF_WEEK 매핑
        val dayOfWeekMap = mapOf(
            0 to Calendar.MONDAY,
            1 to Calendar.TUESDAY,
            2 to Calendar.WEDNESDAY,
            3 to Calendar.THURSDAY,
            4 to Calendar.FRIDAY,
            5 to Calendar.SATURDAY,
            6 to Calendar.SUNDAY
        )

        val resultMap = mutableMapOf<Int, MutableList<ScheduleItem>>()

        // ── 일반 레코드 처리 ─────────────────────────────
        normalRecords.forEach { record ->
            dayCal.timeInMillis = record.timestamp
            val day = dayCal.get(Calendar.DAY_OF_MONTH)
            resultMap.getOrPut(day) { mutableListOf() }.add(
                ScheduleItem(
                    time   = timeFormat.format(record.timestamp),
                    label  = buildLabel(record),
                    isDone = record.timestamp <= now
                )
            )
        }

        // ── 반복 레코드 동적 펼치기 ──────────────────────
        // 이번 달의 모든 날짜를 순회하면서 해당 요일 맞으면 추가
        repeatRecords.forEach { record ->
            if (record.repeatDays.isBlank()) return@forEach

            // repeatDays 파싱 (예: "0,2,4" → [0,2,4])
            val repeatDayInts = record.repeatDays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }

            // Calendar.DAY_OF_WEEK Set으로 변환
            val targetCalendarDays = repeatDayInts
                .mapNotNull { dayOfWeekMap[it] }
                .toSet()

            // 기준 시:분 추출
            val baseCal    = Calendar.getInstance().apply { timeInMillis = record.timestamp }
            val baseHour   = baseCal.get(Calendar.HOUR_OF_DAY)
            val baseMinute = baseCal.get(Calendar.MINUTE)

            // 이번 달 1일~말일 순회
            val iterCal = Calendar.getInstance().apply {
                timeInMillis = startOfMonth
            }

            while (iterCal.timeInMillis < endOfMonth) {
                val dayOfWeek = iterCal.get(Calendar.DAY_OF_WEEK)

                if (dayOfWeek in targetCalendarDays) {
                    // 해당 날짜에 기준 시:분 적용
                    val targetCal = Calendar.getInstance().apply {
                        timeInMillis = iterCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, baseHour)
                        set(Calendar.MINUTE, baseMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val targetTimestamp = targetCal.timeInMillis

                    // 반복 시작 기준 시각(record.timestamp) 이후인 날짜만 표시
                    if (targetTimestamp >= record.timestamp) {
                        val day = iterCal.get(Calendar.DAY_OF_MONTH)
                        resultMap.getOrPut(day) { mutableListOf() }.add(
                            ScheduleItem(
                                time   = timeFormat.format(targetTimestamp),
                                label  = buildLabel(record),
                                isDone = targetTimestamp <= now
                            )
                        )
                    }
                }

                iterCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return resultMap
    }

    private fun buildLabel(record: CareRecord): String {
        val categoryName = record.category.displayName
        return if (record.value.isNullOrBlank()) categoryName
        else "$categoryName - ${record.value}"
    }
    // ─── 정리 ─────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        weatherJob?.cancel()
        periodicWeatherJob?.cancel()
        countdownJob?.cancel()
        calendarLoadJob?.cancel()
    }
}
