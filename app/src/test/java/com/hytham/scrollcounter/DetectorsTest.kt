package com.hytham.scrollcounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorsTest {

    private fun info(
        key: String = "w1:1",
        viewId: String? = "com.instagram.android:id/clips_viewer_view_pager",
        className: String? = "androidx.viewpager.widget.ViewPager",
        from: Int = -1,
        to: Int = -1,
        scrollY: Int = -1,
        deltaX: Int? = null,
        deltaY: Int? = null,
        width: Int = 1080,
        height: Int = 2200,
        time: Long = 0,
    ) = ScrollInfo(key, viewId, className, from, to, -1, scrollY, deltaX, deltaY, width, height, 1080, 2400, time)

    @Test
    fun reels_reportsNewReelsAndCountsEachOnce() {
        val d = ReelDetector()
        assertFalse(d.onScroll(info(from = 0)))  // starting reel
        assertFalse(d.onScroll(info(from = 0)))  // mid-swipe
        assertTrue(d.onScroll(info(from = 1)))
        assertTrue(d.countCurrent())
        assertFalse(d.countCurrent())            // already counted
        assertTrue(d.onScroll(info(from = 2)))   // swiped past quickly, not counted
        assertFalse(d.onScroll(info(from = 1)))  // back to a counted reel
        assertTrue(d.onScroll(info(from = 2)))   // skipped reel can still count
        assertTrue(d.countCurrent())
    }

    @Test
    fun reels_currentReelChangesOnEverySwipe() {
        val d = ReelDetector()
        d.onScroll(info(from = 0))
        d.onScroll(info(from = 1))
        val first = d.currentReel
        d.onScroll(info(from = 0))
        assertTrue(d.currentReel != first)
        assertTrue(d.countCurrent())             // the starting reel was never counted
    }

    @Test
    fun reels_newPagerStartsFresh() {
        val d = ReelDetector()
        d.onScroll(info(from = 0))
        d.onScroll(info(from = 1)); d.countCurrent()
        assertFalse(d.onScroll(info(key = "w2:9", from = 0)))
        assertTrue(d.onScroll(info(key = "w2:9", from = 1)))
    }

    @Test
    fun reels_withoutIndexTreatsSwipeBurstsAsNewReels() {
        val d = ReelDetector(swipeGapMillis = 700)
        assertTrue(d.onScroll(info(time = 1000)))
        assertFalse(d.onScroll(info(time = 1050)))
        assertFalse(d.onScroll(info(time = 1100)))
        assertTrue(d.onScroll(info(time = 3000)))
        assertFalse(d.onScroll(info(time = 5000, deltaY = -300))) // swiping back
    }

    @Test
    fun session_startsAfterGap() {
        val gap = 5 * 60_000L
        assertTrue(Session.isNewSession(nowMillis = 1_000_000, leftAtMillis = 0, gapMillis = gap))
        assertFalse(Session.isNewSession(nowMillis = 1_000_000, leftAtMillis = 1_000_000 - 60_000, gapMillis = gap))
        assertTrue(Session.isNewSession(nowMillis = 1_000_000, leftAtMillis = 1_000_000 - gap, gapMillis = gap))
        assertTrue(Session.isNewSession(nowMillis = 1_000_000, leftAtMillis = 2_000_000, gapMillis = gap)) // clock moved back
    }

    @Test
    fun reels_ignoresStoriesAndFeed() {
        val d = ReelDetector()
        assertFalse(d.isReelsPager(info(viewId = "com.instagram.android:id/reel_viewer_texture_view")))
        assertFalse(d.isReelsPager(info(viewId = "android:id/list", className = "androidx.recyclerview.widget.RecyclerView")))
        assertTrue(d.isReelsPager(info()))
        assertTrue(d.isReelsPager(info(viewId = null)))
    }

    @Test
    fun pages_fromDeltaY() {
        val t = PageScrollTracker()
        assertEquals(0, t.onScroll(info(deltaY = 1000)))
        assertEquals(1, t.onScroll(info(deltaY = 1300)))  // 2300 px >= 2200 viewport
        assertEquals(0, t.onScroll(info(deltaY = -5000))) // scrolling up doesn't count
    }

    @Test
    fun pages_fromScrollY() {
        val t = PageScrollTracker()
        assertEquals(0, t.onScroll(info(scrollY = 0)))
        assertEquals(0, t.onScroll(info(scrollY = 1100)))
        assertEquals(1, t.onScroll(info(scrollY = 2200)))
        assertEquals(0, t.onScroll(info(scrollY = 100)))
        assertEquals(1, t.onScroll(info(scrollY = 2300)))
    }

    @Test
    fun pages_fromItemIndex() {
        val t = PageScrollTracker()
        assertEquals(0, t.onScroll(info(from = 0, to = 1)))  // 2 items visible
        assertEquals(0, t.onScroll(info(from = 1, to = 2)))
        assertEquals(1, t.onScroll(info(from = 2, to = 3)))
    }

    @Test
    fun pages_ignoresCarouselsAndHugeJumps() {
        val t = PageScrollTracker()
        assertEquals(0, t.onScroll(info(deltaX = 500, deltaY = 0)))
        assertEquals(0, t.onScroll(info(deltaY = 5000, width = 400)))
        assertEquals(0, t.onScroll(info(deltaY = 100_000)))
    }

    @Test
    fun facebookUrl() {
        assertTrue(FacebookUrl.matches("m.facebook.com/home.php"))
        assertTrue(FacebookUrl.matches("https://www.facebook.com/"))
        assertTrue(FacebookUrl.matches("facebook.com"))
        assertTrue(FacebookUrl.matches("fb.com"))
        assertFalse(FacebookUrl.matches("notfacebook.com"))
        assertFalse(FacebookUrl.matches("google.com/search?q=facebook.com"))
        assertFalse(FacebookUrl.matches("facebook login"))
        assertFalse(FacebookUrl.matches(null))
    }
}
