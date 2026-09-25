package com.hytham.scrollcounter

/** Setting keys, stored next to the counts in [CounterStore.PREFS_NAME]. */
object Prefs {
    const val DIAGNOSTICS = "diagnostics"
    const val OVERLAY_ENABLED = "overlay_enabled"
    const val BADGE_X = "badge_x"
    const val BADGE_Y = "badge_y"

    /** Daily and per-session limits; 0 means no limit. */
    const val LIMIT_REELS = "limit_reels"
    const val LIMIT_FACEBOOK = "limit_facebook"
    const val SESSION_LIMIT_REELS = "session_limit_reels"
    const val SESSION_LIMIT_FACEBOOK = "session_limit_facebook"

    /** Minutes away from the app after which the next visit is a new session. */
    const val SESSION_GAP_MINUTES = "session_gap_minutes"
    const val DEFAULT_SESSION_GAP_MINUTES = 5

    /** Seconds a reel must stay on screen before it counts; 0 counts every swipe. */
    const val REEL_MIN_SECONDS = "reel_min_seconds"
    const val DEFAULT_REEL_MIN_SECONDS = 5

    /** The most recent unexpected error in the service, shown on the main screen. */
    const val LAST_ERROR = "last_error"

    const val SESSION_INSTAGRAM = "ig"
    const val SESSION_FACEBOOK = "fb"
    fun sessionCount(name: String) = "session_${name}_count"
    fun sessionLeftAt(name: String) = "session_${name}_left_at"

    /** Keys that are settings or session state rather than daily counts. */
    val ALL = setOf(
        DIAGNOSTICS, OVERLAY_ENABLED, BADGE_X, BADGE_Y,
        LIMIT_REELS, LIMIT_FACEBOOK, SESSION_LIMIT_REELS, SESSION_LIMIT_FACEBOOK,
        SESSION_GAP_MINUTES, REEL_MIN_SECONDS, LAST_ERROR,
        sessionLeftAt(SESSION_INSTAGRAM), sessionLeftAt(SESSION_FACEBOOK),
    )
}
