package com.hytham.scrollcounter

/** Setting keys, stored next to the counts in [CounterStore.PREFS_NAME]. */
object Prefs {
    const val DIAGNOSTICS = "diagnostics"
    const val OVERLAY_ENABLED = "overlay_enabled"
    const val BADGE_X = "badge_x"
    const val BADGE_Y = "badge_y"
    /** Daily limits; 0 means no limit. */
    const val LIMIT_REELS = "limit_reels"
    const val LIMIT_FACEBOOK = "limit_facebook"

    /** Keys that are settings rather than daily counts. */
    val ALL = setOf(DIAGNOSTICS, OVERLAY_ENABLED, BADGE_X, BADGE_Y, LIMIT_REELS, LIMIT_FACEBOOK)
}
