package com.parentcare.callguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 보호자 전원에게 문자를 보낸다.
 * - notifyGuardians : 장시간 통화 경고
 * - sendSafeNotice  : 본인이 '경고 해제'를 눌렀을 때 보내는 안심 문자
 */
object SmsHelper {

    private const val TAG = "SmsHelper"
    private const val CHANNEL_RESULT = "seorojikim_sms_result"

    /** 2차 경고 시점에 자동 발송 */
    fun notifyGuardians(
        context: Context,
        callingNumber: String,
        elapsedSeconds: Int,
        toastOnSuccess: Boolean = false
    ) {
        val timeText = PrefsHelper.formatSeconds(elapsedSeconds)
        val myName = PrefsHelper.getMyName(context).ifBlank { "보호 대상자" }

        val message = "[서로지킴]\n" +
                "${myName}님이 ${timeText}째 통화 중입니다.\n" +
                "상대 번호: $callingNumber\n" +
                "안부 확인 부탁드려요."

        sendToAll(context, message, "경고 문자", toastOnSuccess)
    }

    /** 본인이 '경고 해제'를 눌렀을 때 발송 */
    fun sendSafeNotice(context: Context) {
        val myName = PrefsHelper.getMyName(context).ifBlank { "보호 대상자" }

        val message = "[서로지킴]\n" +
                "안심하세요. ${myName}님이 직접 확인했습니다.\n" +
                "아는 사람과 통화 중이니 걱정하지 않으셔도 됩니다."

        sendToAll(context, message, "안심 문자")
    }

    /** 테스트 버튼용 — 성공하면 알림 대신 화면에 잠깐 뜨는 토스트로만 알려준다 */
    fun sendTest(context: Context) {
        notifyGuardians(
            context, "0212345678(테스트)", PrefsHelper.getSecondSeconds(context),
            toastOnSuccess = true
        )
    }

    // ----- 공통 발송 처리 -----

    private fun sendToAll(
        context: Context,
        message: String,
        label: String,
        toastOnSuccess: Boolean = false
    ) {
        val guardians = PrefsHelper.getGuardianList(context)

        if (guardians.isEmpty()) {
            showResult(context, "$label 미발송", "등록된 보호자가 없습니다.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showResult(context, "$label 미발송", "문자 발송 권한이 없습니다.")
            return
        }

        var success = 0
        var failed = 0
        val errors = StringBuilder()

        for (number in guardians) {
            try {
                val smsManager = getSmsManager(context)
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(number, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(number, null, message, null, null)
                }
                success++
                Log.d(TAG, "$label 발송 요청 완료")
            } catch (e: Exception) {
                failed++
                errors.append("$number: ${e.message}\n")
                Log.e(TAG, "$label 발송 실패", e)
            }
        }

        // 전부 성공하면 알림을 띄우지 않는다 (알림이 너무 많다는 피드백 반영).
        // 단, 문자 발송 실패는 보호자에게 경고가 닿지 않았다는 뜻이라 반드시 알림으로 알린다.
        if (failed == 0) {
            if (toastOnSuccess) {
                Toast.makeText(context, "테스트 문자를 보냈습니다 (${success}명)", Toast.LENGTH_LONG).show()
            }
            return
        }

        val title = if (success == 0) "$label 발송 실패" else "$label 일부 실패"
        val body = buildString {
            append("성공 ${success}건 / 실패 ${failed}건\n")
            append("대상: ${guardians.joinToString(", ")}")
            if (errors.isNotEmpty()) append("\n\n$errors")
        }
        showResult(context, title, body)
    }

    private fun getSmsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun showResult(context: Context, title: String, body: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_RESULT, "문자 발송 결과", NotificationManager.IMPORTANCE_DEFAULT
                )
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(ch)
            }
            val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_stat_callguard)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(2100, n)
        } catch (e: Exception) {
            Log.e(TAG, "결과 알림 실패", e)
        }
    }
}
