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
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val number = intent.getStringExtra(EXTRA_NUMBER) ?: "알 수 없는 번호"

            when (intent.action) {
                TYPE_FIRST -> {
                    Log.d(TAG, "1차 경고")
                    NotificationHelper.showFirstWarning(context)
                }
                TYPE_SECOND -> {
                    Log.d(TAG, "2차 경고")
                    val elapsed = AlarmScheduler.SECOND_MINUTES.toInt()
                    NotificationHelper.showSecondWarning(context, number, elapsed)
                    SmsHelper.notifyChild(context, number, elapsed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "경고 처리 오류", e)
        }
    }
}
