package com.hytham.scrollcounter

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * A small draggable counter drawn on top of Instagram / Facebook.
 *
 * It uses an accessibility overlay window, which the accessibility service may
 * add without the "Display over other apps" permission.
 */
class OverlayBadge(private val context: Context, private val prefs: SharedPreferences) {

    enum class Mode { REELS, FACEBOOK }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var mode: Mode? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt(Prefs.BADGE_X, dp(12))
        y = prefs.getInt(Prefs.BADGE_Y, dp(120))
    }

    /** Shows the badge for [newMode], or hides it when null or the badge is turned off. */
    fun show(newMode: Mode?) {
        mode = newMode
        if (newMode == null || !prefs.getBoolean(Prefs.OVERLAY_ENABLED, true)) {
            hide()
            return
        }
        val badge = view ?: createView().also {
            view = it
            windowManager.addView(it, params)
        }
        render(badge, newMode)
    }

    /** Re-draws the current badge, e.g. after a count or setting changed. */
    fun refresh() = show(mode)

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun render(badge: TextView, mode: Mode) {
        val store = CounterStore.from(prefs)
        val (label, count, limit) = when (mode) {
            Mode.REELS -> Triple(
                "🎬", // 🎬
                store.get(Counter.INSTAGRAM_REELS),
                prefs.getInt(Prefs.LIMIT_REELS, 0),
            )
            Mode.FACEBOOK -> Triple(
                "📄", // 📄
                store.get(Counter.FACEBOOK_APP_PAGES) + store.get(Counter.FACEBOOK_WEB_PAGES),
                prefs.getInt(Prefs.LIMIT_FACEBOOK, 0),
            )
        }
        val overLimit = limit in 1..count
        badge.text = if (limit > 0) "$label $count / $limit" else "$label $count"
        (badge.background as GradientDrawable).setColor(if (overLimit) COLOR_OVER_LIMIT else COLOR_NORMAL)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): TextView = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(6), dp(12), dp(6))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat() }
        elevation = dp(4).toFloat()
        importantForAccessibility = TextView.IMPORTANT_FOR_ACCESSIBILITY_NO

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX
                    val dy = e.rawY - touchY
                    if (abs(dx) > 4 || abs(dy) > 4) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        windowManager.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    prefs.edit().putInt(Prefs.BADGE_X, params.x).putInt(Prefs.BADGE_Y, params.y).apply()
            }
            true
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val COLOR_NORMAL = 0xCC303036.toInt()
        const val COLOR_OVER_LIMIT = 0xEED32F2F.toInt()
    }
}
