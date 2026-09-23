package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 예약된 시간에 시스템이 깨워주는 리시버.
 * 1차: 가벼운 알림 / 2차: 전체화면 경고 + 자녀 SMS
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TYPE_FIRST = "com.parentcare.callguard.WARN_FIRST"
        const val TYPE_SECOND = "com.parentcare.callguard.WARN_SECOND"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_MINUTES = "extra_minutes"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val number = intent.getStringExtra(EXTRA_NUMBER) ?: "알 수 없는 번호"
            val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)

            when (intent.action) {
                TYPE_FIRST -> {
                    Log.d(TAG, "1차 경고 (${minutes}분)")
                    NotificationHelper.showFirstWarning(context, minutes)
                }
                TYPE_SECOND -> {
                    Log.d(TAG, "2차 경고 (${minutes}분)")
                    NotificationHelper.showSecondWarning(context, number, minutes)
                    SmsHelper.notifyChild(context, number, minutes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "경고 처리 오류", e)
        }
    }
}
