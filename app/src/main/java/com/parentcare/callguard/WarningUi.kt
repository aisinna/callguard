package com.parentcare.callguard

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
     * @param onDismiss 버튼을 눌러 경고를 닫아야 할 때 호출 (Activity 는 finish, 오버레이는 서비스 종료)
     */
    fun bind(context: Context, root: View, onDismiss: () -> Unit) {
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
                append("보호자 ${guardianCount}명에게 이미 알림을 보냈습니다.")
            }
        }

        // 지금 바로 전화 끊기 → 통화 화면을 앞으로 가져온다 (거기서 종료 버튼만 누르면 됨)
        root.findViewById<Button>(R.id.btn_hangup).setOnClickListener {
            openCallScreen(context)
            Toast.makeText(
                context, "통화 화면의 종료 버튼을 눌러 전화를 끊어주세요", Toast.LENGTH_LONG
            ).show()
            onDismiss()
        }

        // 경고 해제 → 보호자에게 안심 문자
        root.findViewById<Button>(R.id.btn_close).setOnClickListener {
            SmsHelper.sendSafeNotice(context)
            Toast.makeText(context, "보호자에게 안심 문자를 보냈습니다", Toast.LENGTH_LONG).show()
            onDismiss()
        }
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
