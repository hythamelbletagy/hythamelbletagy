package com.hytham.scrollcounter

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

enum class Counter(val key: String) {
    INSTAGRAM_REELS("ig_reels"),
    FACEBOOK_APP_PAGES("fb_app_pages"),
    FACEBOOK_WEB_PAGES("fb_web_pages"),
}

/** Per-day counts, stored as "<yyyy-MM-dd>|<counter key>" -> Int. */
class CounterStore(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(counter: Counter, amount: Int, day: LocalDate = LocalDate.now()) {
        if (amount <= 0) return
        val key = key(day, counter)
        prefs.edit().putInt(key, prefs.getInt(key, 0) + amount).apply()
    }

    fun get(counter: Counter, day: LocalDate = LocalDate.now()): Int = prefs.getInt(key(day, counter), 0)

    fun reset(day: LocalDate = LocalDate.now()) {
        prefs.edit().apply { Counter.entries.forEach { remove(key(day, it)) } }.apply()
    }

    private fun key(day: LocalDate, counter: Counter) = "$day|${counter.key}"

    companion object {
        const val PREFS_NAME = "counts"
    }
}
