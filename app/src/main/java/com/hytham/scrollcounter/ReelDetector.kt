package com.hytham.scrollcounter

/**
 * Counts Instagram reels the user scrolls to.
 *
 * Instagram shows reels in a full-screen vertical pager whose view id contains
 * "clips" (e.g. `com.instagram.android:id/clips_viewer_view_pager`). Stories use
 * "reel_viewer" ids and the home feed is a RecyclerView, so neither matches.
 *
 * When the pager reports item positions we count each new, furthest-reached
 * position once, so swiping back and forth over the same reels doesn't inflate
 * the count. When it doesn't, each burst of scroll events separated by a pause
 * counts as one swipe.
 */
class ReelDetector(private val swipeGapMillis: Long = 700) {
    private var pagerKey: String? = null
    private var maxIndex = -1
    private var lastScrollAt = Long.MIN_VALUE / 2

    fun isReelsPager(info: ScrollInfo): Boolean {
        val id = info.viewId?.lowercase()
        if (id != null) return "clips" in id && "tray" !in id
        // No view id: fall back to "a full-screen ViewPager".
        val cls = info.className ?: return false
        return "ViewPager" in cls &&
            info.viewHeight >= info.screenHeight * 0.75 &&
            info.viewWidth >= info.screenWidth * 0.9
    }

    /** Returns how many new reels this scroll event reached (usually 0 or 1). */
    fun onScroll(info: ScrollInfo): Int {
        if (!isReelsPager(info)) return 0

        val newPager = info.sourceKey != pagerKey
        if (newPager) {
            pagerKey = info.sourceKey
            maxIndex = -1
        }

        if (info.fromIndex >= 0) {
            if (maxIndex < 0) {
                // First event from this pager reports the reel we started on.
                maxIndex = info.fromIndex
                return 0
            }
            if (info.fromIndex > maxIndex) {
                // Cap jumps so a relayout can't add a huge number at once.
                val gained = (info.fromIndex - maxIndex).coerceAtMost(3)
                maxIndex = info.fromIndex
                return gained
            }
            return 0
        }

        // No positions reported: treat each burst of scroll events as one swipe.
        val gap = info.timeMillis - lastScrollAt
        lastScrollAt = info.timeMillis
        val backwards = info.deltaY != null && info.deltaY < 0
        return if (gap >= swipeGapMillis && !backwards) 1 else 0
    }
}
