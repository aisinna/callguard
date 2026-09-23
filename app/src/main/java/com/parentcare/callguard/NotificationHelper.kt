package com.parentcare.callguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val CHANNEL_WARN = "call_guard_warning"
    private const val CHANNEL_ALERT = "call_guard_alert"

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val warn = NotificationChannel(
                CHANNEL_WARN, "통화 경고", NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) }

            val alert = NotificationChannel(
                CHANNEL_ALERT, "보이스피싱 긴급 경고", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }

            manager.createNotificationChannel(warn)
            manager.createNotificationChannel(alert)
        }
    }

    fun showFirstWarning(context: Context, minutes: Int) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_WARN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("통화가 길어지고 있어요")
            .setContentText("통화 ${minutes}분 경과. 잠시 후 안내가 표시됩니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(2001, n)
    }

    fun showSecondWarning(context: Context, number: String, minutes: Int) {
        ensureChannels(context)

        val fullScreenIntent = Intent(context, WarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("number", number)
            putExtra("minutes", minutes)
        }

        val pi = PendingIntent.getActivity(
            context, 3001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("보이스피싱 주의")
            .setContentText("[$number] 와(과) ${minutes}분째 통화 중입니다.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "통화가 ${minutes}분을 넘었습니다.\n" +
                    "송금·계좌번호·현금 이야기가 나오면 즉시 끊고 자녀에게 확인하세요."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(2002, n)
    }
}
