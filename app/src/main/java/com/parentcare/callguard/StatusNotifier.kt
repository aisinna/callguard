package com.parentcare.callguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 감시가 켜져 있는 동안 상태바에 계속 떠 있는 알림.
 * 사용자가 "지금 감시 중이구나"를 언제든 확인할 수 있게 한다.
 */
object StatusNotifier {

    private const val CHANNEL_ID = "call_guard_status"
    private const val NOTI_ID = 1000

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "감시 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "보이스피싱 안심콜이 동작 중임을 알려줍니다"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    fun show(context: Context) {
        ensureChannel(context)

        val first = PrefsHelper.getFirstMinutes(context)
        val second = PrefsHelper.getSecondMinutes(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 500, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("보이스피싱 안심콜 감시 중")
            .setContentText("통화 ${first}분 알림 · ${second}분 경고")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTI_ID, n)
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTI_ID)
    }
}
