package com.parentcare.callguard

import android.content.Context

object PrefsHelper {

    private const val PREFS_NAME = "call_guard_prefs"
    private const val KEY_CHILD_NUMBER = "child_number"
    private const val KEY_WHITELIST = "whitelist_numbers"
    private const val KEY_LAST_NUMBER = "last_number"
    private const val KEY_FIRST_MINUTES = "first_minutes"
    private const val KEY_SECOND_MINUTES = "second_minutes"
    private const val KEY_MONITORING = "monitoring_enabled"

    const val DEFAULT_FIRST = 8
    const val DEFAULT_SECOND = 10

    fun setMonitoring(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING, enabled).apply()
    }

    fun isMonitoring(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MONITORING, false)
    }

    fun setFirstMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_FIRST_MINUTES, minutes).apply()
    }

    fun getFirstMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_FIRST_MINUTES, DEFAULT_FIRST)
    }

    fun setSecondMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_SECOND_MINUTES, minutes).apply()
    }

    fun getSecondMinutes(context: Context): Int {
        return prefs(context).getInt(KEY_SECOND_MINUTES, DEFAULT_SECOND)
    }

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
        val list = raw.split(",").map { item -> item.trim().filter { c -> c.isDigit() } }
        val normalized = number.filter { it.isDigit() }
        if (normalized.isEmpty()) return false
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
