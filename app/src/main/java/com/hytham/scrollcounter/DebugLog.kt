package com.hytham.scrollcounter

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory log of recent scroll events, shown in the app when Diagnostics is on.
 * Useful for adjusting the detectors when Instagram or Facebook change their layouts.
 */
object DebugLog {
    private const val MAX_LINES = 60
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Volatile
    var enabled = false

    @Synchronized
    fun add(line: String) {
        if (!enabled) return
        lines.addFirst("${timeFormat.format(Date())} $line")
        while (lines.size > MAX_LINES) lines.removeLast()
    }

    @Synchronized
    fun snapshot(): String = lines.joinToString("\n")
}
