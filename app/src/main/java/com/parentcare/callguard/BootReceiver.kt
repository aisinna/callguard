package com.parentcare.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 기기 재부팅 후에도 통화 감지가 계속 동작하도록 한다.
 * (통화 감지 자체는 CallStateReceiver가 시스템 브로드캐스트로 항상 받으므로,
 *  여기서는 별도 초기화가 필요할 경우를 대비한 훅만 남겨둔다.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            // 필요 시 초기 설정 확인 로직을 여기에 추가할 수 있다.
            // 현재 구조는 CallStateReceiver가 매니페스트에 등록되어 있어
            // 재부팅 후에도 시스템이 자동으로 브로드캐스트를 전달한다.
        }
    }
}
