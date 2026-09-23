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

    fun showFirstWarning(context: Context) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_WARN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("통화가 길어지고 있어요")
            .setContentText("통화 8분 경과. 잠시 후 안내가 표시됩니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(2001, n)
    }

    fun showSecond
