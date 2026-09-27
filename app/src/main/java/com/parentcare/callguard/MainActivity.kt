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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etMyName: EditText
    private lateinit var rgAge: RadioGroup
    private lateinit var etFirstMin: EditText
    private lateinit var etFirstSec: EditText
    private lateinit var etSecondMin: EditText
    private lateinit var etSecondSec: EditText
    private lateinit var etGuardian1: EditText
    private lateinit var etGuardian2: EditText
    private lateinit var etGuardian3: EditText
    private lateinit var etWhitelist: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDetail: TextView
    private lateinit var tvPermission: TextView
    private lateinit var btnSave: Button

    /** 연령대 라디오 변경 시 자동 채우기를 막기 위한 플래그 */
    private var loadingSaved = false

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

    // ---------------- 화면 연결 ----------------

    private fun bindViews() {
        etMyName = findViewById(R.id.et_my_name)
        rgAge = findViewById(R.id.rg_age)
        etFirstMin = findViewById(R.id.et_first_min)
        etFirstSec = findViewById(R.id.et_first_sec)
        etSecondMin = findViewById(R.id.et_second_min)
        etSecondSec = findViewById(R.id.et_second_sec)
        etGuardian1 = findViewById(R.id.et_guardian1)
        etGuardian2 = findViewById(R.id.et_guardian2)
        etGuardian3 = findViewById(R.id.et_guardian3)
        etWhitelist = findViewById(R.id.et_whitelist_numbers)
        tvStatus = findViewById(R.id.tv_status)
        tvStatusDetail = findViewById(R.id.tv_status_detail)
        tvPermission = findViewById(R.id.tv_permission)
        btnSave = findViewById(R.id.btn_save)
    }

    private fun loadSaved() {
        loadingSaved = true

        etMyName.setText(PrefsHelper.getMyName(this))

        when (PrefsHelper.getAgeGroup(this)) {
            "20~30대" -> rgAge.check(R.id.rb_age_young)
            "40~50대" -> rgAge.check(R.id.rb_age_mid)
            "60대 이상" -> rgAge.check(R.id.rb_age_senior)
        }

        val f = PrefsHelper.getFirstSeconds(this)
        val s = PrefsHelper.getSecondSeconds(this)
        setTime(etFirstMin, etFirstSec, f)
        setTime(etSecondMin, etSecondSec, s)

        val guardians = PrefsHelper.getGuardianList(this)
        etGuardian1.setText(guardians.getOrElse(0) { "" })
        etGuardian2.setText(guardians.getOrElse(1) { "" })
        etGuardian3.setText(guardians.getOrElse(2) { "" })

        etWhitelist.setText(PrefsHelper.getWhitelistRaw(this))

        loadingSaved = false
    }

    private fun setTime(etMin: EditText, etSec: EditText, totalSeconds: Int) {
        etMin.setText((totalSeconds / 60).toString())
        etSec.setText((totalSeconds % 60).toString())
    }

    // ---------------- 버튼 ----------------

    private fun setupButtons() {
        rgAge.setOnCheckedChangeListener { _, checkedId ->
            if (loadingSaved) return@setOnCheckedChangeListener
            val group = ageGroupOf(checkedId) ?: return@setOnCheckedChangeListener
            val (first, second) = PrefsHelper.recommendedSeconds(group)
            setTime(etFirstMin, etFirstSec, first)
            setTime(etSecondMin, etSecondSec, second)
            toast("$group 권장 시간을 적용했습니다. 직접 수정해도 됩니다.")
        }

        btnSave.setOnClickListener {
            if (PrefsHelper.isMonitoring(this)) stopMonitoring() else saveAndStart()
        }

        findViewById<Button>(R.id.btn_test_warning).setOnClickListener {
            NotificationHelper.showSecondWarning(
                this, "0212345678(테스트)", PrefsHelper.getSecondSeconds(this)
            )
        }

        findViewById<Button>(R.id.btn_test_sms).setOnClickListener {
            SmsHelper.sendTest(this)
        }

        findViewById<Button>(R.id.btn_perm_overlay).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.btn_perm_alarm).setOnClickListener { openExactAlarmSettings() }
        findViewById<Button>(R.id.btn_perm_battery).setOnClickListener { openBatterySettings() }
    }

    private fun ageGroupOf(checkedId: Int): String? = when (checkedId) {
        R.id.rb_age_young -> "20~30대"
        R.id.rb_age_mid -> "40~50대"
        R.id.rb_age_senior -> "60대 이상"
        else -> null
    }

    // ---------------- 저장 / 시작 ----------------

    private fun readSeconds(etMin: EditText, etSec: EditText): Int? {
        val m = etMin.text.toString().trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        val s = etSec.text.toString().trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        if (m < 0 || s < 0 || s > 59) return null
        val total = m * 60 + s
        return if (total <= 0) null else total
    }

    private fun saveAndStart() {
        val myName = etMyName.text.toString().trim()
        val first = readSeconds(etFirstMin, etFirstSec)
        val second = readSeconds(etSecondMin, etSecondSec)

        val guardians = listOf(etGuardian1, etGuardian2, etGuardian3)
            .map { it.text.toString().trim() }
            .filter { it.isNotBlank() }

        val whitelistRaw = etWhitelist.text.toString().trim()

        if (myName.isEmpty()) {
            toast("내 이름을 입력해주세요")
            return
        }
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
        if (guardians.isEmpty()) {
            toast("보호자를 최소 1명 입력해주세요")
            return
        }

        PrefsHelper.setMyName(this, myName)
        ageGroupOf(rgAge.checkedRadioButtonId)?.let { PrefsHelper.setAgeGroup(this, it) }
        PrefsHelper.setFirstSeconds(this, first)
        PrefsHelper.setSecondSeconds(this, second)
        PrefsHelper.setGuardiansRaw(this, guardians.joinToString(","))
        PrefsHelper.setWhitelistRaw(this, whitelistRaw)
        PrefsHelper.setMonitoring(this, true)

        StatusNotifier.show(this)
        refreshStatus()
        toast("서로 지켜보기를 시작했습니다")
    }

    private fun stopMonitoring() {
        PrefsHelper.setMonitoring(this, false)
        AlarmScheduler.cancelAll(this)
        StatusNotifier.hide(this)
        refreshStatus()
        toast("중지했습니다")
    }

    // ---------------- 상태 표시 ----------------

    private fun refreshStatus() {
        val on = PrefsHelper.isMonitoring(this)
        val first = PrefsHelper.formatSeconds(PrefsHelper.getFirstSeconds(this))
        val second = PrefsHelper.formatSeconds(PrefsHelper.getSecondSeconds(this))
        val myName = PrefsHelper.getMyName(this)
        val guardians = PrefsHelper.getGuardianList(this)
        val white = PrefsHelper.getWhitelistRaw(this)

        if (on) {
            tvStatus.text = "서로 지켜보는 중"
            tvStatus.setBackgroundColor(Color.parseColor("#2E7D32"))
            btnSave.text = "중지하기"
            tvStatusDetail.text = buildString {
                append("$myName 님을 지켜보고 있습니다\n\n")
                append("통화 $first → 나에게 알림\n")
                append("통화 $second → 경고 화면 + 보호자 알림\n")
                append("보호자 ${guardians.size}명: ${guardians.joinToString(", ")}")
                if (white.isNotBlank()) append("\n예외 번호: $white")
            }
        } else {
            tvStatus.text = "꺼짐"
            tvStatus.setBackgroundColor(Color.parseColor("#757575"))
            btnSave.text = "저장하고 시작하기"
            tvStatusDetail.text = "설정을 입력하고 아래 버튼을 누르면 시작됩니다."
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

        if (!overlay || !exact || !battery) {
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

    // ---------------- 설정 화면 열기 ----------------

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
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e2: Exception) {
                openAppDetails()
            }
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

    // ---------------- 런타임 권한 ----------------

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
