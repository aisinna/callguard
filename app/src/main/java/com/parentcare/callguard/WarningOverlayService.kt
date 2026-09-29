package com.parentcare.callguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * 2차 경고를 '즉시' 전체 화면으로 띄우는 오버레이 창.
 *
 * 화면이 켜져 있고 잠금이 풀린 상태(통화 중 대부분)에서는 안드로이드가
 * 전체 화면 알림(fullScreenIntent)을 화면에 띄우지 않고 상단 알림으로만 보여준다.
 * 그래서 '다른 앱 위에 표시' 권한으로 오버레이 창을 직접 띄운다.
 *
 * 정식 포그라운드 서비스로 동작한다(specialUse 타입). 백그라운드에서(전화 상태를 받은
 * 브로드캐스트 리시버가) 시작해도 안드로이드 8+ 의 백그라운드 서비스 제약 없이 안정적으로
 * 뜨게 하기 위함이다. 포그라운드 서비스는 알림이 하나 필수라, 낮은 우선순위 알림을 하나 띄운다
 * (2차 경고 알림·오버레이 화면과는 별개).
 *
 * 창을 유지하려면 프로세스가 살아 있어야 하므로 서비스가 창을 소유한다.
 * 버튼을 누르거나, 통화가 끝나면(CallStateReceiver 의 IDLE) 종료된다.
 */
class WarningOverlayService : Service() {

    companion object {
        private const val TAG = "WarningOverlay"
        private const val CHANNEL_FG = "call_guard_overlay_fg"
        private const val FOREGROUND_ID = 2200
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_SECONDS = "seconds"

        fun start(context: Context, number: String, seconds: Int): Boolean {
            return try {
                val intent = Intent(context, WarningOverlayService::class.java).apply {
                    putExtra(EXTRA_NUMBER, number)
                    putExtra(EXTRA_SECONDS, seconds)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "오버레이 서비스 시작 실패", e)
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WarningOverlayService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "오버레이 서비스 종료 실패", e)
            }
        }
    }

    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        // 이미 떠 있으면 다시 만들지 않는다 (알람이 중복으로 와도 창은 하나)
        if (overlayView == null) {
            val number = intent?.getStringExtra(EXTRA_NUMBER) ?: PrefsHelper.UNKNOWN_NUMBER
            val seconds = intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0
            if (!showOverlay(number, seconds)) {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FG, "경고 화면 표시", NotificationManager.IMPORTANCE_LOW)
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_FG)
                .setSmallIcon(R.drawable.ic_stat_callguard)
                .setContentTitle("보이스피싱 경고 화면을 표시하는 중")
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(FOREGROUND_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "포그라운드 전환 실패", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverlay(number: String, seconds: Int): Boolean {
        if (!Settings.canDrawOverlays(this)) return false

        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(this).inflate(R.layout.activity_warning, null)

            // 버튼을 누르면 서비스를 끝내고, onDestroy 에서 창을 제거한다
            WarningUi.bind(this, view, number, seconds) { stopSelf() }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.OPAQUE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            wm.addView(view, params)
            overlayView = view
            Log.d(TAG, "경고 오버레이 표시")
            true
        } catch (e: Exception) {
            Log.e(TAG, "오버레이 표시 실패", e)
            false
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "오버레이 제거 실패", e)
        }
    }
}
