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
 * 발신 통화: (RINGING 없이) OFFHOOK(발신 시작) → IDLE
 *
 * 직전 상태를 SharedPreferences 에 저장해 두므로, 프로세스가 종료돼도
 * "RINGING 다음의 OFFHOOK = 수신, IDLE 다음의 OFFHOOK = 발신" 판정이 유지된다.
 *
 * 발신 번호는 PROCESS_OUTGOING_CALLS 권한이 있어야 알 수 있는데, 이 권한은
 * 보이스피싱 악성앱의 대표 권한이라 기기 보안 기능이 설치를 차단한다.
 * 그래서 발신 통화는 번호를 '모름'으로 두고 감시한다.
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            handlePhoneState(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "오류", e)
        }
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
                    // 발신: 이전 통화 번호가 남아 있으면 안 되므로 '모름'으로 처리
                    PrefsHelper.UNKNOWN_NUMBER
                }

                if (number == PrefsHelper.UNKNOWN_NUMBER) {
                    PrefsHelper.clearLastNumber(context)
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
                AlarmScheduler.cancelAll(context)
            }
        }
    }
}
