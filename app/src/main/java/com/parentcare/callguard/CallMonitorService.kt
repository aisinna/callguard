package com.parentcare.callguard

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

/**
 * 통화가 연결된 순간부터 8분/10분 타이머를 예약하는 포그라운드 서비스.
 * 다이얼러를 대체하지 않고, 옆에서 시간만 잰다.
 */
class CallMonitorService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_NUMBER = "EXTRA_NUMBER"

        const val CHANNEL_ID = "call_guard_channel"
        const val NOTIFICATION_ID = 1001

        const val WARN_MINUTES_1 = 8L   // 1차 경고: 8분
        const val WARN_MINUTES_2 = 10L  // 2차 경고: 10분
    }

    private val handler = Handler(Looper.getMainLooper())
    private var runnable8min: Runnable? = null
    private var runnable10min: Runnable? = null
    private var currentNumber: String = "알수없음"
    private var callStartTime: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentNumber = intent.getStringExtra(EXTRA_NUMBER) ?: "알수없음"

                // 화이트리스트(자녀/가족 번호)면 모니터링하지 않음
                if (PrefsHelper.isWhitelisted(this, currentNumber)) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                callStartTime = System.currentTimeMillis()
                scheduleWarnings()
            }
            ACTION_STOP -> {
                cancelWarnings()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun scheduleWarnings() {
        cancelWarnings()

        runnable8min = Runnable {
            NotificationHelper.showFirstWarning(this)
        }
        runnable10min = Runnable {
            val elapsedMin = (System.currentTimeMillis() - callStartTime) / 60000
            NotificationHelper.showSecondWarning(this, currentNumber, elapsedMin.toInt())
            // 전체화면 경고 액티비티 실행
            val warnIntent = Intent(this, WarningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("number", currentNumber)
            }
            startActivity(warnIntent)
            // 자녀에게 SMS 알림
            SmsHelper.notifyChild(this, currentNumber, elapsedMin.toInt())
        }

        handler.postDelayed(runnable8min!!, WARN_MINUTES_1 * 60 * 1000)
        handler.postDelayed(runnable10min!!, WARN_MINUTES_2 * 60 * 1000)
    }

    private fun cancelWarnings() {
        runnable8min?.let { handler.removeCallbacks(it) }
        runnable10min?.let { handler.removeCallbacks(it) }
        runnable8min = null
        runnable10min = null
    }

    private fun buildForegroundNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "통화 모니터링", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("보이스피싱 안심콜 감시 중")
            .setContentText("통화 시간을 확인하고 있어요")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelWarnings()
        super.onDestroy()
    }
}
