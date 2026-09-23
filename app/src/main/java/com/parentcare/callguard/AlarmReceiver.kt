package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TYPE_FIRST = "com.parentcare.callguard.WARN_FIRST"
        const val TYPE_SECOND = "com.parentcare.callguard.WARN_SECOND"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_SECONDS = "extra_seconds"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val number = intent.getStringExtra(EXTRA_NUMBER) ?: "알 수 없는 번호"
            val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)

            when (intent.action) {
                TYPE_FIRST -> {
                    Log.d(TAG, "1차 경고 (${seconds}초)")
                    NotificationHelper.showFirstWarning(context, seconds)
                }
                TYPE_SECOND -> {
                    Log.d(TAG, "2차 경고 (${seconds}초)")
                    NotificationHelper.showSecondWarning(context, number, seconds)
                    SmsHelper.notifyChild(context, number, seconds)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "경고 처리 오류", e)
        }
    }
}
