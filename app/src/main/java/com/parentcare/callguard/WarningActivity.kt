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
        val guardianCount = PrefsHelper.getGuardianList(this).size

        findViewById<TextView>(R.id.tv_warning_message).text = buildString {
            append("통화가 ${timeText}을(를) 넘었습니다\n\n")
            append("[$number] 번호와 통화 중\n\n")
            append("- 송금, 계좌번호, 카드번호를 요구하나요?\n")
            append("- '가족', '경찰', '검찰', '금융기관'을 사칭하나요?\n")
            append("- 아무에게도 말하지 말라고 하나요?\n")
            append("- 투자 수익이나 대출을 권유하나요?\n\n")
            append("조금이라도 의심되면 바로 끊고\n")
            append("보호자에게 먼저 확인하세요.\n\n")
            if (guardianCount > 0) {
                append("보호자 ${guardianCount}명에게 알림을 보냈습니다.")
            }
        }

        findViewById<Button>(R.id.btn_close).setOnClickListener { finish() }
    }
}
