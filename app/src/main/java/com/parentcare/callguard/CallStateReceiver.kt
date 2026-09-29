package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * 통화 상태를 감시해 경고 알람을 예약/취소한다.
 *
 * 감시 대상은 '걸려 온 전화'(수신)뿐이다. 내가 건 전화는 감시하지 않는다.
 *
 * 수신 통화: RINGING → OFFHOOK(받음, 알람 예약) → IDLE(알람 취소)
 * 발신 통화: (RINGING 없이) OFFHOOK → 감시 안 함 → IDLE
 *
 * 상대 번호는 이 리시버가 아니라 CallGuardScreeningService 가 채운다(READ_CALL_LOG 없이
 * 번호를 얻는 방법). 이 리시버는 통화 상태 전환과 타이밍만 담당한다.
 *
 * 직전 상태를 SharedPreferences 에 저장해 두므로, 프로세스가 종료돼도
 * "RINGING 다음의 OFFHOOK = 수신, IDLE 다음의 OFFHOOK = 발신" 판정이 유지된다.
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

                // 상대 번호는 여기서 읽지 않는다. READ_CALL_LOG 없이는 이 인텐트에 번호가 실리지 않고,
                // CallGuardScreeningService 가 전화가 오는 시점에 이미 PrefsHelper 에 저장해 두었다.
                PrefsHelper.setLastCallState(context, state)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (prev == TelephonyManager.EXTRA_STATE_OFFHOOK) return   // 중복
                PrefsHelper.setLastCallState(context, state)
                if (!monitoring) return

                // RINGING 을 거쳐 왔으면 '걸려 온 전화'(수신), 아니면 '내가 건 전화'(발신)
                val incoming = prev == TelephonyManager.EXTRA_STATE_RINGING
                if (!incoming) {
                    // 내가 건 전화는 감시하지 않는다.
                    // 혹시 남아 있을 이전 통화의 알람이 이번 통화 중에 울리지 않도록 함께 정리한다.
                    PrefsHelper.clearLastNumber(context)
                    AlarmScheduler.cancelAll(context)
                    Log.d(TAG, "발신 통화, 감시 안 함")
                    return
                }

                // 걸려 온 전화 — 번호를 못 읽었으면(발신번호 표시제한 등) '알수없음'으로 감시한다
                val number = PrefsHelper.getLastNumber(context)
                Log.d(TAG, "수신 통화 시작, " +
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
                WarningOverlayService.stop(context)   // 통화가 끝났으면 떠 있는 경고 오버레이도 닫는다
            }
        }
    }
}
