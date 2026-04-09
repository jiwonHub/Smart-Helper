package com.scchyodol.smarthelper.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ReminderBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "REMINDER_DEBUG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "")
        Log.d(TAG, "🔔 ========== BroadcastReceiver onReceive 시작 ==========")
        Log.d(TAG, "현재 시간: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(System.currentTimeMillis())}")

        val recordId    = intent.getLongExtra(AlarmScheduler.EXTRA_RECORD_ID, -1L)
        val category    = intent.getStringExtra(AlarmScheduler.EXTRA_RECORD_CATEGORY) ?: ""
        val value       = intent.getStringExtra(AlarmScheduler.EXTRA_RECORD_VALUE) ?: ""
        val recordTime  = intent.getLongExtra(AlarmScheduler.EXTRA_RECORD_TIME, 0L)
        val isPreAlarm  = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PRE_ALARM, false)

        Log.d(TAG, "Intent 데이터:")
        Log.d(TAG, "  - recordId: $recordId")
        Log.d(TAG, "  - category: '$category'")
        Log.d(TAG, "  - value: '$value'")
        Log.d(TAG, "  - recordTime: $recordTime")
        Log.d(TAG, "  - isPreAlarm: $isPreAlarm")

        if (recordId == -1L) {
            Log.e(TAG, "❌ 유효하지 않은 recordId - 종료")
            return
        }

        Log.d(TAG, "Context: $context")
        Log.d(TAG, "Application Context: ${context.applicationContext}")

        try {
            val dialogIntent = Intent(context, ReminderDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmScheduler.EXTRA_RECORD_ID,       recordId)
                putExtra(AlarmScheduler.EXTRA_RECORD_CATEGORY, category)
                putExtra(AlarmScheduler.EXTRA_RECORD_VALUE,    value)
                putExtra(AlarmScheduler.EXTRA_RECORD_TIME,     recordTime)
                putExtra(AlarmScheduler.EXTRA_IS_PRE_ALARM,    isPreAlarm)
            }

            Log.d(TAG, "ReminderDialogActivity Intent 생성 완료")
            Log.d(TAG, "Intent flags: ${dialogIntent.flags}")

            context.startActivity(dialogIntent)
            Log.d(TAG, "✅ ReminderDialogActivity 시작 호출 완료")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ReminderDialogActivity 시작 실패: ${e.message}", e)
        }

        Log.d(TAG, "========== BroadcastReceiver onReceive 완료 ==========")
    }
}
