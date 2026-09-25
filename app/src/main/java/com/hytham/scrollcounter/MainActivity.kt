package com.hytham.scrollcounter

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var store: CounterStore
    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var restrictedHint: TextView
    private lateinit var appInfoButton: Button
    private lateinit var igReels: TextView
    private lateinit var fbApp: TextView
    private lateinit var fbWeb: TextView
    private lateinit var history: TextView
    private lateinit var diagnosticsPanel: View
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = CounterStore(this)

        status = findViewById(R.id.status)
        enableButton = findViewById(R.id.enable_button)
        restrictedHint = findViewById(R.id.restricted_hint)
        appInfoButton = findViewById(R.id.app_info_button)
        igReels = findViewById(R.id.ig_reels_count)
        fbApp = findViewById(R.id.fb_app_count)
        fbWeb = findViewById(R.id.fb_web_count)
        history = findViewById(R.id.history)
        diagnosticsPanel = findViewById(R.id.diagnostics_panel)
        log = findViewById(R.id.log)

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        appInfoButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }
        findViewById<Button>(R.id.reset_button).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.reset_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ -> store.reset() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        val diagnostics = findViewById<Switch>(R.id.diagnostics_switch)
        diagnostics.isChecked = store.prefs.getBoolean(PREF_DIAGNOSTICS, false)
        DebugLog.enabled = diagnostics.isChecked
        diagnosticsPanel.visibility = if (diagnostics.isChecked) View.VISIBLE else View.GONE
        diagnostics.setOnCheckedChangeListener { _, checked ->
            store.prefs.edit().putBoolean(PREF_DIAGNOSTICS, checked).apply()
            DebugLog.enabled = checked
            diagnosticsPanel.visibility = if (checked) View.VISIBLE else View.GONE
            refreshLog()
        }
        findViewById<Button>(R.id.refresh_log_button).setOnClickListener { refreshLog() }
    }

    override fun onResume() {
        super.onResume()
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        refreshStatus()
        refreshCounts()
        refreshLog()
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) = refreshCounts()

    private fun refreshStatus() {
        val on = isServiceEnabled()
        status.setText(if (on) R.string.status_on else R.string.status_off)
        status.setTextColor(getColor(if (on) R.color.status_on else R.color.status_off))
        val offVisibility = if (on) View.GONE else View.VISIBLE
        enableButton.visibility = offVisibility
        restrictedHint.visibility = offVisibility
        appInfoButton.visibility = offVisibility
    }

    private fun refreshCounts() {
        igReels.text = store.get(Counter.INSTAGRAM_REELS).toString()
        fbApp.text = store.get(Counter.FACEBOOK_APP_PAGES).toString()
        fbWeb.text = store.get(Counter.FACEBOOK_WEB_PAGES).toString()

        val dayFormat = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.getDefault())
        val today = LocalDate.now()
        history.text = buildString {
            append("".padEnd(11))
            append(getString(R.string.col_ig).padStart(7))
            append(getString(R.string.col_fb_app).padStart(7))
            append(getString(R.string.col_fb_web).padStart(7))
            for (daysAgo in 0L..6L) {
                val day = today.minusDays(daysAgo)
                append('\n')
                append(day.format(dayFormat).padEnd(11))
                Counter.entries.forEach { append(store.get(it, day).toString().padStart(7)) }
            }
        }
    }

    private fun refreshLog() {
        if (!DebugLog.enabled) return
        log.text = DebugLog.snapshot().ifEmpty { getString(R.string.log_empty) }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(this, ScrollCounterService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    companion object {
        const val PREF_DIAGNOSTICS = "diagnostics"
    }
}
