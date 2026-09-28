package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 통화 상태를 감시해 경고 알람을 예약/취소한다.
 *
 * 수신 통화: RINGING(번호 저장) → OFFHOOK(받음) → IDLE
 * 발신 통화: NEW_OUTGOING_CALL(번호 저장) → OFFHOOK(발신 시작) → IDLE
 *
 * 직전 상태를 SharedPreferences 에 저장해 두므로, 프로세스가 종료돼도
 * "RINGING 다음의 OFFHOOK = 수신, IDLE 다음의 OFFHOOK = 발신" 판정이 유지된다.
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"

        // Intent.ACTION_NEW_OUTGOING_CALL 은 API 29 에서 deprecated 이나 아직 전달된다.
        private const val ACTION_NEW_OUTGOING_CALL = "android.intent.action.NEW_OUTGOING_CALL"

        /** 발신 번호를 받은 뒤 OFFHOOK 까지 허용하는 최대 시간 */
        private const val OUTGOING_MAX_AGE_MS = 60_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                ACTION_NEW_OUTGOING_CALL -> handleOutgoingNumber(context, intent)
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> handlePhoneState(context, intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "오류", e)
        }
    }

    /** 발신 직전에 오는 브로드캐스트 — 상대 번호를 잠시 보관한다 (통화 자체는 건드리지 않음) */
    private fun handleOutgoingNumber(context: Context, intent: Intent) {
        if (!PrefsHelper.isMonitoring(context)) return
        val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        if (number.isNullOrBlank()) return
        PrefsHelper.setPendingOutgoing(context, number)
        Log.d(TAG, "발신 번호 확인됨")
    }

    private fun handlePhoneState(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val monitoring = PrefsHelper.isMonitoring(context)
        val prev = PrefsHelper.getLastCallState(context)

        Log.d(TAG, "통화 상태: $prev -> $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // 통화 중 걸려온 두 번째 전화(통화 대기)는 무시
                if (prev == TelephonyManager.EXTRA_STATE_OFFHOOK) return

                // 같은 벨소리에 RINGING 이 두 번 오는 기기가 많다.
                // 번호는 두 번째에만 들어있는 경우가 있으므로 중복 제거하지 않는다.
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (monitoring && !number.isNullOrBlank()) {
                    PrefsHelper.setLastNumber(context, number)
                }
                PrefsHelper.setLastCallState(context, state)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (prev == TelephonyManager.EXTRA_STATE_OFFHOOK) return   // 중복
                PrefsHelper.setLastCallState(context, state)
                if (!monitoring) return

                val incoming = prev == TelephonyManager.EXTRA_STATE_RINGING
                val number = if (incoming) {
                    PrefsHelper.getLastNumber(context)
                } else {
                    // 발신: 이전 통화 번호가 아니라 이번 발신 번호를 사용한다
                    PrefsHelper.takePendingOutgoing(context, OUTGOING_MAX_AGE_MS)
                        ?: PrefsHelper.UNKNOWN_NUMBER
                }

                // 이후 화면/문자에서 쓰는 '현재 통화 번호'를 이번 통화 기준으로 맞춘다
                if (number == PrefsHelper.UNKNOWN_NUMBER) {
                    PrefsHelper.clearLastNumber(context)
                } else {
                    PrefsHelper.setLastNumber(context, number)
                }

                Log.d(TAG, "통화 시작: ${if (incoming) "수신" else "발신"}, " +
                        "번호 ${if (number == PrefsHelper.UNKNOWN_NUMBER) "모름" else "확인됨"}")

                if (PrefsHelper.isWhitelisted(context, number)) {
                    Log.d(TAG, "화이트리스트 번호, 감시 안 함")
                    return
                }
                AlarmScheduler.scheduleWarnings(context, number)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // 감시 여부와 관계없이 상태는 항상 갱신해야 다음 통화 판정이 어긋나지 않는다
                PrefsHelper.setLastCallState(context, state)
                PrefsHelper.clearLastNumber(context)
                PrefsHelper.clearPendingOutgoing(context)
                AlarmScheduler.cancelAll(context)
            }
        }
    }
}
