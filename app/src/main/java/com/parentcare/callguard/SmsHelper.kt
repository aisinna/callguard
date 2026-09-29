package com.parentcare.callguard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * 보호자에게 상황을 알린다.
 *
 * SEND_SMS 로 직접 문자를 보내지 않는다. 구글 플레이는 이 권한을 기본 문자 앱이 아니면
 * 원칙적으로 제한하기 때문이다("SMS 및 통화 기록 권한" 정책). 대신 상대·내용을 채운 문자
 * 작성 화면을 띄우고, 마지막 '보내기'는 사용자가 직접 누른다. 권한이 전혀 필요 없다.
 */
object SmsHelper {

    private const val TAG = "SmsHelper"

    /** 경고 화면의 '보호자에게 문자 보내기' 버튼에서 호출 */
    fun composeGuardianAlert(context: Context, callingNumber: String, elapsedSeconds: Int) {
        val timeText = PrefsHelper.formatSeconds(elapsedSeconds)
        val myName = PrefsHelper.getMyName(context).ifBlank { "보호 대상자" }

        val message = "[서로지킴]\n" +
                "${myName}님이 ${timeText}째 통화 중입니다.\n" +
                "상대 번호: $callingNumber\n" +
                "안부 확인 부탁드려요."

        compose(context, message)
    }

    /** 경고 화면의 '경고 해제' 버튼에서 호출 */
    fun composeSafeNotice(context: Context) {
        val myName = PrefsHelper.getMyName(context).ifBlank { "보호 대상자" }

        val message = "[서로지킴]\n" +
                "안심하세요. ${myName}님이 직접 확인했습니다.\n" +
                "아는 사람과 통화 중이니 걱정하지 않으셔도 됩니다."

        compose(context, message)
    }

    /** 설정 화면의 '문자 테스트' 버튼에서 호출 */
    fun composeTest(context: Context) {
        composeGuardianAlert(context, "0212345678(테스트)", PrefsHelper.getSecondSeconds(context))
    }

    /**
     * 등록된 보호자 전원을 수신자로 채운 문자 작성 화면을 띄운다.
     *
     * smsto: 주소에 번호를 ';' 로 나열하면 여러 명을 한 번에 채워주는 문자 앱이 많지만,
     * 제조사 문자 앱에 따라 첫 번째 번호만 채워질 수 있다 — 그런 경우에도 문자 자체는
     * 정상적으로 작성되므로 최소한 대표 보호자 한 명에게는 보낼 수 있다.
     */
    private fun compose(context: Context, message: String) {
        val guardians = PrefsHelper.getGuardianList(context)
        if (guardians.isEmpty()) {
            Toast.makeText(context, "등록된 보호자가 없습니다", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val address = guardians.joinToString(";")
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$address")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "문자 작성 화면 실행 실패", e)
            Toast.makeText(context, "문자 앱을 열 수 없습니다", Toast.LENGTH_LONG).show()
        }
    }
}
