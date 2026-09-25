package com.hytham.scrollcounter

import android.content.SharedPreferences

/**
 * Counts for the current session in one app (Instagram, or Facebook app + web).
 *
 * A session starts when the user opens the app after being away from it for
 * longer than the session gap, and is stored in prefs so it survives restarts.
 */
class Session(private val prefs: SharedPreferences, private val name: String) {

    val count: Int get() = prefs.getInt(countKey, 0)

    fun enter(nowMillis: Long) {
        val leftAt = prefs.getLong(leftAtKey, 0)
        val gapMillis = prefs.getInt(Prefs.SESSION_GAP_MINUTES, Prefs.DEFAULT_SESSION_GAP_MINUTES) * 60_000L
        if (isNewSession(nowMillis, leftAt, gapMillis)) prefs.edit().putInt(countKey, 0).apply()
    }

    fun leave(nowMillis: Long) = prefs.edit().putLong(leftAtKey, nowMillis).apply()

    fun add(amount: Int) {
        if (amount > 0) prefs.edit().putInt(countKey, count + amount).apply()
    }

    private val countKey get() = Prefs.sessionCount(name)
    private val leftAtKey get() = Prefs.sessionLeftAt(name)

    companion object {
        fun isNewSession(nowMillis: Long, leftAtMillis: Long, gapMillis: Long): Boolean =
            leftAtMillis <= 0 || nowMillis - leftAtMillis >= gapMillis || nowMillis < leftAtMillis
    }
}
