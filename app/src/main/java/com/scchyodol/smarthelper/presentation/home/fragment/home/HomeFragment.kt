package com.scchyodol.smarthelper.presentation.home.fragment.home

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.data.model.CareCategory
import com.scchyodol.smarthelper.data.model.CareRecord
import com.scchyodol.smarthelper.data.model.CategoryInfo
import com.scchyodol.smarthelper.presentation.home.carerecord.CareRecordActivity
import com.scchyodol.smarthelper.presentation.home.main.DeleteState
import com.scchyodol.smarthelper.presentation.home.main.MainViewModel
import com.scchyodol.smarthelper.util.Result
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private val viewModel: MainViewModel by activityViewModels()

    // 현재 표시 중인 nextTask를 저장해두기 위한 변수
    private var currentNextTask: CareRecord? = null
    private var currentDialog: Dialog? = null
    private var lastWeatherFetchTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        observeWeather(view)
        observeNextTask(view)
        observeDeleteState()
        return view
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        // 5분 지났을 때만 재요청
        if (now - lastWeatherFetchTime > 10 * 60 * 1000L) {
            lastWeatherFetchTime = now
            viewModel.refreshWeatherManually()
        }
    }


    // ─── 날씨 ──────────────────────────────────────────────────────────────
    private fun observeWeather(view: View) {
        val tvTemp  = view.findViewById<TextView>(R.id.tvWeatherSummary)
        val tvHumid = view.findViewById<TextView>(R.id.tvHumiditySummary)

        tvTemp.text  = "--"
        tvHumid.text = "--"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherState.collect { result ->
                    when (result) {
                        is Result.Loading -> Log.d(TAG, "날씨 로딩 중...")
                        is Result.Success -> {
                            val data = result.data
                            tvTemp.text  = "${data.current.temperature}${data.currentUnits.temperatureUnit}"
                            tvHumid.text = "${data.current.humidity}${data.currentUnits.humidityUnit}"
                        }
                        is Result.Error -> {
                            tvTemp.text  = "--"
                            tvHumid.text = "--"
                            Log.e(TAG, "날씨 오류: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    private fun getSmartCountdownFormat(targetTimestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = targetTimestamp - now

        return when {
            diff <= 0 -> "지금 할 시간입니다!"

            diff < 60 * 1000 -> {
                val seconds = (diff / 1000).toInt()
                "${seconds}초 남음"
            }

            diff < 60 * 60 * 1000 -> {
                val minutes = (diff / (60 * 1000)).toInt()
                val seconds = ((diff % (60 * 1000)) / 1000).toInt()
                "${minutes}분 ${seconds}초 남음"
            }

            diff < 24 * 60 * 60 * 1000 -> {
                val hours = (diff / (60 * 60 * 1000)).toInt()
                val minutes = ((diff % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                val seconds = ((diff % (60 * 1000)) / 1000).toInt()
                "${hours}시간 ${minutes}분 ${seconds}초 남음"
            }

            else -> {
                val days = (diff / (24 * 60 * 60 * 1000)).toInt()
                val hours = ((diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)).toInt()
                val minutes = ((diff % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                val seconds = ((diff % (60 * 1000)) / 1000).toInt()
                "${days}일 후 ${hours}시간 ${minutes}분 ${seconds}초"
            }
        }
    }

    // ─── 다음 할 일 ────────────────────────────────────────────────────────
    private fun observeNextTask(view: View) {
        val cardNextTask    = view.findViewById<View>(R.id.cardNextTask)
        val tvNextTaskLabel = view.findViewById<TextView>(R.id.tvNextTaskLabel)
        val tvCountdown     = view.findViewById<TextView>(R.id.tvCountdown)

        tvNextTaskLabel.text = "불러오는 중..."
        tvCountdown.text     = "--"

        // nextTask 상태 수집
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nextTask.collect { record ->
                    currentNextTask = record

                    if (record != null) {
                        val categoryStr = getCategoryLabel(record.category)
                        val timeStr = SimpleDateFormat(
                            "M월 d일 a hh:mm", Locale.KOREA
                        ).format(Date(record.timestamp))

                        tvNextTaskLabel.text = "$timeStr · $categoryStr"
                        Log.d(TAG, "다음 할 일: $timeStr / $categoryStr")

                        // 클릭 리스너 활성화
                        cardNextTask.isClickable = true
                        cardNextTask.setOnClickListener {
                            showNextTaskDetailDialog(record)
                        }
                    } else {
                        tvNextTaskLabel.text = "다음 스케줄이 없습니다"
                        tvCountdown.text     = "새로운 일정을 등록해주세요!"

                        // 일정 없으면 클릭 비활성화
                        cardNextTask.isClickable = false
                        cardNextTask.setOnClickListener(null)
                    }
                }
            }
        }

        // 카운트다운 수집
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.countdown.collect { text ->
                    tvCountdown.text = if (currentNextTask != null) {
                        getSmartCountdownFormat(currentNextTask!!.timestamp)
                    } else {
                        text  // 일정 없으면 기본 메시지
                    }
                }
            }
        }
    }

    // ─── 삭제 상태 관찰 ────────────────────────────────────────────────────
    private fun observeDeleteState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteState.collect { state ->
                    when (state) {
                        is DeleteState.Success -> {
                            // 다이얼로그 닫기
                            currentDialog?.dismiss()
                            currentDialog = null
                            viewModel.resetDeleteState()
                            Toast.makeText(requireContext(), "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                        is DeleteState.Error -> {
                            viewModel.resetDeleteState()
                            Toast.makeText(
                                requireContext(),
                                "삭제 실패: ${state.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    // ─── 다음 할 일 디테일 다이얼로그 ─────────────────────────────────────
    private fun showNextTaskDetailDialog(record: CareRecord) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_date_detail)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        currentDialog = dialog

        val tvDialogDate   = dialog.findViewById<TextView>(R.id.tvDialogDate)
        val btnDialogClose = dialog.findViewById<ImageView>(R.id.btnDialogClose)
        val btnDetailClose = dialog.findViewById<Button>(R.id.btnDetailClose)
        val rvDetailSchedule = dialog.findViewById<RecyclerView>(R.id.rvDetailSchedule)
        val tvDetailEmpty    = dialog.findViewById<TextView>(R.id.tvDetailEmpty)

        val dateStr = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(record.timestamp))
        tvDialogDate.text = "$dateStr 다음 일정"

        rvDetailSchedule.layoutManager = LinearLayoutManager(requireContext())

        val itemView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_schedule_detail, null, false)

        bindDetailItem(itemView, record, dialog)

        rvDetailSchedule.visibility = View.GONE
        val parent = rvDetailSchedule.parent as ViewGroup
        val index = parent.indexOfChild(rvDetailSchedule) + 1
        parent.addView(itemView, index)

        tvDetailEmpty.visibility = View.GONE

        btnDialogClose.setOnClickListener {
            parent.removeView(itemView)
            dialog.dismiss()
        }
        btnDetailClose.setOnClickListener {
            parent.removeView(itemView)
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (itemView.parent != null) {
                (itemView.parent as? ViewGroup)?.removeView(itemView)
            }
            currentDialog = null
        }

        dialog.show()
    }

    // ─── item_schedule_detail 뷰 바인딩 ───────────────────────────────────
    private fun bindDetailItem(itemView: View, record: CareRecord, dialog: Dialog) {
        val tvDetailTime      = itemView.findViewById<TextView>(R.id.tvDetailTime)
        val tvDetailCategory  = itemView.findViewById<TextView>(R.id.tvDetailCategory)
        val tvDetailDoneTag   = itemView.findViewById<TextView>(R.id.tvDetailDoneTag)
        val tvDetailRepeatTag = itemView.findViewById<TextView>(R.id.tvDetailRepeatTag)
        val tvDetailLabel     = itemView.findViewById<TextView>(R.id.tvDetailLabel)
        val tvDetailValue     = itemView.findViewById<TextView>(R.id.tvDetailValue)
        val tvDetailMemo      = itemView.findViewById<TextView>(R.id.tvDetailMemo)
        val btnEditDetail     = itemView.findViewById<ImageView>(R.id.btnEditDetail)
        val btnDeleteDetail   = itemView.findViewById<ImageView>(R.id.btnDeleteDetail)

        // 시간
        val timeStr = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(record.timestamp))
        tvDetailTime.text = timeStr

        // 카테고리
        tvDetailCategory.text = getCategoryLabel(record.category)

        // 완료/예정 태그
        val now = System.currentTimeMillis()
        tvDetailDoneTag.text = if (record.timestamp <= now) "완료" else "예정"

        // 반복 태그
        if (record.isRepeat && record.repeatDays.isNotBlank()) {
            tvDetailRepeatTag.visibility = View.VISIBLE
        } else {
            tvDetailRepeatTag.visibility = View.GONE
        }

        // 일정 이름 (카테고리 표시)
        tvDetailLabel.text = getCategoryLabel(record.category)

        // 수치
        if (!record.value.isNullOrBlank()) {
            tvDetailValue.visibility = View.VISIBLE
            tvDetailValue.text       = "수치: ${record.value}"
        } else {
            tvDetailValue.visibility = View.GONE
        }

        // 메모
        if (!record.memo.isNullOrBlank()) {
            tvDetailMemo.visibility = View.VISIBLE
            tvDetailMemo.text       = record.memo
        } else {
            tvDetailMemo.visibility = View.GONE
        }

        // ── 수정 버튼 ──
        btnEditDetail.setOnClickListener {
            showEditConfirmDialog(record, dialog)
        }

        // ── 삭제 버튼 ──
        btnDeleteDetail.setOnClickListener {
            showDeleteConfirmDialog(record.id)
        }
    }

    // ─── 수정 화면 이동 ────────────────────────────────────────────────────
    private fun navigateToEditRecord(record: CareRecord) {
        val intent = Intent(requireContext(), CareRecordActivity::class.java).apply {
            // ★ 카테고리별 정보 설정 (CalendarFragment와 동일)
            val (title, subtitle, color, category) = getCategoryInfo(record.category)

            putExtra(CareRecordActivity.EXTRA_CATEGORY, category)
            putExtra(CareRecordActivity.EXTRA_TITLE, title)
            putExtra(CareRecordActivity.EXTRA_SUBTITLE, subtitle)
            putExtra(CareRecordActivity.EXTRA_COLOR, color)

            // ★ 수정 모드 데이터 (모든 필드 전달)
            putExtra(CareRecordActivity.EXTRA_IS_EDIT, true)
            putExtra(CareRecordActivity.EXTRA_RECORD_ID, record.id)
            putExtra(CareRecordActivity.EXTRA_VALUE, record.value ?: "")
            putExtra(CareRecordActivity.EXTRA_MEMO, record.memo ?: "")

            // ★ 시간 복원 (timestamp 그대로 사용)
            putExtra(CareRecordActivity.EXTRA_TIMESTAMP, record.timestamp)

            // ★ 반복 일정 정보
            putExtra(CareRecordActivity.EXTRA_IS_REPEAT, record.isRepeat)
            putExtra(CareRecordActivity.EXTRA_REPEAT_DAYS, record.repeatDays ?: "")
        }

        startActivity(intent)
    }

    private fun getCategoryInfo(category: CareCategory): CategoryInfo {
        return when (category.name.uppercase()) {
            "투약", "MEDICATION" -> CategoryInfo(
                "투약", "Medication", "#4A90D9", CareRecordActivity.CATEGORY_MEDICATION
            )
            "수면", "SLEEP" -> CategoryInfo(
                "수면", "Sleep", "#7B52D3", CareRecordActivity.CATEGORY_SLEEP
            )
            "식사", "MEAL" -> CategoryInfo(
                "식사", "Meal", "#F5A623", CareRecordActivity.CATEGORY_MEAL
            )
            "배변", "EXCRETION" -> CategoryInfo(
                "배변", "Excretion", "#4CAF82", CareRecordActivity.CATEGORY_EXCRETION
            )
            "체온", "TEMPERATURE" -> CategoryInfo(
                "체온", "Temperature", "#E84C4C", CareRecordActivity.CATEGORY_TEMPERATURE
            )
            else -> CategoryInfo(
                "기타", "Other", "#26A69A", CareRecordActivity.CATEGORY_OTHER
            )
        }
    }

    // ─── 수정 확인 다이얼로그 ────────────────────────────────────────────────────
    private fun showEditConfirmDialog(record: CareRecord, currentDialog: Dialog) {
        AlertDialog.Builder(requireContext())
            .setTitle("일정 수정")
            .setMessage("이 일정을 수정하시겠습니까?")
            .setPositiveButton("수정") { _, _ ->
                currentDialog.dismiss()
                navigateToEditRecord(record)
            }
            .setNegativeButton("취소", null)
            .show()
    }


    // ─── 삭제 확인 다이얼로그 ─────────────────────────────────────────────
    private fun showDeleteConfirmDialog(recordId: Long) {
        AlertDialog.Builder(requireContext())
            .setTitle("일정 삭제")
            .setMessage("이 일정을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteSchedule(recordId)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun getCategoryLabel(category: CareCategory): String = category.displayName
}
