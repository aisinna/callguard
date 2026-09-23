package com.parentcare.callguard

import android.content.Context
import android.telephony.SmsManager

/**
 * 통화 10분 초과 시 자녀에게 SMS로 알림을 보낸다.
 * (서버/FCM 없이 가장 단순하게 구현한 1차 버전. 추후 앱 푸시로 고도화 가능)
 */
object SmsHelper {

    fun notifyChild(context: Context, callingNumber: String, elapsedMinutes: Int) {
        val childNumber = PrefsHelper.getChildNumber(context)
        if (childNumber.isBlank()) return

        val message = "[보이스피싱 안심콜]\n" +
                "부모님이 $elapsedMinutes 분째 통화 중입니다.\n" +
                "상대 번호: $callingNumber\n" +
                "혹시 모르니 안부 확인 부탁드려요."

        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(childNumber, null, parts, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
