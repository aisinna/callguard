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

    fun scheduleWarnings(context: Context, number: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        val firstSec = PrefsHelper.getFirstSeconds(context)
        val secondSec = PrefsHelper.getSecondSeconds(context)

        scheduleOne(
            context, am, REQ_FIRST, AlarmReceiver.TYPE_FIRST, number,
            now + firstSec * 1000L, firstSec
        )
        scheduleOne(
            context, am, REQ_SECOND, AlarmReceiver.TYPE_SECOND, number,
            now + secondSec * 1000L, secondSec
        )

        Log.d(TAG, "예약 완료: ${firstSec}초 / ${secondSec}초 후")
    }

    private fun scheduleOne(
        context: Context,
        am: AlarmManager,
        requestCode: Int,
        type: String,
        number: String,
        triggerAt: Long,
        seconds: Int
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = type
            putExtra(AlarmReceiver.EXTRA_NUMBER, number)
            putExtra(AlarmReceiver.EXTRA_SECONDS, seconds)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Log.d(TAG, "정확 알람 예약")
                } else {
                    // 권한이 없으면 알람시계 방식으로 우회 (정확도 유지)
                    val info = AlarmManager.AlarmClockInfo(triggerAt, pi)
                    am.setAlarmClock(info, pi)
                    Log.w(TAG, "정확 알람 권한 없음 -> AlarmClock 으로 대체")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "알람 예약 권한 오류, 일반 알람 사용", e)
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
