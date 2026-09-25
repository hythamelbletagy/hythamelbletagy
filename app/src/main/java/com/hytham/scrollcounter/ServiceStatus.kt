package com.hytham.scrollcounter

/**
 * What the accessibility service is doing right now, shown on the main screen so
 * problems can be pinned down: "enabled but not running", "running but no
 * events", or "events arrive but nothing is counted".
 *
 * The service and the app's screen run in the same process, so an object works.
 */
object ServiceStatus {
    @Volatile var connected = false
    @Volatile var events = 0L
    @Volatile var instagramEvents = 0L
    @Volatile var facebookEvents = 0L
    @Volatile var lastPackage: String? = null
    @Volatile var lastEventAt = 0L
}
