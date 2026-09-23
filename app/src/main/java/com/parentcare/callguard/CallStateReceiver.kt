package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        private var lastState: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != "android.intent.action.PHONE_STATE") return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (state == lastState) return
            lastState = state

            Log.d(TAG, "통화 상태: $state / 번호: $incomingNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (!incomingNumber.isNullOrBlank()) {
                        PrefsHelper.setLastNumber(context, incomingNumber)
                    }
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val number = PrefsHelper.getLastNumber(context)
                    if (PrefsHelper.isWhitelisted(context, number)) {
                        Log.d(TAG, "화이트리스트 번호, 감시 안 함")
                        return
                    }
                    AlarmScheduler.scheduleWarnings(context, number)
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    AlarmScheduler.cancelAll(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "오류", e)
        }
    }
}
