package com.hytham.scrollcounter

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScrollCounterService : AccessibilityService() {

    private lateinit var store: CounterStore
    private val reels = ReelDetector()
    private val facebookApp = PageScrollTracker()
    private val facebookWeb = PageScrollTracker()
    private val browsers = HashMap<String, BrowserState>()

    private class BrowserState {
        var onFacebook = false
        var lastCheckAt = 0L
    }

    override fun onServiceConnected() {
        store = CounterStore(this)
        DebugLog.enabled = store.prefs.getBoolean(MainActivity.PREF_DIAGNOSTICS, false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        when (pkg) {
            INSTAGRAM -> if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val info = scrollInfo(event)
                val n = reels.onScroll(info)
                log(pkg, info, n, "reel")
                store.add(Counter.INSTAGRAM_REELS, n)
            }

            in FACEBOOK_APPS -> if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                val info = scrollInfo(event)
                val n = facebookApp.onScroll(info)
                log(pkg, info, n, "page")
                store.add(Counter.FACEBOOK_APP_PAGES, n)
            }

            else -> onBrowserEvent(pkg, event)
        }
    }

    private fun onBrowserEvent(pkg: String, event: AccessibilityEvent) {
        val state = browsers.getOrPut(pkg) { BrowserState() }
        val now = SystemClock.uptimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (now - state.lastCheckAt > URL_CHECK_INTERVAL_MS) refreshBrowserUrl(pkg, state, now)

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (now - state.lastCheckAt > URL_CHECK_INTERVAL_MS * 3) refreshBrowserUrl(pkg, state, now)
                if (!state.onFacebook) return
                val info = scrollInfo(event)
                val n = facebookWeb.onScroll(info)
                log(pkg, info, n, "web page")
                store.add(Counter.FACEBOOK_WEB_PAGES, n)
            }
        }
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

    private fun log(pkg: String, info: ScrollInfo, counted: Int, what: String) {
        if (!DebugLog.enabled) return
        val app = pkg.substringAfterLast('.')
        val id = info.viewId?.substringAfter(":id/") ?: info.className?.substringAfterLast('.')
        DebugLog.add(
            "$app $id idx=${info.fromIndex}-${info.toIndex}/${info.itemCount} y=${info.scrollY} " +
                "dy=${info.deltaY} ${info.viewWidth}x${info.viewHeight}" +
                if (counted > 0) "  +$counted $what" else ""
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        val FACEBOOK_APPS = setOf("com.facebook.katana", "com.facebook.lite")

        private const val URL_CHECK_INTERVAL_MS = 1000L

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
