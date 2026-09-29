package com.parentcare.callguard

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // 채널은 한 번 만들면 설정 변경이 불가하므로 버전 접미사를 붙여 새로 만든다
    // v4: 채널 진동을 껐다 (진동은 AlertPlayer 가 직접 낸다 — 둘이 겹쳐 서로 덮어쓰는 것을 방지)
    private const val CHANNEL_WARN = "call_guard_warning_v4"
    private const val CHANNEL_ALERT = "call_guard_alert_v4"
    private const val OLD_CHANNEL_WARN = "call_guard_warning_v3"
    private const val OLD_CHANNEL_ALERT = "call_guard_alert_v3"

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
            enableVibration(false)
            setSound(soundUri, attrs)
        }

        val alert = NotificationChannel(
            CHANNEL_ALERT, "보이스피싱 긴급 경고 (2차)", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(false)
            setSound(alarmUri, attrs)
            setBypassDnd(true)
        }

        manager.createNotificationChannel(warn)
        manager.createNotificationChannel(alert)

        // 이전 버전 채널 정리 (없으면 아무 일도 일어나지 않음)
        manager.deleteNotificationChannel(OLD_CHANNEL_WARN)
        manager.deleteNotificationChannel(OLD_CHANNEL_ALERT)
    }

    fun showFirstWarning(context: Context, seconds: Int) {
        ensureChannels(context)
        val timeText = PrefsHelper.formatSeconds(seconds)

        val n = NotificationCompat.Builder(context, CHANNEL_WARN)
            .setSmallIcon(R.drawable.ic_stat_callguard)
            .setContentTitle("통화가 길어지고 있어요")
            .setContentText("통화 $timeText 경과. 잠시 후 안내가 표시됩니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(2001, n)

        // 강한 진동('징징') + 짧은 알림음을 직접 재생
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
            .setSmallIcon(R.drawable.ic_stat_callguard)
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
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(2002, n)

        // 강한 진동('징징징징' 여러 차례) + 경고음
        AlertPlayer.playSecond(context)

        // 경고 화면을 즉시 띄운다
        showWarningScreen(context, number, seconds)
    }

    /**
     * 경고 화면을 즉시 띄운다.
     *
     * - 화면이 켜져 있고 잠금이 풀린 상태(통화 중 대부분):
     *   안드로이드는 전체 화면 알림을 '상단 알림'으로만 보여주므로,
     *   '다른 앱 위에 표시' 권한으로 오버레이 창을 직접 띄운다.
     * - 잠금 화면 등: 전체 화면 알림(WarningActivity)이 화면을 켜고 표시한다.
     *
     * 여기서 예외가 나도 뒤따르는 보호자 문자 발송이 막히지 않도록 모두 잡는다.
     */
    private fun showWarningScreen(context: Context, number: String, seconds: Int) {
        try {
            val canOverlay = Settings.canDrawOverlays(context)
            val locked = context.getSystemService(KeyguardManager::class.java)
                ?.isKeyguardLocked ?: false

            if (canOverlay && !locked && WarningOverlayService.start(context, number, seconds)) return
        } catch (e: Exception) {
            Log.w(TAG, "오버레이 경고 실패, Activity 로 대체", e)
        }
        tryStartWarningActivity(context, number, seconds)
    }

    private fun tryStartWarningActivity(context: Context, number: String, seconds: Int) {
        try {
            if (!Settings.canDrawOverlays(context)) return

            val i = Intent(context, WarningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("number", number)
                putExtra("seconds", seconds)
            }
            context.startActivity(i)
        } catch (e: Exception) {
            Log.w(TAG, "경고 화면 직접 실행 실패", e)
        }
    }
}
