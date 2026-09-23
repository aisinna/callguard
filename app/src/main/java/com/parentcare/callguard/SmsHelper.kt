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
 * 자녀에게 SMS 알림을 보낸다.
 * 발송 성공/실패 결과를 알림으로 보여줘서 왜 안 갔는지 바로 알 수 있게 한다.
 */
object SmsHelper {

    private const val TAG = "SmsHelper"
    private const val CHANNEL_RESULT = "call_guard_sms_result_v2"

    fun notifyChild(context: Context, callingNumber: String, elapsedSeconds: Int) {
        val childNumber = PrefsHelper.getChildNumber(context).trim()
        val timeText = PrefsHelper.formatSeconds(elapsedSeconds)

        if (childNumber.isBlank()) {
            showResult(context, "문자 미발송", "자녀 연락처가 등록되어 있지 않습니다.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showResult(context, "문자 미발송", "문자 발송 권한(SEND_SMS)이 없습니다.")
            return
        }

        val message = "[보이스피싱 안심콜]\n" +
                "부모님이 ${timeText}째 통화 중입니다.\n" +
                "상대 번호: $callingNumber\n" +
                "안부 확인 부탁드려요."

        try {
            val smsManager = getSmsManager(context)
            val parts = smsManager.divideMessage(message)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(childNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(childNumber, null, message, null, null)
            }

            Log.d(TAG, "문자 발송 요청 완료 -> $childNumber")
            showResult(context, "문자 발송함", "$childNumber 로 알림을 보냈습니다.")

        } catch (e: Exception) {
            Log.e(TAG, "문자 발송 실패", e)
            showResult(context, "문자 발송 실패", "${e.message}")
        }
    }

    /** 테스트 버튼용 */
    fun sendTest(context: Context) {
        notifyChild(context, "[휴대전화번호_REDACTED](테스트)", PrefsHelper.getSecondSeconds(context))
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
            Log.e(TAG, "결과 알림 표시 실패", e)
        }
    }
}
