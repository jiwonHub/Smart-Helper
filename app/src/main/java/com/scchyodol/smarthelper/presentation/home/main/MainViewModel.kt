package com.scchyodol.smarthelper.presentation.home.main

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.scchyodol.smarthelper.alarm.AlarmScheduler
import com.scchyodol.smarthelper.data.db.AppDatabase
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.model.CareRecord
import com.scchyodol.smarthelper.data.model.Mood
import com.scchyodol.smarthelper.data.model.ScheduleItem
import com.scchyodol.smarthelper.data.model.User
import com.scchyodol.smarthelper.data.model.UserMood
import com.scchyodol.smarthelper.data.remote.dto.WeatherResponse
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository
import com.scchyodol.smarthelper.data.remote.repository.UserMoodRepository
import com.scchyodol.smarthelper.data.remote.repository.WeatherRepository
import com.scchyodol.smarthelper.util.ReportGenerator
import com.scchyodol.smarthelper.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

sealed class ExportState {
    object Idle    : ExportState()
    object Loading : ExportState()
    data class Success(val file: File) : ExportState()
    data class Error(val message: String) : ExportState()
}

// ─── 무드 ────────────────────────────────────────
sealed class MoodSaveState {
    object Idle    : MoodSaveState()
    object Loading : MoodSaveState()
    object Success : MoodSaveState()
    data class Error(val message: String) : MoodSaveState()
}

sealed class DeleteState {
    object Idle    : DeleteState()
    object Loading : DeleteState()
    object Success : DeleteState()
    data class Error(val message: String) : DeleteState()
}


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

    // ─── CareRecord Repository ────────────────────
    private val careRecordRepository: CareRecordRepository by lazy {
        val dao = AppDatabase.getInstance(application).careRecordDao()
        CareRecordRepository(dao)
    }

    // ─── UserMood Repository ──────────────────────
    private val userMoodRepository: UserMoodRepository by lazy {
        val dao = AppDatabase.getInstance(application).userMoodDao()
        UserMoodRepository(dao)
    }

    // ─── 다음 할 일 ──────────────────────────────────
    private val _nextTask = MutableStateFlow<CareRecord?>(null)
    val nextTask: StateFlow<CareRecord?> = _nextTask

    private val _countdown = MutableStateFlow<String>("✏️ 새로운 일정을 등록해주세요!")
    val countdown: StateFlow<String> = _countdown

    private var countdownJob: Job? = null

    // ─── 캘린더 ──────────────────────────────────────
    private val _calendarYear  = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _calendarMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))

    private val _scheduleMap = MutableStateFlow<Map<Int, List<ScheduleItem>>>(emptyMap())
    val scheduleMap: StateFlow<Map<Int, List<ScheduleItem>>> = _scheduleMap

    private var calendarLoadJob: Job? = null

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    private val reportGenerator = ReportGenerator()

    private val _moodSaveState = MutableStateFlow<MoodSaveState>(MoodSaveState.Idle)
    val moodSaveState: StateFlow<MoodSaveState> = _moodSaveState

    private val _deleteState = MutableStateFlow<DeleteState>(DeleteState.Idle)
    val deleteState: StateFlow<DeleteState> = _deleteState

    // 오늘 무드
    private val _todayMood = MutableStateFlow<Mood?>(null)
    val todayMood: StateFlow<Mood?> = _todayMood

    // 월별 무드 맵 (day → UserMood)
    private val _monthMoodMap = MutableStateFlow<Map<Int, UserMood>>(emptyMap())
    val monthMoodMap: StateFlow<Map<Int, UserMood>> = _monthMoodMap

    private var moodLoadJob: Job? = null

    // ─── init ────────────────────────────────────────
    init {
        loadCurrentUser()
        fetchWeather()
        startPeriodicWeatherUpdates()
        loadNextTask()
        loadCalendarMonth(_calendarYear.value, _calendarMonth.value)
        // 무드
        loadTodayMood()
        val now = Calendar.getInstance()
        loadMonthMoods(now.get(Calendar.YEAR), now.get(Calendar.MONTH))
    }

    fun scheduleNearestReminder(context: Context) {
        viewModelScope.launch {
            val records = careRecordRepository.getAllRecords()
            AlarmScheduler.scheduleNearest(context, records)
        }
    }

    // ─── 유저 ────────────────────────────────────────
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

    // ─── 날씨 ────────────────────────────────────────
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

    fun refreshWeatherManually() { fetchWeather() }

    // ─── 다음 할 일 ──────────────────────────────────
    private fun loadNextTask() {
        viewModelScope.launch {
            careRecordRepository.getAll().collect { records ->
                val now = System.currentTimeMillis()

                records.forEachIndexed { index, record ->
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                    Log.d(
                        "COUNTDOWN_DEBUG", "레코드[$index]: " +
                                "timestamp=${record.timestamp}(${fmt.format(record.timestamp)}), " +
                                "isRepeat=${record.isRepeat}, " +
                                "repeatDays='${record.repeatDays}', " +
                                "미래여부=${record.timestamp > now}"
                    )
                }

                Log.d("COUNTDOWN_DEBUG", "=== loadNextTask 시작 ===")
                Log.d("COUNTDOWN_DEBUG", "전체 레코드 수: ${records.size}")
                Log.d("COUNTDOWN_DEBUG", "현재 시각(now): $now")

                val nextNormal = records
                    .filter { !it.isRepeat && it.timestamp > now }
                    .minByOrNull { it.timestamp }
                    ?.let { record ->
                        val adjustedTime = Calendar.getInstance().apply {
                            timeInMillis = record.timestamp
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        Log.d("COUNTDOWN_DEBUG", "일반 태스크 원본 timestamp: ${record.timestamp}")
                        Log.d("COUNTDOWN_DEBUG", "일반 태스크 조정 timestamp: $adjustedTime")
                        record.copy(timestamp = adjustedTime)
                    }

                Log.d(
                    "COUNTDOWN_DEBUG",
                    "nextNormal: ${nextNormal?.category?.displayName} / ${nextNormal?.timestamp}"
                )

                val nextRepeatPair = records
                    .filter { it.isRepeat && it.repeatDays.isNotBlank() }
                    .mapNotNull { record ->
                        val nextOccurrence = getNextOccurrence(record, now)
                        Log.d(
                            "COUNTDOWN_DEBUG",
                            "반복 태스크 [${record.category.displayName}] nextOccurrence: $nextOccurrence"
                        )
                        if (nextOccurrence != null) Pair(record, nextOccurrence) else null
                    }
                    .minByOrNull { it.second }

                Log.d(
                    "COUNTDOWN_DEBUG",
                    "nextRepeatPair: ${nextRepeatPair?.first?.category?.displayName} / ${nextRepeatPair?.second}"
                )

                val next: CareRecord?
                val nextTimestamp: Long

                when {
                    nextNormal == null && nextRepeatPair == null -> {
                        Log.d("COUNTDOWN_DEBUG", "결과: 다음 태스크 없음")
                        next = null
                        nextTimestamp = 0L
                    }

                    nextNormal == null -> {
                        Log.d("COUNTDOWN_DEBUG", "결과: 반복 태스크 선택")
                        next = nextRepeatPair!!.first.copy(timestamp = nextRepeatPair.second)
                        nextTimestamp = nextRepeatPair.second
                    }

                    nextRepeatPair == null -> {
                        Log.d("COUNTDOWN_DEBUG", "결과: 일반 태스크 선택")
                        next = nextNormal
                        nextTimestamp = nextNormal.timestamp
                    }

                    else -> {
                        if (nextNormal.timestamp <= nextRepeatPair.second) {
                            Log.d("COUNTDOWN_DEBUG", "결과: 일반 vs 반복 비교 -> 일반 선택")
                            next = nextNormal
                            nextTimestamp = nextNormal.timestamp
                        } else {
                            Log.d("COUNTDOWN_DEBUG", "결과: 일반 vs 반복 비교 -> 반복 선택")
                            next = nextRepeatPair.first.copy(timestamp = nextRepeatPair.second)
                            nextTimestamp = nextRepeatPair.second
                        }
                    }
                }

                Log.d("COUNTDOWN_DEBUG", "최종 선택된 next: ${next?.category?.displayName}")
                Log.d("COUNTDOWN_DEBUG", "최종 nextTimestamp: $nextTimestamp")
                Log.d("COUNTDOWN_DEBUG", "now와의 차이(ms): ${nextTimestamp - now}")

                _nextTask.value = next

                if (next != null) {
                    startCountdown(next.timestamp)
                } else {
                    countdownJob?.cancel()
                    _countdown.value = "새로운 일정을 등록해주세요!"
                }
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

        val baseCal    = Calendar.getInstance().apply { timeInMillis = record.timestamp }
        val baseHour   = baseCal.get(Calendar.HOUR_OF_DAY)
        val baseMinute = baseCal.get(Calendar.MINUTE)

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

        val exactTargetTime = Calendar.getInstance().apply {
            timeInMillis = targetTimestamp
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        Log.d("COUNTDOWN_DEBUG", "=== startCountdown 시작 ===")
        Log.d("COUNTDOWN_DEBUG", "targetTimestamp 입력값: $targetTimestamp")
        Log.d("COUNTDOWN_DEBUG", "exactTargetTime(초 0처리): $exactTargetTime")
        Log.d("COUNTDOWN_DEBUG", "현재 시각: ${System.currentTimeMillis()}")
        Log.d("COUNTDOWN_DEBUG", "남은 시간(ms): ${exactTargetTime - System.currentTimeMillis()}")

        countdownJob = viewModelScope.launch {
            while (true) {
                val diffMillis = exactTargetTime - System.currentTimeMillis()
                if (diffMillis <= 0) {
                    loadNextTask()
                    break
                }
                val formatted = formatCountdown(diffMillis)
                _countdown.value = formatted
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

    // ─── 캘린더 ──────────────────────────────────────
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
            careRecordRepository.getRecordsByMonth(startOfMonth, endOfMonth)
                .collect { normalRecords ->
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
        normalRecords : List<CareRecord>,
        repeatRecords : List<CareRecord>,
        year          : Int,
        month         : Int,
        startOfMonth  : Long,
        endOfMonth    : Long
    ): Map<Int, List<ScheduleItem>> {

        val now        = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
        val dayCal     = Calendar.getInstance()

        val dayOfWeekMap = mapOf(
            0 to Calendar.MONDAY,    1 to Calendar.TUESDAY,
            2 to Calendar.WEDNESDAY, 3 to Calendar.THURSDAY,
            4 to Calendar.FRIDAY,    5 to Calendar.SATURDAY,
            6 to Calendar.SUNDAY
        )

        val resultMap = mutableMapOf<Int, MutableList<ScheduleItem>>()

        normalRecords.forEach { record ->
            dayCal.timeInMillis = record.timestamp
            val day = dayCal.get(Calendar.DAY_OF_MONTH)
            resultMap.getOrPut(day) { mutableListOf() }.add(
                ScheduleItem(
                    id       = record.id,
                    time     = timeFormat.format(record.timestamp),
                    label    = record.category.displayName,
                    category = record.category.displayName,
                    value    = record.value,
                    memo     = record.memo,
                    isDone   = record.timestamp <= now,
                    isRepeat = false,
                    repeatDays = ""
                )
            )
        }

        repeatRecords.forEach { record ->
            if (record.repeatDays.isBlank()) return@forEach

            val repeatDayInts = record.repeatDays.split(",")
                .mapNotNull { it.trim().toIntOrNull() }

            val targetCalendarDays = repeatDayInts
                .mapNotNull { dayOfWeekMap[it] }
                .toSet()

            val baseCal    = Calendar.getInstance().apply { timeInMillis = record.timestamp }
            val baseHour   = baseCal.get(Calendar.HOUR_OF_DAY)
            val baseMinute = baseCal.get(Calendar.MINUTE)

            val iterCal = Calendar.getInstance().apply { timeInMillis = startOfMonth }

            while (iterCal.timeInMillis < endOfMonth) {
                if (iterCal.get(Calendar.DAY_OF_WEEK) in targetCalendarDays) {
                    val targetCal = Calendar.getInstance().apply {
                        timeInMillis = iterCal.timeInMillis
                        set(Calendar.HOUR_OF_DAY, baseHour)
                        set(Calendar.MINUTE, baseMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val targetTimestamp = targetCal.timeInMillis
                    if (targetTimestamp >= record.timestamp) {
                        val day = iterCal.get(Calendar.DAY_OF_MONTH)
                        resultMap.getOrPut(day) { mutableListOf() }.add(
                            ScheduleItem(
                                id       = record.id,
                                time     = timeFormat.format(targetTimestamp),
                                label    = record.category.displayName,
                                category = record.category.displayName,
                                value    = record.value,
                                memo     = record.memo,
                                isDone   = targetTimestamp <= now,
                                isRepeat = true,
                                repeatDays = record.repeatDays
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

    // ─── 무드 ────────────────────────────────────────
    private fun getTodayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun loadTodayMood() {
        viewModelScope.launch {
            try {
                val existing = userMoodRepository.getByDate(getTodayStartMillis())
                _todayMood.value = existing?.mood
                Log.d(TAG, "오늘 무드 로드: ${existing?.mood}")
            } catch (e: Exception) {
                Log.e(TAG, "오늘 무드 로드 실패: ${e.message}", e)
            }
        }
    }

    fun loadMonthMoods(year: Int, month: Int) {
        moodLoadJob?.cancel()

        val startCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        Log.d(TAG, "=== 월별 무드 로드 시작 ===")
        Log.d(TAG, "연도: $year, 월: ${month + 1}")
        Log.d(TAG, "범위: ${fmt.format(startCal.timeInMillis)} ~ ${fmt.format(endCal.timeInMillis)}")

        moodLoadJob = viewModelScope.launch {
            try {
                userMoodRepository.getByDateRange(
                    startMillis = startCal.timeInMillis,
                    endMillis   = endCal.timeInMillis
                ).collect { moodList ->
                    Log.d(TAG, "DB에서 가져온 무드 데이터: ${moodList.size}건")
                    moodList.forEachIndexed { index, mood ->
                        val cal = Calendar.getInstance().apply { timeInMillis = mood.date }
                        Log.d(TAG, "[$index] ${cal.get(Calendar.DAY_OF_MONTH)}일 - ${mood.mood}")
                    }

                    val map = mutableMapOf<Int, UserMood>()
                    val dayCal = Calendar.getInstance()
                    moodList.forEach { mood ->
                        dayCal.timeInMillis = mood.date
                        val day = dayCal.get(Calendar.DAY_OF_MONTH)
                        map[day] = mood
                        Log.d(TAG, "맵에 추가: ${day}일 → ${mood.mood}")
                    }

                    _monthMoodMap.value = map
                    Log.d(TAG, "최종 월별 무드 맵: ${map.size}건")
                }
            } catch (e: Exception) {
                Log.e(TAG, "월별 무드 로드 실패: ${e.message}", e)
            }
        }
    }


    fun saveMood(mood: Mood) {
        viewModelScope.launch {
            _moodSaveState.value = MoodSaveState.Loading
            try {
                val todayMillis = getTodayStartMillis()
                val id = userMoodRepository.insert(mood = mood, date = getTodayStartMillis())
                _todayMood.value = mood
                _moodSaveState.value = MoodSaveState.Success
                Log.d(TAG, "무드 저장 성공 - ID: $id, mood: $mood")
                val cal = Calendar.getInstance().apply { timeInMillis = todayMillis }
                loadMonthMoods(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            } catch (e: Exception) {
                Log.e(TAG, "무드 저장 실패: ${e.message}", e)
                _moodSaveState.value = MoodSaveState.Error(e.message ?: "저장 중 오류 발생")
            }
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Loading
            try {
                careRecordRepository.deleteById(id)
                careRecordRepository.rescheduleReminder()
                Log.d(TAG, "일정 삭제 완료 - id: $id")

                // 삭제 후 현재 월 캘린더 새로고침
                val year  = _calendarYear.value
                val month = _calendarMonth.value
                loadCalendarMonth(year, month)

                _deleteState.value = DeleteState.Success
            } catch (e: Exception) {
                Log.e(TAG, "일정 삭제 실패 - id: $id, error: ${e.message}", e)
                _deleteState.value = DeleteState.Error(e.message ?: "삭제 중 오류 발생")
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = DeleteState.Idle
    }

    fun exportCurrentMonthToPdf() {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val year = _calendarYear.value
                val month = _calendarMonth.value

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
                val endOfMonth = endCal.timeInMillis

                val allRecords = careRecordRepository.getAllOnce()
                val expandedRecords = mutableListOf<CareRecord>()

                Log.d(TAG, "=== 월간 PDF 데이터 확인 ===")
                Log.d(TAG, "전체 레코드 수: ${allRecords.size}")
                allRecords.forEachIndexed { i, record ->
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                    Log.d(TAG, "[$i] id=${record.id} | category=${record.category} | " +
                            "isRepeat=${record.isRepeat} | repeatDays='${record.repeatDays}' | " +
                            "timestamp=${fmt.format(record.timestamp)}"
                    )
                }

                val repeatOnly = allRecords.filter { it.isRepeat }
                Log.d(TAG, "--- 반복 레코드만 ${repeatOnly.size}건 ---")
                repeatOnly.forEachIndexed { i, record ->
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                    Log.d(TAG, "반복[$i] id=${record.id} | category=${record.category} | " +
                            "repeatDays='${record.repeatDays}' | timestamp=${fmt.format(record.timestamp)}"
                    )
                }
                Log.d(TAG, "===========================")

                val dayOfWeekMap = mapOf(
                    0 to Calendar.MONDAY, 1 to Calendar.TUESDAY,
                    2 to Calendar.WEDNESDAY, 3 to Calendar.THURSDAY,
                    4 to Calendar.FRIDAY, 5 to Calendar.SATURDAY,
                    6 to Calendar.SUNDAY
                )

                // 일반 기록 추가
                allRecords
                    .filter { !it.isRepeat && it.timestamp >= startOfMonth && it.timestamp < endOfMonth }
                    .forEach { expandedRecords.add(it) }

                // ✅ 핵심 수정: 반복 기록 중복 제거 후 확장
                // 동일한 (category + repeatDays + hour + minute) 조합은 가장 오래된(첫 번째) 레코드 하나만 사용
                val uniqueRepeatRecords = allRecords
                    .filter { it.isRepeat && it.repeatDays.isNotBlank() }
                    .groupBy { record ->
                        val baseCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                        val hour = baseCal.get(Calendar.HOUR_OF_DAY)
                        val minute = baseCal.get(Calendar.MINUTE)
                        "${record.category}_${record.repeatDays}_${hour}_${minute}"
                    }
                    .map { (_, records) -> records.minByOrNull { it.timestamp }!! } // 가장 오래된 것 하나만

                uniqueRepeatRecords.forEach { record ->
                    val repeatDayInts = record.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                    val targetCalendarDays = repeatDayInts.mapNotNull { dayOfWeekMap[it] }.toSet()

                    val baseCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                    val baseHour = baseCal.get(Calendar.HOUR_OF_DAY)
                    val baseMinute = baseCal.get(Calendar.MINUTE)

                    val iterCal = Calendar.getInstance().apply { timeInMillis = startOfMonth }

                    while (iterCal.timeInMillis < endOfMonth) {
                        if (iterCal.get(Calendar.DAY_OF_WEEK) in targetCalendarDays) {
                            val targetCal = Calendar.getInstance().apply {
                                timeInMillis = iterCal.timeInMillis
                                set(Calendar.HOUR_OF_DAY, baseHour)
                                set(Calendar.MINUTE, baseMinute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val targetTimestamp = targetCal.timeInMillis

                            if (targetTimestamp >= record.timestamp) {
                                expandedRecords.add(record.copy(timestamp = targetTimestamp))
                            }
                        }
                        iterCal.add(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                expandedRecords.sortBy { it.timestamp }

                Log.d(TAG, "=== 확장된 레코드 상세 ===")
                expandedRecords.forEachIndexed { i, record ->
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                    val cal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                    Log.d(TAG, "확장[$i] ${dayOfMonth}일 ${fmt.format(record.timestamp)} | " +
                            "category=${record.category} | isRepeat=${record.isRepeat}"
                    )
                }
                Log.d(TAG, "=======================")

                Log.d(TAG, "월간 PDF - uniqueRepeat=${uniqueRepeatRecords.size}건, 확장=${expandedRecords.size}건")

                if (expandedRecords.isEmpty()) {
                    _exportState.value = ExportState.Error("이번달 기록이 없습니다.")
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    reportGenerator.generateMonthlyPDF(
                        context = getApplication(),
                        records = expandedRecords,
                        year = year,
                        month = month
                    )
                }

                _exportState.value = if (file != null) ExportState.Success(file)
                else ExportState.Error("PDF 생성 실패")

            } catch (e: Exception) {
                Log.e(TAG, "PDF 내보내기 실패: ${e.message}", e)
                _exportState.value = ExportState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    // ─── 이번주 PDF 내보내기 ──────────────────────────────────────────
    fun exportCurrentWeekToPdf() {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            try {
                val startCal = Calendar.getInstance().apply {
                    val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                    val offset = when (dayOfWeek) {
                        Calendar.SUNDAY -> -6; Calendar.MONDAY -> 0
                        Calendar.TUESDAY -> -1; Calendar.WEDNESDAY -> -2
                        Calendar.THURSDAY -> -3; Calendar.FRIDAY -> -4
                        Calendar.SATURDAY -> -5; else -> 0
                    }
                    add(Calendar.DAY_OF_MONTH, offset)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                    add(Calendar.DAY_OF_MONTH, 7)
                }

                val startOfWeek = startCal.timeInMillis
                val endOfWeek = endCal.timeInMillis

                val allRecords = careRecordRepository.getAllOnce()

                // ⭐ 주간 전용 직접 확장 로직
                val expandedRecords = mutableListOf<CareRecord>()

                val dayOfWeekMap = mapOf(
                    0 to Calendar.MONDAY, 1 to Calendar.TUESDAY,
                    2 to Calendar.WEDNESDAY, 3 to Calendar.THURSDAY,
                    4 to Calendar.FRIDAY, 5 to Calendar.SATURDAY,
                    6 to Calendar.SUNDAY
                )

                // 일반 기록 추가
                val uniqueRepeatRecords = allRecords
                    .filter { it.isRepeat && it.repeatDays.isNotBlank() }
                    .groupBy { record ->
                        val baseCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                        val hour = baseCal.get(Calendar.HOUR_OF_DAY)
                        val minute = baseCal.get(Calendar.MINUTE)
                        "${record.category}_${record.repeatDays}_${hour}_${minute}"
                    }
                    .map { (_, records) -> records.minByOrNull { it.timestamp }!! }

                // 반복 기록 확장
                uniqueRepeatRecords.filter { it.isRepeat && it.repeatDays.isNotBlank() }
                    .forEach { record ->
                        val repeatDayInts = record.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                        val targetCalendarDays = repeatDayInts.mapNotNull { dayOfWeekMap[it] }.toSet()

                        val baseCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
                        val baseHour = baseCal.get(Calendar.HOUR_OF_DAY)
                        val baseMinute = baseCal.get(Calendar.MINUTE)

                        val iterCal = Calendar.getInstance().apply { timeInMillis = startOfWeek }

                        while (iterCal.timeInMillis < endOfWeek) {
                            if (iterCal.get(Calendar.DAY_OF_WEEK) in targetCalendarDays) {
                                val targetCal = Calendar.getInstance().apply {
                                    timeInMillis = iterCal.timeInMillis
                                    set(Calendar.HOUR_OF_DAY, baseHour)
                                    set(Calendar.MINUTE, baseMinute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                val targetTimestamp = targetCal.timeInMillis

                                if (targetTimestamp >= record.timestamp) {
                                    expandedRecords.add(record.copy(timestamp = targetTimestamp))
                                }
                            }
                            iterCal.add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }

                // 시간순 정렬
                expandedRecords.sortBy { it.timestamp }

                Log.d(TAG, "주간 PDF 출력 - 확장된 레코드: ${expandedRecords.size}건")

                if (expandedRecords.isEmpty()) {
                    _exportState.value = ExportState.Error("이번주 기록이 없습니다.")
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    reportGenerator.generateWeeklyPDF(
                        context = getApplication(),
                        records = expandedRecords,
                        startOfWeek = startOfWeek,
                        endOfWeek = endOfWeek
                    )
                }

                if (file != null) {
                    _exportState.value = ExportState.Success(file)
                } else {
                    _exportState.value = ExportState.Error("PDF 생성 실패")
                }

            } catch (e: Exception) {
                Log.e(TAG, "주간 PDF 내보내기 실패: ${e.message}", e)
                _exportState.value = ExportState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // ─── 정리 ─────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        weatherJob?.cancel()
        periodicWeatherJob?.cancel()
        countdownJob?.cancel()
        calendarLoadJob?.cancel()
        moodLoadJob?.cancel()
    }
}


