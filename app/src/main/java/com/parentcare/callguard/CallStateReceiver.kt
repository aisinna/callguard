package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 기본 전화 앱(다이얼러)은 건드리지 않고,
 * 시스템 브로드캐스트로 통화 상태(수신/응답/종료)만 감지한다.
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
        // 마지막 상태를 기억해서 중복 트리거를 막는다
        private var lastState: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state == lastState) return
        lastState = state

        Log.d(TAG, "통화 상태 변경: $state, 번호: $incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // 벨 울림 - 아직 통화 시작 아님, 번호만 저장해둔다
                PrefsHelper.setLastNumber(context, incomingNumber ?: "알수없음")
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // 통화 연결됨(발신 연결 또는 수신 응답) -> 모니터링 시작
                val number = PrefsHelper.getLastNumber(context)
                val serviceIntent = Intent(context, CallMonitorService::class.java).apply {
                    action = CallMonitorService.ACTION_START
                    putExtra(CallMonitorService.EXTRA_NUMBER, number)
                }
                context.startForegroundService(serviceIntent)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // 통화 종료 -> 모니터링 중지, 예약된 알림 취소
                val stopIntent = Intent(context, CallMonitorService::class.java).apply {
                    action = CallMonitorService.ACTION_STOP
                }
                context.startService(stopIntent)
            }
        }
    }
}
