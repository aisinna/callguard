package com.parentcare.callguard

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 통화 10분 경과 시 화면 위(잠금화면 포함)에 뜨는 전체화면 경고.
 * 통화 자체를 끊지는 않고, 사용자가 직접 판단하도록 유도한다.
 */
class WarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)

        val number = intent.getStringExtra("number") ?: "알 수 없는 번호"

        val messageView = findViewById<TextView>(R.id.tv_warning_message)
        messageView.text = buildString {
            append("⚠ 통화가 10분을 넘었습니다\n\n")
            append("[$number] 번호와 통화 중\n\n")
            append("- 낯선 사람이 송금, 계좌번호, 카드번호를 요구하나요?\n")
            append("- '자녀', '경찰', '검찰', '은행'을 사칭하고 있나요?\n")
            append("- 조금이라도 의심되면 바로 전화를 끊고\n")
            append("  등록된 자녀에게 먼저 확인 전화를 하세요.")
        }

        findViewById<Button>(R.id.btn_close).setOnClickListener {
            finish()
        }

        vibrateStrongly()
    }

    private fun vibrateStrongly() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
