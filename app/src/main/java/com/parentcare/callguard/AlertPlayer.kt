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
 *
 * 통화에 집중하면 소리는 잘 안 들리므로 '진동'을 주된 경고 수단으로 쓴다.
 * (진동 패턴: [대기, 켬, 끔, 켬, 끔, ...] — 홀수 번째가 '징' 하고 울리는 구간)
 */
object AlertPlayer {

    private const val TAG = "AlertPlayer"

    /** 1차: 강한 진동 두 번  "징 ─ 징" */
    internal val PATTERN_FIRST = longArrayOf(
        0, 600, 250, 600
    )

    /** 2차: "징징징징" 을 3라운드 반복 (약 8초) */
    internal val PATTERN_SECOND = longArrayOf(
        0, 500, 150, 500, 150, 500, 150, 500,
        700, 500, 150, 500, 150, 500, 150, 500,
        700, 500, 150, 500, 150, 500, 150, 500
    )

    /** 1차: 강한 진동 + 짧은 알림음 */
    fun playFirst(context: Context) {
        vibrate(context, PATTERN_FIRST)
        playTone(short = true)
    }

    /** 2차: 긴 진동 + 강한 경고음 */
    fun playSecond(context: Context) {
        vibrate(context, PATTERN_SECOND)
        playTone(short = false)
        playRingtone(context)
    }

    /**
     * 진동 재생.
     * - 켜는 구간은 최대 세기(255)로 울린다 (세기 조절을 지원하는 기기).
     * - 알람 용도로 지정해, 통화 중/방해금지 상태에서도 억제되지 않게 한다.
     */
    private fun vibrate(context: Context, timings: LongArray) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val effect = if (vibrator.hasAmplitudeControl()) {
                val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 1) 255 else 0 }
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            vibrator.cancel()   // 이전 진동이 남아 있으면 끊고 새로 시작
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attrs)
            Log.d(TAG, "진동 재생 (${timings.size / 2}회 구간)")
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

            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

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
