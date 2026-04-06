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

    private val _countdown = MutableStateFlow<String>("--")
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
            try {
                careRecordRepository.getAll().collect { records ->
                    val now = System.currentTimeMillis()

                    val nextNormal = records
                        .filter { !it.isRepeat && it.timestamp > now }
                        .minByOrNull { it.timestamp }

                    val nextRepeatPair = records
                        .filter { it.isRepeat && it.repeatDays.isNotBlank() }
                        .mapNotNull { record ->
                            val nextOccurrence = getNextOccurrence(record, now)
                            if (nextOccurrence != null) Pair(record, nextOccurrence) else null
                        }
                        .minByOrNull { it.second }

                    val next: CareRecord?
                    val nextTimestamp: Long

                    when {
                        nextNormal == null && nextRepeatPair == null -> {
                            next          = null
                            nextTimestamp = 0L
                        }
                        nextNormal == null -> {
                            next          = nextRepeatPair!!.first.copy(timestamp = nextRepeatPair.second)
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
                                next          = nextRepeatPair.first.copy(timestamp = nextRepeatPair.second)
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
                                isRepeat = true
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

        moodLoadJob = viewModelScope.launch {
            try {
                userMoodRepository.getByDateRange(
                    startMillis = startCal.timeInMillis,
                    endMillis   = endCal.timeInMillis
                ).collect { moodList ->
                    val map    = mutableMapOf<Int, UserMood>()
                    val dayCal = Calendar.getInstance()
                    moodList.forEach { mood ->
                        dayCal.timeInMillis = mood.date
                        map[dayCal.get(Calendar.DAY_OF_MONTH)] = mood
                    }
                    _monthMoodMap.value = map
                    Log.d(TAG, "월별 무드 로드 완료: ${map.size}건")
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
                val id = userMoodRepository.insert(mood = mood, date = getTodayStartMillis())
                _todayMood.value = mood
                _moodSaveState.value = MoodSaveState.Success
                Log.d(TAG, "무드 저장 성공 - ID: $id, mood: $mood")
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
                // 현재 캘린더에 표시 중인 연도/월 기준
                val year  = _calendarYear.value
                val month = _calendarMonth.value

                // DB에서 전체 레코드 조회 (반복 포함)
                // → ReportGenerator 내부에서 이번달 데이터만 필터 + 반복 확장
                val allRecords = careRecordRepository.getAllOnce()

                Log.d(TAG, "PDF 출력 대상 - ${year}년 ${month + 1}월, 전체 레코드: ${allRecords.size}건")

                if (allRecords.isEmpty()) {
                    _exportState.value = ExportState.Error("저장된 기록이 없습니다.")
                    return@launch
                }

                val file = withContext(Dispatchers.IO) {
                    reportGenerator.generateMonthlyPDF(
                        context = getApplication(),
                        records = allRecords,
                        year    = year,
                        month   = month
                    )
                }

                if (file != null) {
                    _exportState.value = ExportState.Success(file)
                } else {
                    _exportState.value = ExportState.Error("이번달 기록이 없습니다.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "PDF 내보내기 실패: ${e.message}", e)
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


