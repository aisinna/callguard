package com.parentcare.callguard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 전체 화면 경고. 잠금 화면 등에서 전체 화면 알림으로 뜰 때 사용한다.
 * (화면이 켜진 통화 중에는 WarningOverlayService 의 오버레이가 먼저 뜬다.)
 */
class WarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)

        // 오버레이가 떠 있었다면 닫아서 경고가 두 겹으로 보이지 않게 한다
        WarningOverlayService.stop(this)

        val number = intent.getStringExtra("number") ?: PrefsHelper.UNKNOWN_NUMBER
        val seconds = intent.getIntExtra("seconds", 0)

        WarningUi.bind(this, findViewById<View>(android.R.id.content), number, seconds) { finish() }
    }

    /** 뒤로가기로 실수로 닫히지 않게 막는다 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "아래 버튼을 눌러주세요", Toast.LENGTH_SHORT).show()
    }
}
