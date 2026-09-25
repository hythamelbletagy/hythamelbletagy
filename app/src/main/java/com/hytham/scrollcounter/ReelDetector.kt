package com.hytham.scrollcounter

/**
 * Tracks which Instagram reel is on screen.
 *
 * Instagram shows reels in a full-screen vertical pager whose view id contains
 * "clips" (e.g. `com.instagram.android:id/clips_viewer_view_pager`). Stories use
 * "reel_viewer" ids and the home feed is a RecyclerView, so neither matches.
 *
 * [onScroll] reports when the user lands on a reel that hasn't been counted
 * yet; the caller decides when it counts (immediately, or after it has stayed
 * on screen long enough) and then calls [countCurrent]. Each reel position is
 * counted once per pager, so swiping back and forth doesn't inflate the count.
 *
 * When the pager doesn't report positions, each burst of scroll events
 * separated by a pause is treated as a swipe to a new reel.
 */
class ReelDetector(private val swipeGapMillis: Long = 700) {
    private var pagerKey: String? = null
    private var currentIndex = -1
    private val countedIndexes = HashSet<Int>()
    private var lastScrollAt = Long.MIN_VALUE / 2
    private var unindexedReel = 0

    /** Changes every time the user lands on a different reel. */
    var currentReel = 0L
        private set

    /** Whether the reel on screen was reached by a swipe and hasn't been counted. */
    var currentIsNew = false
        private set

    /** View id of the reels pager, used to check it's still on screen. */
    var pagerViewId: String? = null
        private set

    fun isReelsPager(info: ScrollInfo): Boolean {
        val id = info.viewId?.lowercase()
        if (id != null) return "clips" in id && "tray" !in id
        // No view id: fall back to "a full-screen ViewPager".
        val cls = info.className ?: return false
        return "ViewPager" in cls &&
            info.viewHeight >= info.screenHeight * 0.75 &&
            info.viewWidth >= info.screenWidth * 0.9
    }

    /** Returns true when this event moved to a reel that hasn't been counted yet. */
    fun onScroll(info: ScrollInfo): Boolean {
        if (!isReelsPager(info)) return false

        if (info.sourceKey != pagerKey) {
            pagerKey = info.sourceKey
            pagerViewId = info.viewId
            currentIndex = -1
            countedIndexes.clear()
        }

        if (info.fromIndex >= 0) {
            if (currentIndex < 0) {
                // First event from this pager reports the reel we started on.
                currentIndex = info.fromIndex
                return false
            }
            if (info.fromIndex == currentIndex) return false
            currentIndex = info.fromIndex
            return landOn(isNew = currentIndex !in countedIndexes)
        }

        // No positions reported: treat each burst of scroll events as one swipe.
        val gap = info.timeMillis - lastScrollAt
        lastScrollAt = info.timeMillis
        val backwards = info.deltaY != null && info.deltaY < 0
        if (gap < swipeGapMillis || backwards) return false
        unindexedReel++
        return landOn(isNew = true)
    }

    /** Marks the reel on screen as counted. Returns false if it already was. */
    fun countCurrent(): Boolean {
        if (!currentIsNew) return false
        currentIsNew = false
        if (currentIndex >= 0) countedIndexes += currentIndex
        return true
    }

    private fun landOn(isNew: Boolean): Boolean {
        currentReel++
        currentIsNew = isNew
        return isNew
    }
}
