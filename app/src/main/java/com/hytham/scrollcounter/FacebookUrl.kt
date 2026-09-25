package com.hytham.scrollcounter

object FacebookUrl {
    private val HOSTS = listOf("facebook.com", "fb.com", "facebook.net")

    /** True for address bar text such as "m.facebook.com/…" or "https://www.facebook.com". */
    fun matches(addressBarText: CharSequence?): Boolean {
        val text = addressBarText?.toString()?.trim()?.lowercase() ?: return false
        if (text.isEmpty() || ' ' in text) return false
        val host = text
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
        return HOSTS.any { host == it || host.endsWith(".$it") }
    }
}
