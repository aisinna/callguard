package com.parentcare.callguard

import android.content.Context

object PrefsHelper {

    private const val PREFS_NAME = "call_guard_prefs"
    private const val KEY_MY_NAME = "my_name"
    private const val KEY_GUARDIANS = "guardian_numbers"
    private const val KEY_WHITELIST = "whitelist_numbers"
    private const val KEY_LAST_NUMBER = "last_number"
    private const val KEY_FIRST_SECONDS = "first_seconds"
    private const val KEY_SECOND_SECONDS = "second_seconds"
    private const val KEY_MONITORING = "monitoring_enabled"
    private const val KEY_LAST_CALL_STATE = "last_call_state"
    private const val KEY_PENDING_OUT_NUMBER = "pending_outgoing_number"
    private const val KEY_PENDING_OUT_AT = "pending_outgoing_at"

    /** 번호를 알 수 없을 때 쓰는 표시 문자열 */
    const val UNKNOWN_NUMBER = "알수없음"

    const val DEFAULT_FIRST_SEC = 480   // 8분
    const val DEFAULT_SECOND_SEC = 600  // 10분

    // ----- 내 이름 -----

    fun setMyName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_MY_NAME, name).apply()
    }

    fun getMyName(context: Context): String {
        return prefs(context).getString(KEY_MY_NAME, "") ?: ""
    }

    // ----- 동작 on/off -----

    fun setMonitoring(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING, enabled).apply()
    }

    fun isMonitoring(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MONITORING, false)
    }

    // ----- 시간 설정 (초) -----

    fun setFirstSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_FIRST_SECONDS, seconds).apply()
    }

    fun getFirstSeconds(context: Context): Int {
        return prefs(context).getInt(KEY_FIRST_SECONDS, DEFAULT_FIRST_SEC)
    }

    fun setSecondSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_SECOND_SECONDS, seconds).apply()
    }

    fun getSecondSeconds(context: Context): Int {
        return prefs(context).getInt(KEY_SECOND_SECONDS, DEFAULT_SECOND_SEC)
    }

    fun formatSeconds(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return when {
            m > 0 && s > 0 -> "${m}분 ${s}초"
            m > 0 -> "${m}분"
            else -> "${s}초"
        }
    }

    // ----- 보호자 목록 (콤마 구분, 최대 3명) -----

    fun setGuardiansRaw(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_GUARDIANS, raw).apply()
    }

    fun getGuardiansRaw(context: Context): String {
        return prefs(context).getString(KEY_GUARDIANS, "") ?: ""
    }

    fun getGuardianList(context: Context): List<String> {
        val raw = getGuardiansRaw(context)
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
    }

    // ----- 화이트리스트 -----

    fun setWhitelistRaw(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_WHITELIST, raw).apply()
    }

    fun getWhitelistRaw(context: Context): String {
        return prefs(context).getString(KEY_WHITELIST, "") ?: ""
    }

    /**
     * 전화번호를 비교용으로 정규화한다.
     * - 숫자만 남긴다.
     * - 국제 표기(+82 10-1234-5678 / 82-10-...)는 국내 표기(010...)로 바꾼다.
     */
    fun normalizeNumber(number: String): String {
        var d = number.filter { it.isDigit() }
        if (d.startsWith("82") && d.length >= 9) {   // 국내 번호는 항상 0으로 시작하므로 "82…"는 국가번호
            d = d.removePrefix("82")
            if (!d.startsWith("0")) d = "0$d"
        }
        return d
    }

    fun isWhitelisted(context: Context, number: String): Boolean {
        val normalized = normalizeNumber(number)
        if (normalized.isEmpty()) return false

        // 보호자 번호는 자동으로 예외 처리
        val guardians = getGuardianList(context).map { normalizeNumber(it) }
        if (guardians.any { it.isNotEmpty() && normalized.endsWith(it) }) return true

        val raw = getWhitelistRaw(context)
        if (raw.isBlank()) return false
        val list = raw.split(",").map { normalizeNumber(it.trim()) }
        return list.any { it.isNotEmpty() && normalized.endsWith(it) }
    }

    // ----- 현재(직전) 통화 번호 -----

    fun setLastNumber(context: Context, number: String) {
        prefs(context).edit().putString(KEY_LAST_NUMBER, number).apply()
    }

    fun getLastNumber(context: Context): String {
        val v = prefs(context).getString(KEY_LAST_NUMBER, "") ?: ""
        return v.ifBlank { UNKNOWN_NUMBER }
    }

    fun clearLastNumber(context: Context) {
        prefs(context).edit().remove(KEY_LAST_NUMBER).apply()
    }

    // ----- 통화 상태 (프로세스가 죽어도 유지되도록 저장) -----

    fun setLastCallState(context: Context, state: String) {
        prefs(context).edit().putString(KEY_LAST_CALL_STATE, state).apply()
    }

    /** 저장된 마지막 통화 상태. 없으면 IDLE 로 간주 */
    fun getLastCallState(context: Context): String {
        return prefs(context).getString(KEY_LAST_CALL_STATE, "IDLE") ?: "IDLE"
    }

    // ----- 발신 번호 (NEW_OUTGOING_CALL 로 받은 값을 OFFHOOK 때까지 보관) -----

    fun setPendingOutgoing(context: Context, number: String) {
        prefs(context).edit()
            .putString(KEY_PENDING_OUT_NUMBER, number)
            .putLong(KEY_PENDING_OUT_AT, System.currentTimeMillis())
            .apply()
    }

    /** 보관된 발신 번호를 꺼내고 비운다. maxAgeMs 보다 오래됐으면 null */
    fun takePendingOutgoing(context: Context, maxAgeMs: Long): String? {
        val p = prefs(context)
        val number = p.getString(KEY_PENDING_OUT_NUMBER, null)
        val at = p.getLong(KEY_PENDING_OUT_AT, 0L)
        clearPendingOutgoing(context)
        if (number.isNullOrBlank()) return null
        if (System.currentTimeMillis() - at > maxAgeMs) return null
        return number
    }

    fun clearPendingOutgoing(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_OUT_NUMBER)
            .remove(KEY_PENDING_OUT_AT)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
