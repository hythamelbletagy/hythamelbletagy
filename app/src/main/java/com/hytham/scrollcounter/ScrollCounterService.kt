package com.hytham.scrollcounter

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ScrollCounterService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var store: CounterStore
    private lateinit var badge: OverlayBadge
    private lateinit var instagramSession: Session
    private lateinit var facebookSession: Session
    private val handler = Handler(Looper.getMainLooper())
    private val reels = ReelDetector()
    private val facebookApp = PageScrollTracker()
    private val facebookWeb = PageScrollTracker()
    private val browsers = HashMap<String, BrowserState>()

    /** Package of the app currently on screen. */
    private var foreground: String? = null
    /** What the badge is showing, which is also the app whose session is running. */
    private var mode: OverlayBadge.Mode? = null

    private class BrowserState {
        var onFacebook = false
        var lastCheckAt = 0L
    }

    override fun onServiceConnected() {
        store = CounterStore(this)
        try {
            instagramSession = Session(store.prefs, Prefs.SESSION_INSTAGRAM)
            facebookSession = Session(store.prefs, Prefs.SESSION_FACEBOOK)
            badge = OverlayBadge(this, store.prefs, instagramSession, facebookSession)
            DebugLog.enabled = store.prefs.getBoolean(Prefs.DIAGNOSTICS, false)
            store.prefs.registerOnSharedPreferenceChangeListener(this)
            ServiceStatus.connected = true
            DebugLog.add("service connected")
        } catch (t: Throwable) {
            reportError("start", t)
        }
    }

    override fun onDestroy() {
        ServiceStatus.connected = false
        if (::store.isInitialized) store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::badge.isInitialized) badge.hide()
        mode?.let { sessionFor(it).leave(System.currentTimeMillis()) }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Counts, limits and the on/off switch all live in prefs: redraw on any change. */
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == Prefs.BADGE_X || key == Prefs.BADGE_Y || key == Prefs.LAST_ERROR) return
        showBadge(mode)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        ServiceStatus.events++
        ServiceStatus.lastPackage = pkg
        ServiceStatus.lastEventAt = System.currentTimeMillis()
        if (pkg == INSTAGRAM) ServiceStatus.instagramEvents++
        if (pkg in FACEBOOK_APPS) ServiceStatus.facebookEvents++
        // An unexpected error must not take the whole service down: record it and carry on.
        try {
            handleEvent(pkg, event)
        } catch (t: Throwable) {
            reportError("event from $pkg", t)
        }
    }

    private fun handleEvent(pkg: String, event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) onWindowChanged(pkg)
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (pkg != foreground && (pkg == INSTAGRAM || pkg in FACEBOOK_APPS || pkg in BROWSERS)) {
            // Back in the app without a window event, e.g. after closing the notification shade.
            foreground = pkg
            updateBadge()
        }

        when (pkg) {
            INSTAGRAM -> {
                val info = scrollInfo(event)
                val newReel = reels.onScroll(info)
                log(pkg, info, if (newReel) "new reel" else null)
                if (newReel) countReelAfterDelay()
            }

            in FACEBOOK_APPS -> {
                val info = scrollInfo(event)
                val n = facebookApp.onScroll(info)
                log(pkg, info, if (n > 0) "+$n page" else null)
                addFacebookPages(Counter.FACEBOOK_APP_PAGES, n)
            }

            in BROWSERS -> onBrowserScroll(pkg, event)
        }
    }

    private fun onWindowChanged(eventPkg: String) {
        // Use the active window's app: keyboards and popups send window events
        // too, but the app underneath stays active.
        // Our badge window isn't focusable, so it's never the active window; an
        // event from it with no active window tells us nothing.
        val root = rootInActiveWindow
        val activePkg = root?.packageName?.toString()
        root?.release()
        if (activePkg == null && eventPkg == packageName) return
        val pkg = activePkg ?: eventPkg

        foreground = pkg
        if (pkg in BROWSERS) {
            refreshBrowserUrl(pkg, browsers.getOrPut(pkg) { BrowserState() }, SystemClock.uptimeMillis())
        }
        updateBadge()
    }

    private fun onBrowserScroll(pkg: String, event: AccessibilityEvent) {
        val state = browsers.getOrPut(pkg) { BrowserState() }
        val now = SystemClock.uptimeMillis()
        if (now - state.lastCheckAt > URL_CHECK_INTERVAL_MS) {
            refreshBrowserUrl(pkg, state, now)
            updateBadge()
        }
        if (!state.onFacebook) return
        val info = scrollInfo(event)
        val n = facebookWeb.onScroll(info)
        log(pkg, info, if (n > 0) "+$n web page" else null)
        addFacebookPages(Counter.FACEBOOK_WEB_PAGES, n)
    }

    /** Counts the reel on screen once it has stayed there for the minimum watch time. */
    private fun countReelAfterDelay() {
        val seconds = store.prefs.getInt(Prefs.REEL_MIN_SECONDS, Prefs.DEFAULT_REEL_MIN_SECONDS)
        if (seconds <= 0) {
            countReel()
            return
        }
        val reel = reels.currentReel
        handler.postDelayed({
            try {
                // Still the same reel, still in Instagram, and the reels screen is still open.
                if (reels.currentReel == reel && foreground == INSTAGRAM && reelsPagerOnScreen()) countReel()
            } catch (t: Throwable) {
                reportError("reel timer", t)
            }
        }, seconds * 1000L)
    }

    private fun countReel() {
        if (!reels.countCurrent()) return
        DebugLog.add("instagram +1 reel")
        addAndWarn(Counter.INSTAGRAM_REELS, 1, instagramSession, Prefs.LIMIT_REELS, Prefs.SESSION_LIMIT_REELS, "reels")
    }

    private fun addFacebookPages(counter: Counter, n: Int) =
        addAndWarn(counter, n, facebookSession, Prefs.LIMIT_FACEBOOK, Prefs.SESSION_LIMIT_FACEBOOK, "Facebook pages")

    /** Adds [n] to today's count and the session, with a toast when a limit is reached. */
    private fun addAndWarn(counter: Counter, n: Int, session: Session, dailyKey: String, sessionKey: String, what: String) {
        if (n <= 0) return
        val daily = if (counter == Counter.INSTAGRAM_REELS) {
            store.get(counter)
        } else {
            store.get(Counter.FACEBOOK_APP_PAGES) + store.get(Counter.FACEBOOK_WEB_PAGES)
        }
        val inSession = session.count
        store.add(counter, n)
        session.add(n)

        val sessionLimit = store.prefs.getInt(sessionKey, 0)
        val dailyLimit = store.prefs.getInt(dailyKey, 0)
        val message = when {
            sessionLimit > 0 && inSession < sessionLimit && inSession + n >= sessionLimit ->
                getString(R.string.session_limit_reached, sessionLimit, what)
            dailyLimit > 0 && daily < dailyLimit && daily + n >= dailyLimit ->
                getString(R.string.daily_limit_reached, dailyLimit, what)
            else -> null
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }

    private fun reelsPagerOnScreen(): Boolean {
        val id = reels.pagerViewId ?: return true // can't check without an id
        val root = rootInActiveWindow ?: return false
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val visible = nodes.any { it.isVisibleToUser }
            nodes.forEach { it.release() }
            visible
        } finally {
            root.release()
        }
    }

    private fun sessionFor(mode: OverlayBadge.Mode) =
        if (mode == OverlayBadge.Mode.REELS) instagramSession else facebookSession

    /** Shows the right badge for the app on screen and starts/ends sessions. */
    private fun updateBadge() {
        val pkg = foreground
        val newMode = when {
            pkg == INSTAGRAM -> OverlayBadge.Mode.REELS
            pkg in FACEBOOK_APPS -> OverlayBadge.Mode.FACEBOOK
            pkg in BROWSERS && browsers[pkg]?.onFacebook == true -> OverlayBadge.Mode.FACEBOOK
            else -> null
        }
        if (newMode != mode) {
            val now = System.currentTimeMillis()
            mode?.let { sessionFor(it).leave(now) }
            newMode?.let { sessionFor(it).enter(now) }
            mode = newMode
        }
        showBadge(newMode)
    }

    /** Drawing the badge is kept separate so a failure there can't stop counting. */
    private fun showBadge(mode: OverlayBadge.Mode?) {
        try {
            badge.show(mode)
        } catch (t: Throwable) {
            reportError("badge", t)
        }
    }

    private fun reportError(where: String, t: Throwable) {
        val message = "$where: ${t.javaClass.simpleName}: ${t.message}"
        DebugLog.add("ERROR $message")
        if (::store.isInitialized) store.prefs.edit().putString(Prefs.LAST_ERROR, message).apply()
    }

    private fun refreshBrowserUrl(pkg: String, state: BrowserState, now: Long) {
        state.lastCheckAt = now
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            val url = findAddressBarText(root, pkg) ?: return // toolbar hidden: keep last answer
            val onFacebook = FacebookUrl.matches(url)
            if (onFacebook != state.onFacebook) DebugLog.add("$pkg on facebook=$onFacebook ($url)")
            state.onFacebook = onFacebook
        } finally {
            root.release()
        }
    }

    /** Reads the browser's address bar, trying known view ids first, then any "url"-like field. */
    private fun findAddressBarText(root: AccessibilityNodeInfo, pkg: String): CharSequence? {
        for (id in ADDRESS_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            val text = nodes.firstNotNullOfOrNull { it.text?.takeIf(CharSequence::isNotBlank) }
            nodes.forEach { it.release() }
            if (text != null) return text
        }
        return searchForUrlField(root, depth = 0, budget = intArrayOf(300))
    }

    private fun searchForUrlField(node: AccessibilityNodeInfo, depth: Int, budget: IntArray): CharSequence? {
        if (depth > 12 || budget[0]-- <= 0) return null
        val id = node.viewIdResourceName?.lowercase()
        if (id != null && ("url" in id || "address" in id || "location_bar" in id)) {
            node.text?.takeIf(CharSequence::isNotBlank)?.let { return it }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = try {
                // Don't walk into web page content: it's large and never holds the address bar.
                if (child.className?.toString() == "android.webkit.WebView") null
                else searchForUrlField(child, depth + 1, budget)
            } finally {
                child.release()
            }
            if (found != null) return found
        }
        return null
    }

    private fun scrollInfo(event: AccessibilityEvent): ScrollInfo {
        val metrics = resources.displayMetrics
        var viewId: String? = null
        var sourceHash = 0
        val bounds = Rect()
        event.source?.let { source ->
            viewId = source.viewIdResourceName
            sourceHash = source.hashCode()
            source.getBoundsInScreen(bounds)
            source.release()
        }
        var deltaX: Int? = null
        var deltaY: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            deltaX = event.scrollDeltaX.takeIf { it != -1 }
            deltaY = event.scrollDeltaY.takeIf { it != -1 }
        }
        return ScrollInfo(
            sourceKey = "${event.windowId}:$sourceHash:${viewId ?: event.className}",
            viewId = viewId,
            className = event.className?.toString(),
            fromIndex = event.fromIndex,
            toIndex = event.toIndex,
            itemCount = event.itemCount,
            scrollY = event.scrollY,
            deltaX = deltaX,
            deltaY = deltaY,
            viewWidth = bounds.width(),
            viewHeight = bounds.height(),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            timeMillis = SystemClock.uptimeMillis(),
        )
    }

    private fun log(pkg: String, info: ScrollInfo, note: String?) {
        if (!DebugLog.enabled) return
        val app = pkg.substringAfterLast('.')
        val id = info.viewId?.substringAfter(":id/") ?: info.className?.substringAfterLast('.')
        DebugLog.add(
            "$app $id idx=${info.fromIndex}-${info.toIndex}/${info.itemCount} y=${info.scrollY} " +
                "dy=${info.deltaY} ${info.viewWidth}x${info.viewHeight}" +
                if (note != null) "  $note" else ""
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        val FACEBOOK_APPS = setOf("com.facebook.katana", "com.facebook.lite")
        val BROWSERS = setOf(
            "com.android.chrome", "com.chrome.beta", "org.mozilla.firefox", "com.sec.android.app.sbrowser",
            "com.microsoft.emmx", "com.brave.browser", "com.opera.browser", "com.opera.mini.native",
            "com.kiwibrowser.browser", "com.duckduckgo.mobile.android", "com.vivaldi.browser",
            "com.mi.globalbrowser",
        )

        private const val URL_CHECK_INTERVAL_MS = 1500L

        /** Address bar view ids used by Chrome and the Chromium/Firefox-based browsers. */
        private val ADDRESS_BAR_IDS = listOf(
            "url_bar",                              // Chrome, Edge, Brave, Kiwi, Vivaldi
            "location_bar_edit_text",               // Samsung Internet
            "mozac_browser_toolbar_url_view",       // Firefox
            "url_field",                            // Opera
            "omnibarTextInput",                     // DuckDuckGo
        )
    }
}

/** recycle() is required before Android 13 and a deprecated no-op after. */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.release() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}
