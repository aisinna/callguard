package com.parentcare.callguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val REQ_FIRST = 1001
    const val REQ_SECOND = 1002

    // 테스트할 땐 1L, 2L 로 바꾸세요
    const val FIRST_MINUTES = 8L
    const val SECOND_MINUTES = 10L

    fun scheduleWarnings(context: Context, number: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        scheduleOne(context, am, REQ_FIRST, AlarmReceiver.TYPE_FIRST, number,
            now + FIRST_MINUTES * 60_000)
        scheduleOne(context, am, REQ_SECOND, AlarmReceiver.TYPE_SECOND, number,
            now + SECOND_MINUTES * 60_000)

        Log.d(TAG, "예약 완료: ${FIRST_MINUTES}분 / ${SECOND_MINUTES}분 후")
    }

    private fun scheduleOne(
        context: Context, am: AlarmManager, requestCode: Int,
        type: String, number: String, triggerAt: Long
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = type
            putExtra(AlarmReceiver.EXTRA_NUMBER, number)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "정확 알람 권한 없음, 일반 알람 사용", e)
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelOne(context, am, REQ_FIRST, AlarmReceiver.TYPE_FIRST)
        cancelOne(context, am, REQ_SECOND, AlarmReceiver.TYPE_SECOND)
        Log.d(TAG, "예약 전부 취소")
    }

    private fun cancelOne(context: Context, am: AlarmManager, requestCode: Int, type: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply { action = type }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        pi.cancel()
    }
}
