package com.parentcare.callguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 최초 실행 시 권한 요청 + 자녀 번호 / 화이트리스트 등록 화면.
 *
 * 크래시 방지를 위해
 *  - POST_NOTIFICATIONS 는 Android 13(API 33) 이상에서만 요청
 *  - 화면 구성 중 예외가 나도 앱이 죽지 않도록 try/catch 로 감쌈
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)
            setupViews()
            requestNeededPermissions()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "초기화 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupViews() {
        val etChildNumber = findViewById<EditText>(R.id.et_child_number)
        val etWhitelist = findViewById<EditText>(R.id.et_whitelist_numbers)
        val btnSave = findViewById<Button>(R.id.btn_save)

        etChildNumber.setText(PrefsHelper.getChildNumber(this))
        etWhitelist.setText(PrefsHelper.getWhitelistRaw(this))

        btnSave.setOnClickListener {
            val childNumber = etChildNumber.text.toString().trim()
            val whitelistRaw = etWhitelist.text.toString().trim()

            if (childNumber.isEmpty()) {
                Toast.makeText(this, "자녀 연락처를 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            PrefsHelper.setChildNumber(this, childNumber)
            PrefsHelper.setWhitelistRaw(this, whitelistRaw)
            Toast.makeText(this, "설정이 저장되었습니다. 이제 통화를 감시합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS
        )

        // POST_NOTIFICATIONS 는 Android 13(API 33) 이상에서만 존재한다.
        // 하위 버전에서 요청하면 예외가 발생할 수 있으므로 분기 처리.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        }
    }
}
