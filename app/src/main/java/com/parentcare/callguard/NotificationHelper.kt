package com.parentcare.callguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    // 채널은 한 번 만들면 설정 변경이 불가하므로 버전 접미사를 붙여 새로 만든다
    private const val CHANNEL_WARN = "call_guard_warning_v3"
    private const val CHANNEL_ALERT = "call_guard_alert_v3"

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: soundUri

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val warn = NotificationChannel(
            CHANNEL_WARN, "통화 경고 (1차)", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
            setSound(soundUri, attrs)
        }

        val alert = NotificationChannel(
            CHANNEL_ALERT, "보이스피싱 긴급 경고 (2차)", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 200, 600, 200, 600)
            setSound(alarmUri, attrs)
            setBypassDnd(true)
        }

        manager.createNotificationChannel(warn)
        manager.createNotificationChannel(alert)
    }

    fun showFirstWarning(context: Context, seconds: Int) {
        ensureChannels(context)
        val timeText = PrefsHelper.formatSeconds(seconds)

        val n = NotificationCompat.Builder(context, CHANNEL_WARN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("통화가 길어지고 있어요")
            .setContentText("통화 $timeText 경과. 잠시 후 안내가 표시됩니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(2001, n)

        // 채널 설정과 별개로 직접 진동·소리 재생
        AlertPlayer.playFirst(context)
    }

    fun showSecondWarning(context: Context, number: String, seconds: Int) {
        ensureChannels(context)
        val timeText = PrefsHelper.formatSeconds(seconds)

        val fullScreenIntent = Intent(context, WarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("number", number)
            putExtra("seconds", seconds)
        }

        val pi = PendingIntent.getActivity(
            context, 3001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("보이스피싱 주의")
            .setContentText("[$number] 와(과) ${timeText}째 통화 중입니다.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "통화가 ${timeText}을(를) 넘었습니다.\n" +
                    "송금·계좌번호·현금 이야기가 나오면 즉시 끊고 자녀에게 확인하세요."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(2002, n)

        AlertPlayer.playSecond(context)

        // 오버레이 권한이 있으면 경고 화면을 직접 띄운다
        tryStartWarningActivity(context, number, seconds)
    }

    private fun tryStartWarningActivity(context: Context, number: String, seconds: Int) {
        try {
            val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                    android.provider.Settings.canDrawOverlays(context)
            if (!canOverlay) return

            val i = Intent(context, WarningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_AC
