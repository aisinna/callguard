package com.parentcare.callguard

import android.content.Context

object PrefsHelper {

    private const val PREFS_NAME = "call_guard_prefs"
    private const val KEY_CHILD_NUMBER = "child_number"
    private const val KEY_WHITELIST = "whitelist_numbers"
    private const val KEY_LAST_NUMBER = "last_number"
    private const val KEY_FIRST_SECONDS = "first_seconds"
    private const val KEY_SECOND_SECONDS = "second_seconds"
    private const val KEY_MONITORING = "monitoring_enabled"

    // 기본값: 8분 / 10분 (초 단위)
    const val DEFAULT_FIRST_SEC = 480
    const val DEFAULT_SECOND_SEC = 600

    // ----- 감시 on/off -----

    fun setMonitoring(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING, enabled).apply()
    }

    fun isMonitoring(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MONITORING, false)
    }

    // ----- 1차 알림 시간 (초) -----

    fun setFirstSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_FIRST_SECONDS, seconds).apply()
    }

    fun getFirstSeconds(context: Context): Int {
        return prefs(context).getInt(KEY_FIRST_SECONDS, DEFAULT_FIRST_SEC)
    }

    // ----- 2차 경고 시간 (초) -----

    fun setSecondSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_SECOND_SECONDS, seconds).apply()
    }

    fun getSecondSeconds(context: Context): Int {
        return prefs(context).getInt(KEY_SECOND_SECONDS, DEFAULT_SECOND_SEC)
    }

    // ----- 초 -> "n분 n초" 문자열 -----

    fun formatSeconds(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return when {
            m > 0 && s > 0 -> "${m}분 ${s}초"
            m > 0 -> "${m}분"
            else -> "${s}초"
        }
    }

    // ----- 자녀 번호 -----

    fun setChildNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_CHILD_NUMBER, number).apply()
    }

    fun getChildNumber(context: Context): String {
        return prefs(context).getString(KEY_CHILD_NUMBER, "") ?: ""
    }

    // ----- 화이트리스트 -----

    fun setWhitelistRaw(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_WHITELIST, raw).apply()
    }

    fun getWhitelistRaw(context: Context): String {
        return prefs(context).getString(KEY_WHITELIST, "") ?: ""
    }

    fun isWhitelisted(context: Context, number: String): Boolean {
        val raw = getWhitelistRaw(context)
        if (raw.isBlank()) return false
        val list = raw.split(",").map { item -> item.trim().filter { c -> c.isDigit() } }
        val normalized = number.filter { it.isDigit() }
        if (normalized.isEmpty()) return false
        return list.any { it.isNotEmpty() && normalized.endsWith(it) }
    }

    // ----- 마지막 통화 번호 -----

    fun setLastNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_LAST_NUMBER, number).apply()
    }

    fun getLastNumber(context: Context): String {
        return prefs(context).getString(KEY_LAST_NUMBER, "알수없음") ?: "알수없음"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
