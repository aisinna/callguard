package com.parentcare.callguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 걸려 온 번호가 연락처에 저장돼 있는지만 확인한다.
 *
 * 연락처의 _ID 한 칸만 조회해 "있다/없다"만 판단한다. 이름 등 다른 내용은 읽지 않고, 결과도 저장하지 않는다.
 * 번호 비교(국가번호·하이픈 차이 등)는 시스템의 PhoneLookup 이 알아서 처리한다.
 */
object ContactLookup {

    private const val TAG = "ContactLookup"

    fun isSavedContact(context: Context, number: String): Boolean {
        if (number.isBlank() || number == PrefsHelper.UNKNOWN_NUMBER) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            // 확인에 실패하면 '연락처 아님'으로 보고 감시한다(안전 쪽)
            Log.w(TAG, "연락처 확인 실패", e)
            false
        }
    }
}
