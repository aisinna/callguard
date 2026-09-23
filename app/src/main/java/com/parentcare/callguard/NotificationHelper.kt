package com.parentcare.callguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 8분 시점 1차 경고 / 10분 시점 2차 경고 알림을 담당.
 */
object NotificationHelper {

    private const val CHANNEL_ID_WARN = "call_guard_warning_channel"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_WARN,
                "통화 경고 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showFirstWarning(context: Context) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_WARN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("통화가 길어지고 있어요")
            .setContentText("통화 8분 경과. 계속 진행 중이면 곧 안내가 표시됩니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(2001, notification)
    }

    fun showSecondWarning(context: Context, number: String, elapsedMinutes: Int) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_WARN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ 보이스피싱 주의")
            .setContentText("[$number] 와(과) ${elapsedMinutes}분째 통화 중입니다.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(2002, notification)
    }
}
