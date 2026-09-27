package com.parentcare.callguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 등록된 보호자 전원에게 알림 문자를 보낸다.
 */
object SmsHelper {

    private const val TAG = "SmsHelper"
    private const val CHANNEL_RESULT = "seorojikim_sms_result"

    fun notifyGuardians(context: Context, callingNumber: String, elapsedSeconds: Int) {
        val guardians = PrefsHelper.getGuardianList(context)
        val timeText = PrefsHelper.formatSeconds(elapsedSeconds)
        val myName = PrefsHelper.getMyName(context).ifBlank { "보호 대상자" }

        if (guardians.isEmpty()) {
            showResult(context, "문자 미발송", "등록된 보호자가 없습니다.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showResult(context, "문자 미발송", "문자 발송 권한이 없습니다.")
            return
        }

        val message = "[서로지킴]\n" +
                "${myName}님이 ${timeText}째 통화 중입니다.\n" +
                "상대 번호: $callingNumber\n" +
                "안부 확인 부탁드려요."

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
                Log.d(TAG, "발송 완료 -> $number")
            } catch (e: Exception) {
                failed++
                errors.append("$number: ${e.message}\n")
                Log.e(TAG, "발송 실패 -> $number", e)
            }
        }

        val title = if (failed == 0) "문자 발송함 ($success 명)" else "일부 발송 실패"
        val body = buildString {
            append("성공 ${success}건 / 실패 ${failed}건\n")
            append("대상: ${guardians.joinToString(", ")}")
            if (errors.isNotEmpty()) append("\n\n$errors")
        }
        showResult(context, title, body)
    }

    fun sendTest(context: Context) {
        notifyGuardians(context, "0212345678(테스트)", PrefsHelper.getSecondSeconds(context))
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
                .setSmallIcon(android.R.drawable.ic_dialog_email)
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
