package com.scchyodol.smarthelper.alarm

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import com.scchyodol.smarthelper.R
import com.scchyodol.smarthelper.databinding.DialogReminderCustomBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderDialogActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "REMINDER_DEBUG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "")
        Log.d(TAG, "📱 ========== ReminderDialogActivity onCreate 시작 ==========")

        super.onCreate(savedInstanceState)
        Log.d(TAG, "super.onCreate() 완료")

        try {
            setContentView(R.layout.activity_transparent)
            Log.d(TAG, "setContentView(activity_transparent) 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ setContentView 실패: ${e.message}", e)
            finish()
            return
        }

        val recordId   = intent.getLongExtra(AlarmScheduler.EXTRA_RECORD_ID, -1L)
        val category   = intent.getStringExtra(AlarmScheduler.EXTRA_RECORD_CATEGORY) ?: ""
        val value      = intent.getStringExtra(AlarmScheduler.EXTRA_RECORD_VALUE) ?: ""
        val recordTime = intent.getLongExtra(AlarmScheduler.EXTRA_RECORD_TIME, 0L)
        val isPreAlarm = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PRE_ALARM, false)

        Log.d(TAG, "Intent에서 받은 데이터:")
        Log.d(TAG, "  - recordId: $recordId")
        Log.d(TAG, "  - category: '$category'")
        Log.d(TAG, "  - value: '$value'")
        Log.d(TAG, "  - recordTime: $recordTime")
        Log.d(TAG, "  - isPreAlarm: $isPreAlarm")

        if (recordId == -1L) {
            Log.e(TAG, "❌ 유효하지 않은 recordId - Activity 종료")
            finish()
            return
        }

        Log.d(TAG, "다이얼로그 표시 시작")
        showReminderDialog(recordId, category, value, recordTime, isPreAlarm)
    }

    private fun showReminderDialog(
        recordId: Long,
        category: String,
        value: String,
        recordTime: Long,
        isPreAlarm: Boolean
    ) {
        Log.d(TAG, "showReminderDialog 시작")
        Log.d(TAG, "  - recordId: $recordId")
        Log.d(TAG, "  - category: '$category'")
        Log.d(TAG, "  - isPreAlarm: $isPreAlarm")

        try {
            val dialog = Dialog(this)
            Log.d(TAG, "Dialog 객체 생성 완료")

            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            Log.d(TAG, "FEATURE_NO_TITLE 설정 완료")

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            Log.d(TAG, "투명 배경 설정 완료")

            val binding = DialogReminderCustomBinding.inflate(layoutInflater)
            Log.d(TAG, "DialogReminderCustomBinding inflate 완료")

            dialog.setContentView(binding.root)
            Log.d(TAG, "dialog.setContentView 완료")

            dialog.setCancelable(false)
            Log.d(TAG, "setCancelable(false) 완료")

            // 시간 포맷
            val timeFmt = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)
            val timeText = timeFmt.format(Date(recordTime))
            binding.tvReminderTime.text = timeText
            Log.d(TAG, "시간 설정 완료: $timeText")

            // 카테고리 한글화
            val categoryKorean = categoryToKorean(category)
            binding.tvReminderCategory.text = categoryKorean
            Log.d(TAG, "카테고리 설정 완료: $categoryKorean")

            // 수치 (있을 때만)
            if (value.isNotBlank()) {
                binding.tvReminderValue.visibility = View.VISIBLE
                binding.tvReminderValue.text = "수치: $value"
                Log.d(TAG, "수치 설정 완료: $value")
            } else {
                binding.tvReminderValue.visibility = View.GONE
                Log.d(TAG, "수치 없음 - View GONE 처리")
            }

            if (isPreAlarm) {
                Log.d(TAG, "10분 전 알림 UI 설정")
                binding.tvReminderMessage.text = "일정이 10분 후에 시작됩니다."
                binding.btnReminderConfirm.visibility = View.VISIBLE
                binding.layoutActionButtons.visibility = View.GONE

                binding.btnReminderConfirm.setOnClickListener {
                    Log.d(TAG, "10분 전 알림 - 확인 버튼 클릭")
                    dialog.dismiss()
                    finish()
                }
            } else {
                Log.d(TAG, "정시 알림 UI 설정")
                binding.tvReminderMessage.text = "지금 일정 시간입니다."
                binding.btnReminderConfirm.visibility = View.GONE
                binding.layoutActionButtons.visibility = View.VISIBLE

                binding.btnDone.setOnClickListener {
                    Log.d(TAG, "정시 알림 - 수행 완료 버튼 클릭 (recordId=$recordId)")
                    dialog.dismiss()
                    finish()
                }

                binding.btnNotDone.setOnClickListener {
                    Log.d(TAG, "정시 알림 - 미수행 버튼 클릭 (recordId=$recordId)")
                    dialog.dismiss()
                    finish()
                }
            }

            dialog.setOnDismissListener {
                Log.d(TAG, "dialog.onDismiss 호출 - Activity 종료")
                finish()
            }

            Log.d(TAG, "dialog.show() 호출")
            dialog.show()
            Log.d(TAG, "✅ 다이얼로그 표시 완료!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 다이얼로그 표시 실패: ${e.message}", e)
            finish()
        }
    }

    private fun categoryToKorean(category: String): String {
        val korean = when (category) {
            "MEDICATION"  -> "투약"
            "SLEEP"       -> "수면"
            "EXCRETION"   -> "배변"
            "MEAL"        -> "식사"
            "EXERCISE"    -> "운동"
            "HOSPITAL"    -> "병원"
            else          -> category
        }
        Log.d(TAG, "카테고리 변환: '$category' → '$korean'")
        return korean
    }

    override fun onDestroy() {
        Log.d(TAG, "ReminderDialogActivity onDestroy 호출")
        super.onDestroy()
    }

    override fun onPause() {
        Log.d(TAG, "ReminderDialogActivity onPause 호출")
        super.onPause()
    }

    override fun onResume() {
        Log.d(TAG, "ReminderDialogActivity onResume 호출")
        super.onResume()
    }
}
