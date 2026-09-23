package com.parentcare.callguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etFirstMin: EditText
    private lateinit var etFirstSec: EditText
    private lateinit var etSecondMin: EditText
    private lateinit var etSecondSec: EditText
    private lateinit var etChildNumber: EditText
    private lateinit var etWhitelist: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDetail: TextView
    private lateinit var tvPermission: TextView
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            bindViews()
            loadSaved()
            setupButtons()
            requestRuntimePermissions()
            refreshStatus()
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
        etFirstMin = findViewById(R.id.et_first_min)
        etFirstSec = findViewById(R.id.et_first_sec)
        etSecondMin = findViewById(R.id.et_second_min)
        etSecondSec = findViewById(R.id.et_second_sec)
        etChildNumber = findViewById(R.id.et_child_number)
        etWhitelist = findViewById(R.id.et_whitelist_numbers)
        tvStatus = findViewById(R.id.tv_status)
        tvStatusDetail = findViewById(R.id.tv_status_detail)
        tvPermission = findViewById(R.id.tv_permission)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun loadSaved() {
        val f = PrefsHelper.getFirstSeconds(this)
        val s = PrefsHelper.getSecondSeconds(this)
        etFirstMin.setText((f / 60).toString())
        etFirstSec.setText((f % 60).toString())
        etSecondMin.setText((s / 60).toString())
        etSecondSec.setText((s % 60).toString())
        etChildNumber.setText(PrefsHelper.getChildNumber(this))
        etWhitelist.setText(PrefsHelper.getWhitelistRaw(this))
    }

    private fun setupButtons() {
        btnSave.setOnClickListener {
            if (PrefsHelper.isMonitoring(this)) stopMonitoring() else saveAndStart()
        }

        findViewById<Button>(R.id.btn_test_warning).setOnClickListener {
            NotificationHelper.showSecondWarning(
                this, "[휴대전화번호_REDACTED](테스트)", PrefsHelper.getSecondSeconds(this)
            )
        }

        findViewById<Button>(R.id.btn_test_sms).setOnClickListener {
            SmsHelper.sendTest(this)
        }

        findViewById<Button>(R.id.btn_perm_overlay).setOnClickListener {
            openOverlaySettings()
        }

        findViewById<Button>(R.id.btn_perm_alarm).setOnClickListener {
            openExactAlarmSettings()
        }

        findViewById<Button>(R.id.btn_perm_battery).setOnClickListener {
            openBatterySettings()
        }
    }

    private fun readSeconds(etMin: EditText, etSec: EditText): Int? {
        val m = etMin.text.toString().trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        val s = etSec.text.toString().trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        if (m < 0 || s < 0 || s > 59) return null
        val total = m * 60 + s
        return if (total <= 0) null else total
    }

    private fun saveAndStart() {
        val first = readSeconds(etFirstMin, etFirstSec)
        val second = readSeconds(etSecondMin, etSecondSec)
        val childNumber = etChildNumber.text.toString().trim()
        val whitelistRaw = etWhitelist.text.toString().trim()

        if (first == null) {
            toast("1차 알림 시간을 확인해주세요 (초는 0~59)")
            return
        }
        if (second == null) {
            toast("2차 경고 시간을 확인해주세요 (초는 0~59)")
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

        PrefsHelper.setFirstSeconds(this, first)
        PrefsHelper.setSecondSeconds(this, second)
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
        val first = PrefsHelper.formatSeconds(PrefsHelper.getFirstSeconds(this))
        val second = PrefsHelper.formatSeconds(PrefsHelper.getSecondSeconds(this))
        val child = PrefsHelper.getChildNumber(this)
        val white = PrefsHelper.getWhitelistRaw(this)

        if (on) {
            tvStatus.text = "감시 중"
            tvStatus.setBackgroundColor(Color.parseColor("#2E7D32"))
            btnSave.text = "감시 중지하기"
            tvStatusDetail.text = buildString {
                append("통화 $first → 1차 알림\n")
                append("통화 $second → 경고 화면 + 문자 발송\n")
                append("알림 받을 번호: $child")
                if (white.isNotBlank()) append("\n예외 번호: $white")
            }
        } else {
            tvStatus.text = "감시 꺼짐"
            tvStatus.setBackgroundColor(Color.parseColor("#757575"))
            btnSave.text = "저장하고 감시 시작하기"
            tvStatusDetail.text = "설정을 입력하고 아래 버튼을 누르면 감시가 시작됩니다."
        }

        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        val overlay = canDrawOverlay()
        val exact = canScheduleExact()
        val battery = isIgnoringBattery()
        val sms = hasPermission(Manifest.permission.SEND_SMS)
        val phone = hasPermission(Manifest.permission.READ_PHONE_STATE)

        val sb = StringBuilder("권한 상태\n")
        sb.append(mark(phone)).append(" 전화 상태 읽기\n")
        sb.append(mark(sms)).append(" 문자 발송\n")
        sb.append(mark(overlay)).append(" 다른 앱 위에 표시  ← 경고화면 필수\n")
        sb.append(mark(exact)).append(" 알람 및 리마인더  ← 정확한 시간 필수\n")
        sb.append(mark(battery)).append(" 배터리 최적화 해제")

        if (!overlay || !exact) {
            sb.append("\n\n아래 버튼으로 꺼진 권한을 켜주세요.")
        }

        tvPermission.text = sb.toString()
    }

    private fun mark(ok: Boolean) = if (ok) "[O]" else "[X]"

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
    }

    private fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    private fun isIgnoringBattery(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun openOverlaySettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else toast("이 버전에서는 별도 설정이 필요 없습니다")
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun openExactAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            } else toast("이 버전에서는 별도 설정이 필요 없습니다")
        } catch (e: Exception) {
            openAppDetails()
        }
    }

    private fun openBatterySettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } else toast("이 버전에서는 별도 설정이 필요 없습니다")
        } catch (e: Exception) {
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            toast("설정 화면을 열 수 없습니다")
        }
    }

    private fun requestRuntimePermissions() {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
