package com.parentcare.callguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object StatusNotifier {

    private const val CHANNEL_ID = "seorojikim_status"
    private const val NOTI_ID = 1000

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "동작 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "서로지킴이 동작 중임을 알려줍니다"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    fun show(context: Context) {
        ensureChannel(context)

        val first = PrefsHelper.formatSeconds(PrefsHelper.getFirstSeconds(context))
        val second = PrefsHelper.formatSeconds(PrefsHelper.getSecondSeconds(context))
        val count = PrefsHelper.getGuardianList(context).size

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 500, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_callguard)
            .setContentTitle("서로지킴 동작 중")
            .setContentText("$first 알림 · $second 경고 · 보호자 ${count}명")
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
