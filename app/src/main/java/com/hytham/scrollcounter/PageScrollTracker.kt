package com.hytham.scrollcounter

import kotlin.math.floor

/**
 * Turns scroll events into "pages": one page is one viewport height scrolled
 * downward. Scrolling back up is not counted.
 *
 * Apps report scrolling in different ways, so the first available signal wins:
 *  1. scrollDeltaY (pixels, Android 9+ for plain views and WebViews)
 *  2. change in scrollY (pixels, browsers usually report this)
 *  3. change in first visible item index (RecyclerView feeds), converted to
 *     pages using the number of items visible on screen.
 */
class PageScrollTracker {
    private var sourceKey: String? = null
    private var lastScrollY = -1
    private var lastFromIndex = -1
    private var pageFraction = 0.0

    /** Returns the number of whole pages completed by this scroll event. */
    fun onScroll(info: ScrollInfo): Int {
        if (isSideways(info)) return 0

        if (info.sourceKey != sourceKey) {
            sourceKey = info.sourceKey
            lastScrollY = -1
            lastFromIndex = -1
        }

        val viewport = viewportHeight(info).toDouble()
        var pages = 0.0
        when {
            info.deltaY != null && info.deltaY != 0 ->
                if (info.deltaY > 0) pages = info.deltaY / viewport

            info.scrollY >= 0 -> {
                if (lastScrollY >= 0 && info.scrollY > lastScrollY) {
                    pages = (info.scrollY - lastScrollY) / viewport
                }
            }

            info.fromIndex >= 0 -> {
                if (lastFromIndex >= 0 && info.fromIndex > lastFromIndex) {
                    val visible = if (info.toIndex >= info.fromIndex) info.toIndex - info.fromIndex + 1 else 1
                    pages = (info.fromIndex - lastFromIndex).toDouble() / visible
                }
            }
        }
        if (info.scrollY >= 0) lastScrollY = info.scrollY
        if (info.fromIndex >= 0) lastFromIndex = info.fromIndex

        // A single event can't plausibly scroll more than a few screens; bigger
        // jumps are page reloads or "scroll to top" resets.
        if (pages > MAX_PAGES_PER_EVENT) pages = 0.0

        pageFraction += pages
        val whole = floor(pageFraction)
        pageFraction -= whole
        return whole.toInt()
    }

    private fun isSideways(info: ScrollInfo): Boolean {
        if (info.deltaX != null && info.deltaX != 0 && (info.deltaY == null || info.deltaY == 0)) return true
        // Narrow or short scrollers are carousels, story trays, comment boxes...
        if (info.viewWidth > 0 && info.viewWidth < info.screenWidth * 0.6) return true
        if (info.viewHeight > 0 && info.viewHeight < info.screenHeight * 0.3) return true
        return false
    }

    private fun viewportHeight(info: ScrollInfo): Int =
        if (info.viewHeight >= info.screenHeight * 0.3) info.viewHeight else info.screenHeight

    private companion object {
        const val MAX_PAGES_PER_EVENT = 5.0
    }
}
