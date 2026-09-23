package com.parentcare.callguard

import android.content.Context

/**
 * SharedPreferences 기반 간단한 설정 저장소.
 * - 자녀 번호
 * - 화이트리스트(예외) 번호 목록, 콤마로 구분
 * - 마지막 수신 번호(RINGING 시점에 저장했다가 OFFHOOK 때 사용)
 */
object PrefsHelper {

    private const val PREFS_NAME = "call_guard_prefs"
    private const val KEY_CHILD_NUMBER = "child_number"
    private const val KEY_WHITELIST = "whitelist_numbers"
    private const val KEY_LAST_NUMBER = "last_number"

    fun setChildNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_CHILD_NUMBER, number).apply()
    }

    fun getChildNumber(context: Context): String {
        return prefs(context).getString(KEY_CHILD_NUMBER, "") ?: ""
    }

    fun setWhitelistRaw(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_WHITELIST, raw).apply()
    }

    fun getWhitelistRaw(context: Context): String {
        return prefs(context).getString(KEY_WHITELIST, "") ?: ""
    }

    fun isWhitelisted(context: Context, number: String): Boolean {
        val raw = getWhitelistRaw(context)
        if (raw.isBlank()) return false
        val list = raw.split(",").map { it.trim().filter { c -> c.isDigit() } }
        val normalized = number.filter { it.isDigit() }
        return list.any { it.isNotEmpty() && normalized.endsWith(it) }
    }

    fun setLastNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_LAST_NUMBER, number).apply()
    }

    fun getLastNumber(context: Context): String {
        return prefs(context).getString(KEY_LAST_NUMBER, "알수없음") ?: "알수없음"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
