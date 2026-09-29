package com.parentcare.callguard

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * 경고 화면(WarningActivity)과 오버레이 창(WarningOverlayService)이 함께 쓰는 로직.
 * 두 화면은 같은 레이아웃(activity_warning)을 쓰므로 문구·버튼 동작도 한 곳에서 관리한다.
 */
object WarningUi {

    private const val TAG = "WarningUi"

    /**
     * @param root      activity_warning 레이아웃이 들어 있는 뷰
     * @param number    통화 상대 번호(모르면 PrefsHelper.UNKNOWN_NUMBER)
     * @param seconds   지금까지 통화한 시간(초)
     * @param onDismiss 버튼을 눌러 경고를 닫아야 할 때 호출 (Activity 는 finish, 오버레이는 서비스 종료)
     */
    fun bind(context: Context, root: View, number: String, seconds: Int, onDismiss: () -> Unit) {
        val guardianCount = PrefsHelper.getGuardianList(context).size

        root.findViewById<TextView>(R.id.tv_warning_message).text = buildString {
            append("지금 이런 이야기가 오가고 있나요?\n\n")
            append("· 송금, 계좌번호, 카드번호를 요구한다\n")
            append("· '가족', '경찰', '검찰', '금융기관'을 사칭한다\n")
            append("· 투자 수익이나 대출을 권유한다\n")
            append("· \"아무에게도 말하지 마세요\"라고 했다\n\n")
            append("하나라도 해당되면 보이스피싱입니다.\n")
            append("지금 끊고 보호자와 상의하세요.\n\n")
            if (guardianCount > 0) {
                append("아래 버튼으로 보호자 ${guardianCount}명에게 바로 알릴 수 있습니다.")
            }
        }

        // 세 버튼 모두 다른 앱(통화 화면·문자 앱)을 새로 띄운다.
        // 오버레이 창(WarningOverlayService)은 다른 앱 위에 항상 떠 있는 창이라, 먼저 닫지 않으면
        // 새로 뜬 화면을 오버레이가 덮어 터치가 먹히지 않을 수 있다. 그래서 onDismiss() 를 먼저 부른다.
        // 잠금 화면 위의 WarningActivity 에서는 잠금부터 풀어야 다른 앱이 뜬다 → openAfterUnlock 참고.

        // 지금 바로 전화 끊기 → 통화 화면을 앞으로 가져온다 (거기서 종료 버튼만 누르면 됨)
        root.findViewById<Button>(R.id.btn_hangup).setOnClickListener {
            openAfterUnlock(context, onDismiss) {
                openCallScreen(context)
                Toast.makeText(
                    context, "통화 화면의 종료 버튼을 눌러 전화를 끊어주세요", Toast.LENGTH_LONG
                ).show()
            }
        }

        // 보호자에게 문자 보내기 → 수신자·내용이 채워진 문자 작성 화면을 연다 ('보내기'는 직접 눌러야 함)
        root.findViewById<Button>(R.id.btn_sms).setOnClickListener {
            openAfterUnlock(context, onDismiss) {
                SmsHelper.composeGuardianAlert(context, number, seconds)
            }
        }

        // 경고 해제 → 보호자에게 보낼 안심 문자 작성 화면을 연다
        root.findViewById<Button>(R.id.btn_close).setOnClickListener {
            openAfterUnlock(context, onDismiss) {
                SmsHelper.composeSafeNotice(context)
            }
        }
    }

    /**
     * 경고를 닫고 다른 앱 화면을 연다. 화면이 잠겨 있으면 잠금부터 푼다.
     *
     * 문자 앱·통화 화면 같은 다른 앱은 보안 잠금(PIN·패턴·생체) 위에 뜰 수 없어서, 그냥 열면 시스템이
     * 경고 화면을 치우고 잠금해제를 요구한다(2026-09-29 실기기 확인). 그래서 경고 화면을 띄운 채로
     * 잠금해제 창을 먼저 부르고, 풀리면 그때 경고를 닫고 이어서 연다. 취소하면 경고 화면이 그대로 남는다.
     * 오버레이는 잠금이 풀린 상태에서만 뜨므로(NotificationHelper.showWarningScreen) 여기서는 바로 연다.
     */
    private fun openAfterUnlock(context: Context, onDismiss: () -> Unit, open: () -> Unit) {
        val activity = context as? Activity
        val km = context.getSystemService(KeyguardManager::class.java)
        if (activity == null || km == null || !km.isKeyguardLocked) {
            onDismiss()
            open()
            return
        }

        Toast.makeText(context, "잠금을 풀면 바로 이어서 열립니다", Toast.LENGTH_SHORT).show()
        km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                onDismiss()
                open()
            }

            override fun onDismissCancelled() {
                Toast.makeText(
                    context, "잠금이 풀리지 않았습니다. 버튼을 다시 눌러주세요", Toast.LENGTH_LONG
                ).show()
            }

            override fun onDismissError() {
                // 잠금해제 창을 못 띄웠으면 예전 방식대로 연다(시스템이 잠금해제를 요구함)
                Log.w(TAG, "잠금해제 요청 실패, 바로 열기로 대체")
                onDismiss()
                open()
            }
        })
    }

    /**
     * 통화(다이얼러) 화면을 앞으로 가져온다.
     * 1) 통화 중이면 TelecomManager.showInCallScreen — 진행 중인 통화 화면으로 바로 이동
     * 2) 통화 중이 아니거나 실패하면 다이얼러(전화 앱)를 연다
     * 3) 그것도 안 되면 홈 화면으로 나간다
     */
    fun openCallScreen(context: Context) {
        try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (tm.isInCall) {
                tm.showInCallScreen(false)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "통화 화면 열기 실패, 다이얼러로 대체", e)
        }

        try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (e: Exception) {
            Log.w(TAG, "다이얼러 열기 실패, 홈으로 대체", e)
        }

        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "홈 화면 이동 실패", e)
        }
    }
}
