package com.parentcare.callguard

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * READ_CALL_LOG 없이 걸려 온 번호를 알아내는 방법.
 *
 * 구글 플레이는 READ_CALL_LOG 를 기본 전화·어시스턴트 앱에만 허용한다. 그 대신 사용자가
 * 서로지킴을 "발신자 정보 및 스팸 방지 앱"(통화 심사 역할)으로 지정하면, 전화가 오는 즉시
 * 이 서비스가 번호를 받는다. 이건 권한이 아니라 역할(role)이라 그 정책의 적용 대상이 아니다.
 *
 * 서로지킴은 스팸을 걸러내지 않는다 — 번호만 저장해 두고, 통화는 시스템 기본 동작 그대로
 * 두어야(허용해야) 한다. 그래서 onScreenCall 은 항상 "막지도 거절하지도 않음"으로 응답한다.
 * 이후 통화 시간 감시·화이트리스트 판정은 지금까지처럼 CallStateReceiver 가 한다.
 */
class CallGuardScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallGuardScreening"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            if (PrefsHelper.isMonitoring(applicationContext)) {
                val number = callDetails.handle?.schemeSpecificPart
                if (!number.isNullOrBlank()) {
                    PrefsHelper.setLastNumber(applicationContext, number)
                    Log.d(TAG, "발신자 번호 확인됨")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "번호 확인 실패", e)
        } finally {
            // 통화를 막거나 소리를 죽이지 않는다. 번호만 확인할 뿐, 통화 자체는 그대로 둔다.
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            )
        }
    }
}
