package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * 기기 재부팅 후 통화 상태를 초기화한다.
 *
 * 통화 상태(직전 상태)를 저장해 두기 때문에, 통화 중에 전원이 꺼지면
 * 저장값이 OFFHOOK 으로 남아 다음 통화가 '중복'으로 무시될 수 있다.
 * (통화 감지 자체는 CallStateReceiver 가 매니페스트에 등록돼 있어 재부팅 후에도 동작한다.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            PrefsHelper.setLastCallState(context, TelephonyManager.EXTRA_STATE_IDLE)
            PrefsHelper.clearLastNumber(context)
        }
    }
}
