package com.scchyodol.smarthelper.presentation.home.carerecord

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.db.AppDatabase
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.remote.repository.CareRecordRepository
import com.scchyodol.smarthelper.databinding.ActivityCareRecordBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CareRecordActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CareRecordActivity"

        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_TITLE    = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_COLOR    = "extra_color"
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_IS_EDIT   = "extra_is_edit"
        const val EXTRA_VALUE     = "extra_value"
        const val EXTRA_MEMO      = "extra_memo"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_IS_REPEAT = "extra_is_repeat"
        const val EXTRA_REPEAT_DAYS = "extra_repeat_days"

        // CareCategory.name 기준으로 통일
        const val CATEGORY_MEDICATION   = "MEDICATION"
        const val CATEGORY_SLEEP        = "SLEEP"
        const val CATEGORY_MEAL         = "MEAL"
        const val CATEGORY_EXCRETION    = "EXCRETION"
        const val CATEGORY_TEMPERATURE  = "TEMPERATURE"
        const val CATEGORY_OTHER        = "OTHER"
    }

    private lateinit var binding: ActivityCareRecordBinding
    private val selectedDays = mutableSetOf<Int>()
    private var categoryColor: Int = Color.parseColor("#4A90D9")

    private val viewModel: CareRecordViewModel by viewModels {
        val dao        = AppDatabase.getInstance(this).careRecordDao()
        val repository = CareRecordRepository(dao)
        CareRecordViewModelFactory(repository)
    }

    private val calendar = Calendar.getInstance()
    private var currentCategory = CATEGORY_OTHER

    private var isEditMode = false
    private var recordId   = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCareRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setCurrentDateTime()
        setupToolbar()
        applyIntentData()
        setupDateTimePickers()
        setupButtons()
        setupRepeatSwitch()
        setupDayButtons()
        initDateTimeDisplay()
        observeViewModel()
    }

    private fun setCurrentDateTime() {
        val now        = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy. MM. dd", Locale.KOREA)
        val timeFormat = SimpleDateFormat("a hh:mm", Locale.KOREA)
        binding.tvSelectedDate.text = dateFormat.format(now.time)
        binding.tvSelectedTime.text = timeFormat.format(now.time)
    }

    private fun initDateTimeDisplay() {
        updateDateDisplay()
        updateTimeDisplay()
    }

    private fun setupDateTimePickers() {
        binding.layoutDate.setOnClickListener {
            // 스위치 ON이면 클릭 무시
            if (binding.switchRepeat.isChecked) return@setOnClickListener

            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    updateDateDisplay()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.layoutTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    updateTimeDisplay()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            ).show()
        }
    }

    private fun updateDateDisplay() {
        val year  = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day   = calendar.get(Calendar.DAY_OF_MONTH)
        binding.tvSelectedDate.text = String.format("%d. %02d. %02d", year, month, day)
    }

    private fun updateTimeDisplay() {
        val hour   = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm   = if (hour < 12) "오전" else "오후"
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else      -> hour
        }
        binding.tvSelectedTime.text = String.format("%s %02d:%02d", amPm, hour12, minute)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun applyIntentData() {
        val title    = intent.getStringExtra(EXTRA_TITLE)    ?: "기록"
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: "Record"
        val color    = intent.getStringExtra(EXTRA_COLOR)    ?: "#4A90D9"
        currentCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: CATEGORY_OTHER

        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT, false)
        recordId   = intent.getLongExtra(EXTRA_RECORD_ID, -1L)

        if (isEditMode) {
            binding.tvHeaderTitle.text = "$title 수정"
            binding.btnSave.text = "수정하기"
            loadEditData()
        } else {
            binding.tvHeaderTitle.text = title
            val defaultValue = viewModel.getDefaultValue(this, currentCategory)
            binding.etValue.setText(defaultValue)
        }

        binding.tvHeaderSubtitle.text = subtitle

        binding.btnSearchHistory.setOnClickListener {
            showValueHistoryDialog()
        }

        val parsedColor = Color.parseColor(color)
        categoryColor = parsedColor

        // ★ 색상 테마 적용 (중복 제거)
        binding.headerBackground.background = ColorDrawable(parsedColor)
        binding.accentBarDateTime.setBackgroundColor(parsedColor)
        binding.accentBarDetail.setBackgroundColor(parsedColor)
        binding.accentBarMemo.setBackgroundColor(parsedColor)
        binding.btnSave.backgroundTintList = ColorStateList.valueOf(parsedColor)

        // 스위치 색상 (기존 그대로 유지)
        val trackStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val trackColors = intArrayOf(
            parsedColor,
            Color.parseColor("#E2E2E2")
        )
        binding.switchRepeat.trackTintList = ColorStateList(trackStates, trackColors)

        val thumbStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            Color.parseColor("#000000"), // 기존 검은색 유지
            Color.parseColor("#000000")  // 기존 검은색 유지
        )
        binding.switchRepeat.thumbTintList = ColorStateList(thumbStates, thumbColors)

        // 아이콘 설정
        val iconRes = when (currentCategory) {
            CATEGORY_MEDICATION  -> R.drawable.pills
            CATEGORY_SLEEP       -> R.drawable.bed
            CATEGORY_MEAL        -> R.drawable.rice
            CATEGORY_EXCRETION   -> R.drawable.diaper
            CATEGORY_TEMPERATURE -> R.drawable.thermometer
            else                 -> R.drawable.document
        }
        binding.ivHeaderIcon.setImageResource(iconRes)
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val value    = binding.etValue.text.toString().trim()
            val memo     = binding.etMemo.text.toString().trim()
            val isRepeat = binding.switchRepeat.isChecked

            if (value.isEmpty() && currentCategory != CATEGORY_OTHER) {
                binding.etValue.error = "수치를 입력해주세요"
                binding.etValue.requestFocus()
                Toast.makeText(this, "수치를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRepeat && selectedDays.isEmpty()) {
                Toast.makeText(this, "반복할 요일을 하나 이상 선택해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val timestamp = calendar.timeInMillis

            if (isEditMode) {
                // ★ 수정 모드
                if (isRepeat) {
                    viewModel.updateRepeatRecord(
                        id             = recordId,
                        baseTimestamp  = timestamp,
                        category       = currentCategory,
                        value          = value,
                        memo           = memo,
                        repeatDaysList = selectedDays.sorted()
                    )
                } else {
                    viewModel.updateRecord(
                        id        = recordId,
                        timestamp = timestamp,
                        category  = currentCategory,
                        value     = value,
                        memo      = memo
                    )
                }
            } else {
                // 기존 저장 로직
                if (isRepeat) {
                    viewModel.saveRepeatRecord(
                        baseTimestamp  = timestamp,
                        category       = currentCategory,
                        value          = value,
                        memo           = memo,
                        repeatDaysList = selectedDays.sorted()
                    )
                } else {
                    viewModel.saveRecord(
                        timestamp = timestamp,
                        category  = currentCategory,
                        value     = value,
                        memo      = memo
                    )
                }
            }
        }

    }

    private fun setupRepeatSwitch() {
        binding.switchRepeat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 요일 선택 뷰로 전환 (애니메이션)
                binding.viewFlipperDate.apply {
                    inAnimation  = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                    outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
                    displayedChild = 1
                }
            } else {
                binding.viewFlipperDate.apply {
                    inAnimation  = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                    outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
                    displayedChild = 0
                }
                // 스위치 OFF시 선택 요일 초기화
                selectedDays.clear()
                resetAllDayButtons()
            }
        }
    }

    // 요일 버튼 셋업
    private fun setupDayButtons() {
        val dayViews = listOf(
            binding.dayMon,  // 0
            binding.dayTue,  // 1
            binding.dayWed,  // 2
            binding.dayThu,  // 3
            binding.dayFri,  // 4
            binding.daySat,  // 5
            binding.daySun   // 6
        )

        dayViews.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                if (selectedDays.contains(index)) {
                    selectedDays.remove(index)
                    setDayUnselected(textView, index)
                } else {
                    selectedDays.add(index)
                    setDaySelected(textView, index)
                }
            }
        }
    }

    private fun loadEditData() {
        val value      = intent.getStringExtra(EXTRA_VALUE) ?: ""
        val memo       = intent.getStringExtra(EXTRA_MEMO) ?: ""
        val timestamp  = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        val isRepeat   = intent.getBooleanExtra(EXTRA_IS_REPEAT, false)
        val repeatDays = intent.getStringExtra(EXTRA_REPEAT_DAYS) ?: ""

        Log.d(TAG, "수정 모드 데이터 로드 - isRepeat: $isRepeat, repeatDays: '$repeatDays'")

        // 값과 메모 설정
        binding.etValue.setText(value)
        binding.etMemo.setText(memo)

        // 시간 설정
        calendar.timeInMillis = timestamp
        updateDateDisplay()
        updateTimeDisplay()
        setupSwitchForEditMode(isRepeat, repeatDays)
    }

    private fun setupSwitchForEditMode(isRepeat: Boolean, repeatDays: String) {
        Log.d(TAG, "스위치 설정 시작 - isRepeat: $isRepeat, repeatDays: '$repeatDays'")

        // ★ 1단계: 리스너 제거
        binding.switchRepeat.setOnCheckedChangeListener(null)

        // ★ 2단계: 무조건 OFF로 시작
        binding.switchRepeat.isChecked = false
        binding.viewFlipperDate.displayedChild = 0

        // ★ 3단계: 요일 데이터 복원 (백그라운드에서)
        selectedDays.clear()
        resetAllDayButtons()

        if (isRepeat && repeatDays.isNotEmpty()) {
            Log.d(TAG, "요일 데이터 복원 시작 - repeatDays: '$repeatDays'")
            val dayIndices = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
            selectedDays.addAll(dayIndices)

            val dayViews = listOf(
                binding.dayMon, binding.dayTue, binding.dayWed,
                binding.dayThu, binding.dayFri, binding.daySat, binding.daySun
            )

            dayIndices.forEach { index ->
                if (index in 0..6) {
                    setDaySelected(dayViews[index], index)
                    Log.d(TAG, "요일 버튼 복원 완료 - index: $index")
                }
            }
        }

        // ★ 4단계: 리스너 재등록
        binding.switchRepeat.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                binding.viewFlipperDate.apply {
                    inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                    outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
                    displayedChild = 1
                }
            } else {
                binding.viewFlipperDate.apply {
                    inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                    outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
                    displayedChild = 0
                }
                selectedDays.clear()
                resetAllDayButtons()
            }
        }

        // ★ 5단계: 반복 일정이면 0.5초 후에 스위치 ON
        if (isRepeat) {
            binding.switchRepeat.postDelayed({
                Log.d(TAG, "0.5초 후 스위치 ON 실행")
                binding.switchRepeat.isChecked = true
                // ViewFlipper는 리스너에서 자동 처리됨
            }, 500)
        }

        Log.d(TAG, "스위치 설정 완료 - 초기 상태: OFF, 반복여부: $isRepeat")
    }
    private fun setDaySelected(view: TextView, index: Int) {
        // 배경 카테고리 컬러로 채우기
        val bg = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(categoryColor)
            setStroke(
                (1f * resources.displayMetrics.density).toInt(),
                categoryColor
            )
        }
        view.background = bg

        // 선택된 상태 텍스트는 항상 흰색
        view.setTextColor(Color.WHITE)
    }

    private fun setDayUnselected(view: TextView, index: Int) {
        view.setBackgroundResource(R.drawable.bg_day_unselected)

        // 원래 색상 복원
        view.setTextColor(
            when (index) {
                5 -> Color.parseColor("#2979FF")  // 토: 파랑
                6 -> Color.parseColor("#F44336")  // 일: 빨강
                else -> Color.parseColor("#1A1A2E")
            }
        )
    }

    private fun resetAllDayButtons() {
        val dayViews = listOf(
            binding.dayMon, binding.dayTue, binding.dayWed,
            binding.dayThu, binding.dayFri, binding.daySat, binding.daySun
        )
        dayViews.forEachIndexed { index, view -> setDayUnselected(view, index) }
    }

    // 선택된 요일 가져오기 (저장 시 사용)
    private fun getSelectedDayString(): String {
        val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")
        return selectedDays.sorted().joinToString(",") { dayNames[it] }
    }

    private fun showValueHistoryDialog() {
        val color          = intent.getStringExtra(EXTRA_COLOR) ?: "#4A90D9"
        val accentColor    = Color.parseColor(color)
        val currentDefault = viewModel.getDefaultValue(this, currentCategory)

        Log.d(TAG, "다이얼로그 열기 - category: '$currentCategory', currentDefault: '$currentDefault'")

        val historyFlow = viewModel.getValueHistory(currentCategory)

        val dialog = ValueHistoryDialog(
            category       = currentCategory,
            currentDefault = currentDefault,
            accentColor    = accentColor,
            historyFlow    = historyFlow,
            onConfirm      = { selectedValue ->
                viewModel.saveDefaultValue(this, currentCategory, selectedValue)
                binding.etValue.setText(selectedValue)
                Log.d(TAG, "기본값 변경 완료 - category: '$currentCategory', value: '$selectedValue'")
            }
        )

        dialog.show(supportFragmentManager, "ValueHistoryDialog")
    }

    private fun observeViewModel() {
        viewModel.saveState.observe(this) { state ->
            when (state) {
                is CareRecordViewModel.SaveState.Idle -> Unit

                is CareRecordViewModel.SaveState.Loading -> {
                    binding.btnSave.isEnabled = false
                    binding.btnSave.text = "저장 중..."
                }

                is CareRecordViewModel.SaveState.Success -> {
                    Log.d(TAG, "저장 성공 - ID: ${state.id}")
                    Toast.makeText(this, "저장 완료!", Toast.LENGTH_SHORT).show()
                    finish()
                }

                is CareRecordViewModel.SaveState.Error -> {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = "저장하기"
                    Log.e(TAG, "저장 실패: ${state.message}")
                    Toast.makeText(this, "저장 실패: ${state.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
