package com.parentcare.callguard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 진동 + 경고음을 직접 재생한다.
 * 알림 채널 설정(한 번 만들면 변경 불가)에 의존하지 않기 위함.
 * 통화 중에도 들리도록 ToneGenerator 를 함께 사용한다.
 */
object AlertPlayer {

    private const val TAG = "AlertPlayer"

    /** 1차: 짧은 진동 + 짧은 알림음 */
    fun playFirst(context: Context) {
        vibrate(context, longArrayOf(0, 300, 150, 300))
        playTone(short = true)
    }

    /** 2차: 긴 진동 + 강한 경고음 */
    fun playSecond(context: Context) {
        vibrate(context, longArrayOf(0, 600, 200, 600, 200, 600, 200, 600))
        playTone(short = false)
        playRingtone(context)
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
            Log.d(TAG, "진동 재생")
        } catch (e: Exception) {
            Log.e(TAG, "진동 실패", e)
        }
    }

    /**
     * ToneGenerator 는 통화 중에도 소리가 나는 몇 안 되는 방법이다.
     * STREAM_ALARM 을 쓰면 무음 모드에서도 들린다.
     */
    private fun playTone(short: Boolean) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            if (short) {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
            } else {
                tg.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 2000)
            }
            // 재생이 끝난 뒤 해제
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, if (short) 800 else 2500)
            Log.d(TAG, "경고음 재생")
        } catch (e: Exception) {
            Log.e(TAG, "경고음 실패", e)
        }
    }

    /** 2차 경고에서만 알람 벨소리를 추가로 재생 */
    private fun playRingtone(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }

            ringtone.play()

            // 5초 후 정지
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { if (ringtone.isPlaying) ringtone.stop() } catch (_: Exception) {}
            }, 5000)

            Log.d(TAG, "벨소리 재생")
        } catch (e: Exception) {
            Log.e(TAG, "벨소리 실패", e)
        }
    }
}
