package com.parentcare.callguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
            append("지금 이런 이야기가 오가고 있나요?\n\n")
            append("· 송금, 계좌번호, 카드번호를 요구한다\n")
            append("· '가족', '경찰', '검찰', '금융기관'을 사칭한다\n")
            append("· 투자 수익이나 대출을 권유한다\n")
            append("· \"아무에게도 말하지 마세요\"라고 했다\n\n")
            append("하나라도 해당되면 보이스피싱입니다.\n")
            append("지금 끊고 보호자와 상의하세요.\n\n")
            if (guardianCount > 0) {
                append("보호자 ${guardianCount}명에게 이미 알림을 보냈습니다.")
            }
        }

        // 지금 바로 전화 끊기
        findViewById<Button>(R.id.btn_hangup).setOnClickListener {
            goHome()
            Toast.makeText(
                this,
                "통화 화면에서 종료 버튼을 눌러 전화를 끊어주세요",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }

        // 경고 해제 → 보호자에게 안심 문자
        findViewById<Button>(R.id.btn_close).setOnClickListener {
            SmsHelper.sendSafeNotice(this)
            Toast.makeText(
                this,
                "보호자에게 안심 문자를 보냈습니다",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    /** 경고 화면을 닫고 홈으로 나가 통화 화면이 보이도록 유도 */
    private fun goHome() {
        try {
            val i = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(i)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 뒤로가기로 실수로 닫히지 않게 막는다 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "아래 버튼을 눌러주세요", Toast.LENGTH_SHORT).show()
    }
}
