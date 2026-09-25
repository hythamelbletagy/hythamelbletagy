package com.hytham.scrollcounter

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var store: CounterStore
    private lateinit var status: TextView
    private lateinit var serviceDetail: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val statusTicker = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }
    private lateinit var enableButton: Button
    private lateinit var restrictedHint: TextView
    private lateinit var appInfoButton: Button
    private lateinit var igReels: TextView
    private lateinit var fbApp: TextView
    private lateinit var fbWeb: TextView
    private lateinit var history: TextView
    private lateinit var sessionSummary: TextView
    private lateinit var diagnosticsPanel: View
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = CounterStore(this)

        status = findViewById(R.id.status)
        serviceDetail = findViewById(R.id.service_detail)
        enableButton = findViewById(R.id.enable_button)
        restrictedHint = findViewById(R.id.restricted_hint)
        appInfoButton = findViewById(R.id.app_info_button)
        igReels = findViewById(R.id.ig_reels_count)
        fbApp = findViewById(R.id.fb_app_count)
        fbWeb = findViewById(R.id.fb_web_count)
        history = findViewById(R.id.history)
        sessionSummary = findViewById(R.id.session_summary)
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
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    store.reset()
                    store.prefs.edit()
                        .remove(Prefs.sessionCount(Prefs.SESSION_INSTAGRAM))
                        .remove(Prefs.sessionCount(Prefs.SESSION_FACEBOOK))
                        .apply()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        val diagnostics = findViewById<Switch>(R.id.diagnostics_switch)
        diagnostics.isChecked = store.prefs.getBoolean(Prefs.DIAGNOSTICS, false)
        DebugLog.enabled = diagnostics.isChecked
        diagnosticsPanel.visibility = if (diagnostics.isChecked) View.VISIBLE else View.GONE
        diagnostics.setOnCheckedChangeListener { _, checked ->
            store.prefs.edit().putBoolean(Prefs.DIAGNOSTICS, checked).apply()
            DebugLog.enabled = checked
            diagnosticsPanel.visibility = if (checked) View.VISIBLE else View.GONE
            refreshLog()
        }
        findViewById<Button>(R.id.refresh_log_button).setOnClickListener { refreshLog() }

        val overlay = findViewById<Switch>(R.id.overlay_switch)
        overlay.isChecked = store.prefs.getBoolean(Prefs.OVERLAY_ENABLED, true)
        overlay.setOnCheckedChangeListener { _, checked ->
            store.prefs.edit().putBoolean(Prefs.OVERLAY_ENABLED, checked).apply()
        }
        bindNumber(R.id.limit_reels, Prefs.LIMIT_REELS)
        bindNumber(R.id.limit_facebook, Prefs.LIMIT_FACEBOOK)
        bindNumber(R.id.session_limit_reels, Prefs.SESSION_LIMIT_REELS)
        bindNumber(R.id.session_limit_facebook, Prefs.SESSION_LIMIT_FACEBOOK)
        bindNumber(R.id.session_gap, Prefs.SESSION_GAP_MINUTES, Prefs.DEFAULT_SESSION_GAP_MINUTES)
        bindNumber(R.id.reel_min_seconds, Prefs.REEL_MIN_SECONDS, Prefs.DEFAULT_REEL_MIN_SECONDS)
    }

    /**
     * Shows the saved number in a field and saves edits. An empty field means
     * [default] (0 = no limit), and the field then shows the default as its hint.
     */
    private fun bindNumber(fieldId: Int, key: String, default: Int = 0) {
        val field = findViewById<EditText>(fieldId)
        val saved = store.prefs.getInt(key, default)
        if (saved != default) field.setText(saved.toString())
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                store.prefs.edit().putInt(key, s?.toString()?.toIntOrNull() ?: default).apply()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        handler.post(statusTicker)
        refreshCounts()
        refreshLog()
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(statusTicker)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key !in Prefs.ALL) refreshCounts()
    }

    private fun refreshStatus() {
        val enabled = isServiceEnabled()
        val running = enabled && ServiceStatus.connected
        status.setText(
            when {
                running -> R.string.status_on
                enabled -> R.string.status_not_running
                else -> R.string.status_off
            }
        )
        status.setTextColor(getColor(if (running) R.color.status_on else R.color.status_off))

        val detail = mutableListOf<String>()
        when {
            enabled && !running -> detail += getString(R.string.detail_not_running)
            running && ServiceStatus.events == 0L -> detail += getString(R.string.detail_no_events)
            running -> detail += getString(
                R.string.detail_events,
                ServiceStatus.events,
                ServiceStatus.instagramEvents,
                ServiceStatus.facebookEvents,
                ServiceStatus.lastPackage ?: "?",
                (System.currentTimeMillis() - ServiceStatus.lastEventAt) / 1000,
            )
        }
        store.prefs.getString(Prefs.LAST_ERROR, null)?.let { detail += getString(R.string.detail_error, it) }
        serviceDetail.text = detail.joinToString("\n")
        serviceDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE

        // Show the settings buttons whenever the counter isn't actually running.
        val offVisibility = if (running) View.GONE else View.VISIBLE
        enableButton.visibility = offVisibility
        restrictedHint.visibility = offVisibility
        appInfoButton.visibility = offVisibility
    }

    private fun refreshCounts() {
        igReels.text = store.get(Counter.INSTAGRAM_REELS).toString()
        fbApp.text = store.get(Counter.FACEBOOK_APP_PAGES).toString()
        fbWeb.text = store.get(Counter.FACEBOOK_WEB_PAGES).toString()
        sessionSummary.text = getString(
            R.string.session_summary,
            store.prefs.getInt(Prefs.sessionCount(Prefs.SESSION_INSTAGRAM), 0),
            store.prefs.getInt(Prefs.sessionCount(Prefs.SESSION_FACEBOOK), 0),
        )

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
}
