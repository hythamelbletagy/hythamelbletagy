package com.hytham.scrollcounter

/**
 * The parts of a TYPE_VIEW_SCROLLED accessibility event the detectors need,
 * pulled out so the counting logic can be unit tested without Android.
 *
 * Unknown values are -1 (Android's own convention for these fields), and
 * [deltaX]/[deltaY] are null when the platform did not report them.
 */
data class ScrollInfo(
    /** Identifies the scrolling view; changes when the user opens a different list/pager. */
    val sourceKey: String,
    val viewId: String?,
    val className: String?,
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    val scrollY: Int,
    val deltaX: Int?,
    val deltaY: Int?,
    /** Size of the scrolling view in pixels, 0 when unknown. */
    val viewWidth: Int,
    val viewHeight: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val timeMillis: Long,
)
