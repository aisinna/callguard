package com.parentcare.callguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etFirst: EditText
    private lateinit var etSecond: EditText
    private lateinit var etChildNumber: EditText
    private lateinit var etWhitelist: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDetail: TextView
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            bindViews()
            loadSaved()
            setupButtons()
            refreshStatus()
            requestNeededPermissions()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "초기화 오류: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindViews() {
        etFirst = findViewById(R.id.et_first_minutes)
        etSecond = findViewById(R.id.et_second_minutes)
        etChildNumber = findViewById(R.id.et_child_number)
        etWhitelist = findViewById(R.id.et_whitelist_numbers)
        tvStatus = findViewById(R.id.tv_status)
        tvStatusDetail = findViewById(R.id.tv_status_detail)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun loadSaved() {
        etFirst.setText(PrefsHelper.getFirstMinutes(this).toString())
        etSecond.setText(PrefsHelper.getSecondMinutes(this).toString())
        etChildNumber.setText(PrefsHelper.getChildNumber(this))
        etWhitelist.setText(PrefsHelper.getWhitelistRaw(this))
    }

    private fun setupButtons() {
        btnSave.setOnClickListener {
            if (PrefsHelper.isMonitoring(this)) {
                stopMonitoring()
            } else {
                saveAndStart()
            }
        }

        findViewById<Button>(R.id.btn_test).setOnClickListener {
            val i = Intent(this, WarningActivity::class.java)
            i.putExtra("number", "[휴대전화번호_REDACTED]")
            i.putExtra("minutes", PrefsHelper.getSecondMinutes(this))
            startActivity(i)
        }
    }

    private fun saveAndStart() {
        val first = etFirst.text.toString().trim().toIntOrNull()
        val second = etSecond.text.toString().trim().toIntOrNull()
        val childNumber = etChildNumber.text.toString().trim()
        val whitelistRaw = etWhitelist.text.toString().trim()

        if (first == null || first < 1 || first > 120) {
            toast("1차 알림 시간을 1~120 사이 숫자로 입력해주세요")
            return
        }
        if (second == null || second < 1 || second > 120) {
            toast("2차 경고 시간을 1~120 사이 숫자로 입력해주세요")
            return
        }
        if (second <= first) {
            toast("2차 시간은 1차 시간보다 커야 합니다")
            return
        }
        if (childNumber.isEmpty()) {
            toast("자녀 연락처를 입력해주세요")
            return
        }

        PrefsHelper.setFirstMinutes(this, first)
        PrefsHelper.setSecondMinutes(this, second)
        PrefsHelper.setChildNumber(this, childNumber)
        PrefsHelper.setWhitelistRaw(this, whitelistRaw)
        PrefsHelper.setMonitoring(this, true)

        StatusNotifier.show(this)
        refreshStatus()
        toast("감시를 시작했습니다")
    }

    private fun stopMonitoring() {
        PrefsHelper.setMonitoring(this, false)
        AlarmScheduler.cancelAll(this)
        StatusNotifier.hide(this)
        refreshStatus()
        toast("감시를 중지했습니다")
    }

    private fun refreshStatus() {
        val on = PrefsHelper.isMonitoring(this)
        val first = PrefsHelper.getFirstMinutes(this)
        val second = PrefsHelper.getSecondMinutes(this)
        val child = PrefsHelper.getChildNumber(this)
        val white = PrefsHelper.getWhitelistRaw(this)

        if (on) {
            tvStatus.text = "감시 중"
            tvStatus.setBackgroundColor(Color.parseColor("#2E7D32"))
            btnSave.text = "감시 중지하기"
            tvStatusDetail.text = buildString {
                append("통화 ${first}분 → 1차 알림\n")
                append("통화 ${second}분 → 경고 화면 + 문자 발송\n")
                append("알림 받을 번호: $child")
                if (white.isNotBlank()) append("\n예외 번호: $white")
            }
        } else {
            tvStatus.text = "감시 꺼짐"
            tvStatus.setBackgroundColor(Color.parseColor("#757575"))
            btnSave.text = "저장하고 감시 시작하기"
            tvStatusDetail.text = "설정을 입력하고 아래 버튼을 누르면 감시가 시작됩니다."
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS
        )
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
