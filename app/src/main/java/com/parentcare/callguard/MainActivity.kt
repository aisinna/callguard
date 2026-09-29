package com.parentcare.callguard

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_RUNTIME_PERMISSIONS = 100
        private const val REQ_CALL_SCREENING_ROLE = 101
    }

    private lateinit var etMyName: EditText
    private lateinit var etFirstMin: EditText
    private lateinit var etFirstSec: EditText
    private lateinit var etSecondMin: EditText
    private lateinit var etSecondSec: EditText
    private lateinit var etGuardian1: EditText
    private lateinit var etGuardian2: EditText
    private lateinit var etGuardian3: EditText
    private lateinit var etWhitelist: EditText
    private lateinit var tvStatusDetail: TextView
    private lateinit var tvPermission: TextView
    private lateinit var btnSave: Button

    // 고정 헤더
    private lateinit var headerBox: LinearLayout
    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSub: TextView
    private lateinit var scrollRoot: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            bindViews()
            loadSaved()
            setupButtons()
            ensureConsentThenRequestPermissions()
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
        etFirstMin = findViewById(R.id.et_first_min)
        etFirstSec = findViewById(R.id.et_first_sec)
        etSecondMin = findViewById(R.id.et_second_min)
        etSecondSec = findViewById(R.id.et_second_sec)
        etGuardian1 = findViewById(R.id.et_guardian1)
        etGuardian2 = findViewById(R.id.et_guardian2)
        etGuardian3 = findViewById(R.id.et_guardian3)
        etWhitelist = findViewById(R.id.et_whitelist_numbers)
        tvStatusDetail = findViewById(R.id.tv_status_detail)
        tvPermission = findViewById(R.id.tv_permission)
        btnSave = findViewById(R.id.btn_save)

        headerBox = findViewById(R.id.header_box)
        tvHeaderTitle = findViewById(R.id.tv_header_title)
        tvHeaderSub = findViewById(R.id.tv_header_sub)
        scrollRoot = findViewById(R.id.scroll_root)
    }

    private fun loadSaved() {
        etMyName.setText(PrefsHelper.getMyName(this))

        setTime(etFirstMin, etFirstSec, PrefsHelper.getFirstSeconds(this))
        setTime(etSecondMin, etSecondSec, PrefsHelper.getSecondSeconds(this))

        val guardians = PrefsHelper.getGuardianList(this)
        etGuardian1.setText(guardians.getOrElse(0) { "" })
        etGuardian2.setText(guardians.getOrElse(1) { "" })
        etGuardian3.setText(guardians.getOrElse(2) { "" })

        etWhitelist.setText(PrefsHelper.getWhitelistRaw(this))
    }

    private fun setTime(etMin: EditText, etSec: EditText, totalSeconds: Int) {
        etMin.setText((totalSeconds / 60).toString())
        etSec.setText((totalSeconds % 60).toString())
    }

    // ---------------- 버튼 ----------------

    private fun setupButtons() {
        btnSave.setOnClickListener {
            if (PrefsHelper.isMonitoring(this)) stopMonitoring() else saveAndStart()
        }

        findViewById<Button>(R.id.btn_test_warning).setOnClickListener {
            NotificationHelper.showSecondWarning(
                this, "0212345678(테스트)", PrefsHelper.getSecondSeconds(this)
            )
        }

        findViewById<Button>(R.id.btn_test_sms).setOnClickListener {
            SmsHelper.composeTest(this)
        }

        findViewById<Button>(R.id.btn_perm_overlay).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.btn_perm_alarm).setOnClickListener { openExactAlarmSettings() }
        findViewById<Button>(R.id.btn_perm_battery).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.btn_perm_screening).setOnClickListener { requestCallScreeningRole() }
    }

    // ---------------- 온보딩 동의 ----------------

    /**
     * 권한을 요청하기 전에, 이 앱이 무엇을 하는지 먼저 보여주고 동의를 받는다.
     * 한 번 동의하면 다음 실행부터는 바로 권한 요청으로 넘어간다.
     */
    private fun ensureConsentThenRequestPermissions() {
        if (PrefsHelper.isConsentGiven(this)) {
            requestRuntimePermissions()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("시작하기 전에 확인해주세요")
            .setMessage(
                "서로지킴은 통화 시간을 재고, 오래 통화하면 등록한 보호자에게 상대 번호와 함께 " +
                        "알릴 수 있는 문자 작성 화면을 엽니다(문자는 직접 '보내기'를 눌러야 나갑니다).\n\n" +
                        "연락처 권한은 걸려 온 전화가 보호자 등 저장된 사람인지 확인하는 데만 쓰며, " +
                        "연락처 내용을 따로 읽거나 저장하지 않습니다.\n\n" +
                        "통화 내용을 녹음하거나 듣지 않으며, 서버로 아무것도 전송하지 않습니다."
            )
            .setCancelable(false)
            .setPositiveButton("동의하고 시작") { _, _ ->
                PrefsHelper.setConsentGiven(this, true)
                requestRuntimePermissions()
            }
            .show()
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
        PrefsHelper.setFirstSeconds(this, first)
        PrefsHelper.setSecondSeconds(this, second)
        PrefsHelper.setGuardiansRaw(this, guardians.joinToString(","))
        PrefsHelper.setWhitelistRaw(this, whitelistRaw)
        PrefsHelper.setMonitoring(this, true)

        StatusNotifier.show(this)
        refreshStatus()

        vibrateFeedback(longArrayOf(0, 60, 80, 60))   // 짧게 두 번
        scrollToTop()
        toast("서로 지켜보기를 시작했습니다")
    }

    private fun stopMonitoring() {
        PrefsHelper.setMonitoring(this, false)
        AlarmScheduler.cancelAll(this)
        StatusNotifier.hide(this)
        refreshStatus()

        vibrateFeedback(longArrayOf(0, 120))          // 길게 한 번
        scrollToTop()
        toast("중지했습니다")
    }

    private fun scrollToTop() {
        scrollRoot.post { scrollRoot.smoothScrollTo(0, 0) }
    }

    private fun vibrateFeedback(pattern: LongArray) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            // 고정 헤더
            headerBox.setBackgroundColor(Color.parseColor("#2E7D32"))
            tvHeaderTitle.text = "서로 지켜보는 중"
            tvHeaderSub.text = "$second 경과 시 보호자 ${guardians.size}명에게 알림"

            btnSave.text = "중지하기"
            tvStatusDetail.text = buildString {
                append("$myName 님을 지켜보고 있습니다\n\n")
                append("통화 $first → 나에게 알림\n")
                append("통화 $second → 경고 화면 + 보호자 알림\n")
                append("보호자 ${guardians.size}명: ${guardians.joinToString(", ")}")
                if (white.isNotBlank()) append("\n예외 번호: $white")
            }
        } else {
            headerBox.setBackgroundColor(Color.parseColor("#757575"))
            tvHeaderTitle.text = "서로지킴 - 꺼짐"
            tvHeaderSub.text = "설정을 저장하면 시작됩니다"

            btnSave.text = "저장하고 시작하기"
            tvStatusDetail.text = "설정을 입력하고 아래 버튼을 누르면 시작됩니다."
        }

        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        val overlay = canDrawOverlay()
        val exact = canScheduleExact()
        val battery = isIgnoringBattery()
        val phone = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val contacts = hasPermission(Manifest.permission.READ_CONTACTS)
        val screening = hasCallScreeningRole()
        val screeningSupported = isCallScreeningSupported()

        val sb = StringBuilder("권한 상태\n")
        sb.append(mark(phone)).append(" 전화 상태 읽기\n")
        if (screeningSupported) {
            sb.append(mark(screening)).append(" 발신자 정보 앱  ← 상대 번호 확인(보호자 통화 제외)\n")
            sb.append(mark(contacts)).append(" 연락처  ← 저장된 사람의 번호도 확인\n")
        }
        sb.append(mark(overlay)).append(" 다른 앱 위에 표시  ← 경고화면 필수\n")
        sb.append(mark(exact)).append(" 알람 및 리마인더  ← 정확한 시간 필수\n")
        sb.append(mark(battery)).append(" 배터리 최적화 해제\n")
        sb.append("보호자 알림: 2차 경고 화면에서 문자 작성 화면을 열어줍니다 (별도 권한 불필요)")

        val screeningMissing = screeningSupported && !screening
        if (!overlay || !exact || !battery || screeningMissing) {
            sb.append("\n\n아래 버튼으로 꺼진 권한을 켜주세요.")
        }
        if (screeningMissing) {
            sb.append("\n발신자 정보 앱으로 설정하지 않으면 상대 번호를 알 수 없어, 보호자와의 통화도 감시됩니다.")
        }
        if (screeningSupported && !contacts) {
            sb.append("\n연락처 권한이 없으면 연락처에 저장된 사람(보호자 포함)의 번호를 알 수 없어, " +
                    "보호자와의 통화도 감시됩니다. 설정 > 애플리케이션 > 서로지킴 > 권한 에서 켜주세요.")
        }
        if (!screeningSupported) {
            sb.append("\n이 안드로이드 버전에서는 상대 번호 확인 기능을 지원하지 않습니다.")
        }

        tvPermission.text = sb.toString()
    }

    private fun mark(ok: Boolean) = if (ok) "[O]" else "[X]"

    /** 안드로이드 10(API 29) 이상에서만 "발신자 정보 및 스팸 방지 앱" 역할을 쓸 수 있다 */
    private fun isCallScreeningSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 서로지킴이 "발신자 정보 및 스팸 방지 앱" 역할을 가졌는지 (READ_CALL_LOG 없이 번호를 얻는 방법) */
    private fun hasCallScreeningRole(): Boolean {
        if (!isCallScreeningSupported()) return false
        val rm = getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            toast("이 버전에서는 별도 설정이 필요 없습니다")
            return
        }

        if (isIgnoringBattery()) {
            toast("이미 배터리 최적화가 해제되어 있습니다")
            return
        }

        try {
            val i = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i)
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i)
                toast("목록에서 '서로지킴'을 찾아 '최적화 안 함'으로 바꿔주세요")
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        openAppDetails()
        toast("배터리 항목에서 '제한 없음'으로 바꿔주세요")
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
            Manifest.permission.READ_CONTACTS   // 연락처에 저장된 사람의 전화도 번호를 받기 위해
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQ_RUNTIME_PERMISSIONS)
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

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CALL_SCREENING_ROLE) {
            refreshStatus()
        }
    }

    /** "발신자 정보 및 스팸 방지 앱" 역할을 요청한다 (READ_CALL_LOG 없이 상대 번호를 얻는 방법) */
    private fun requestCallScreeningRole() {
        if (!isCallScreeningSupported()) {
            toast("이 기능은 안드로이드 10 이상에서 지원됩니다")
            return
        }
        if (hasCallScreeningRole()) {
            toast("이미 설정되어 있습니다")
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        val intent = rm?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        if (intent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_CALL_SCREENING_ROLE)
        } else {
            toast("설정 > 앱 > 기본 앱 에서 '발신자 정보 및 스팸 방지 앱'을 서로지킴으로 바꿔주세요")
            openAppDetails()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
