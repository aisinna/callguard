package com.parentcare.callguard

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)

        val number = intent.getStringExtra("number") ?: "알 수 없는 번호"
        val seconds = intent.getIntExtra("seconds", PrefsHelper.getSecondSeconds(this))
        val timeText = PrefsHelper.formatSeconds(seconds)

        findViewById<TextView>(R.id.tv_warning_message).text = buildString {
            append("통화가 ${timeText}을(를) 넘었습니다\n\n")
            append("[$number] 번호와 통화 중\n\n")
            append("- 낯선 사람이 송금, 계좌번호, 카드번호를 요구하나요?\n")
            append("- '자녀', '경찰', '검찰', '은행'을 사칭하고 있나요?\n")
            append("- 조금이라도 의심되면 바로 전화를 끊고\n")
            append("  등록된 자녀에게 먼저 확인 전화를 하세요.")
        }

        findViewById<Button>(R.id.btn_close).setOnClickListener { finish() }
    }
}
