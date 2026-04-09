package com.scchyodol.smarthelper.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.scchyodol.smarthelper.data.model.CareRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {

    private const val TAG = "REMINDER_DEBUG"

    const val EXTRA_RECORD_ID       = "extra_record_id"
    const val EXTRA_RECORD_CATEGORY = "extra_record_category"
    const val EXTRA_RECORD_VALUE    = "extra_record_value"
    const val EXTRA_RECORD_TIME     = "extra_record_time"
    const val EXTRA_IS_PRE_ALARM    = "extra_is_pre_alarm"

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)

    fun scheduleNearest(context: Context, records: List<CareRecord>, onScheduled: ((String) -> Unit)? = null) {
        Log.d(TAG, "=== scheduleNearest 시작 ===")
        Log.d(TAG, "전체 레코드 수: ${records.size}")

        val now = System.currentTimeMillis()
        Log.d(TAG, "현재 시간: ${fmt.format(now)}")

        val futureRecords = records.filter { !it.isRepeat && it.timestamp > now }
        Log.d(TAG, "미래 일정 수: ${futureRecords.size}")

        futureRecords.forEach { record ->
            Log.d(TAG, "  - id=${record.id}, time=${fmt.format(record.timestamp)}, cat=${record.category}")
        }

        val nearest = futureRecords.minByOrNull { it.timestamp }

        if (nearest == null) {
            Log.w(TAG, "미래 일정 없음 - 알림 등록 안함")
            onScheduled?.invoke("등록할 일정이 없습니다.")
            return
        }

        Log.d(TAG, "가장 가까운 일정 선택:")
        Log.d(TAG, "  - id=${nearest.id}")
        Log.d(TAG, "  - timestamp=${nearest.timestamp} (${fmt.format(nearest.timestamp)})")
        Log.d(TAG, "  - category=${nearest.category}")
        Log.d(TAG, "  - value='${nearest.value}'")

        val preAlarmTime = nearest.timestamp - 10 * 60 * 1000L
        Log.d(TAG, "10분 전 시간: ${fmt.format(preAlarmTime)}")

        if (preAlarmTime > now) {
            Log.d(TAG, "10분 전 알림 등록 시도")
            scheduleAlarm(context, nearest, preAlarmTime, isPreAlarm = true)
        } else {
            Log.w(TAG, "10분 전 시간이 이미 과거 - 10분 전 알림 건너뜀")
        }

        Log.d(TAG, "정시 알림 등록 시도")
        scheduleAlarm(context, nearest, nearest.timestamp, isPreAlarm = false)

        Log.d(TAG, "=== scheduleNearest 완료 ===")

        // 🔥 알림 등록 완료 메시지
        val timeStr = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(nearest.timestamp)
        onScheduled?.invoke("${timeStr} ${nearest.category.displayName} 알림이 등록되었습니다.")
    }

    private fun scheduleAlarm(
        context: Context,
        record: CareRecord,
        triggerAtMillis: Long,
        isPreAlarm: Boolean
    ) {
        val type = if (isPreAlarm) "10분전" else "정시"
        Log.d(TAG, "[$type] scheduleAlarm 시작")

        val exactTriggerTime = Calendar.getInstance().apply {
            timeInMillis = triggerAtMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val exactRecordTime = Calendar.getInstance().apply {
            timeInMillis = record.timestamp
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_RECORD_ID,       record.id)
            putExtra(EXTRA_RECORD_CATEGORY, record.category.name)
            putExtra(EXTRA_RECORD_VALUE,    record.value ?: "")
            putExtra(EXTRA_RECORD_TIME,     exactRecordTime)
            putExtra(EXTRA_IS_PRE_ALARM,    isPreAlarm)
        }

        val requestCode = if (isPreAlarm) (record.id * 10 + 1).toInt()
        else            (record.id * 10 + 2).toInt()

        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // 🔥 정확한 트리거 시간 사용
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, exactTriggerTime, pending)
            Log.d(TAG, "[$type] ✅ 알림 등록 성공!")
            Log.d(TAG, "[$type]   - 원본: ${fmt.format(triggerAtMillis)}")
            Log.d(TAG, "[$type]   - 수정: ${fmt.format(exactTriggerTime)}")
        } catch (e: Exception) {
            Log.e(TAG, "[$type] ❌ 알림 등록 실패: ${e.message}", e)
        }
    }


    fun cancelAllAlarms(context: Context) {
        Log.d(TAG, "=== cancelAllAlarms 시작 ===")

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            val registeredCodes = prefs.getStringSet("registered_codes", emptySet()) ?: emptySet()

            Log.d(TAG, "등록된 알림 코드 수: ${registeredCodes.size}")

            registeredCodes.forEach { codeStr ->
                try {
                    val code = codeStr.toInt()
                    val intent = Intent(context, ReminderBroadcastReceiver::class.java)
                    val pending = PendingIntent.getBroadcast(
                        context, code, intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )

                    pending?.let {
                        alarmManager.cancel(it)
                        Log.d(TAG, "✅ 알림 취소 완료: requestCode=$code")
                    } ?: Log.d(TAG, "⚠️ 취소할 알림 없음: requestCode=$code")

                } catch (e: Exception) {
                    Log.e(TAG, "알림 취소 실패: $codeStr - ${e.message}")
                }
            }

            // 등록 목록 초기화
            prefs.edit().remove("registered_codes").apply()
            Log.d(TAG, "등록된 알림 목록 초기화 완료")

        } catch (e: Exception) {
            Log.e(TAG, "❌ cancelAllAlarms 실패: ${e.message}", e)
        }

        Log.d(TAG, "=== cancelAllAlarms 완료 ===")
    }

    fun cancelAlarms(context: Context, record: CareRecord) {
        Log.d(TAG, "cancelAlarms 시작 - recordId: ${record.id}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        listOf(
            (record.id * 10 + 1).toInt(),
            (record.id * 10 + 2).toInt()
        ).forEach { code ->
            Log.d(TAG, "알림 취소 시도 - requestCode: $code")

            val intent = Intent(context, ReminderBroadcastReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pending != null) {
                alarmManager.cancel(pending)
                Log.d(TAG, "✅ 알림 취소 완료 - requestCode: $code")
            } else {
                Log.d(TAG, "⚠️ 취소할 알림 없음 - requestCode: $code")
            }
        }
    }
}
