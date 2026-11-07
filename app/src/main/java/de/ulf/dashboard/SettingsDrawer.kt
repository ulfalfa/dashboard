package de.ulf.dashboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * Side menu that slides in from the left over the dashboard. Closes itself after [AUTO_CLOSE_MS]
 * without interaction so a forgotten menu never hides the dashboard permanently.
 */
interface DrawerListener {
    fun onServerToggle(enabled: Boolean)
    fun onExport()
    fun onImport()
    fun onNewPassword()
}

class SettingsDrawer(context: Context, private val listener: DrawerListener) : FrameLayout(context) {
    private companion object {
        const val PANEL_WIDTH_DP = 360
        const val ANIMATION_MS = 200L
        const val AUTO_CLOSE_MS = 30_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { close() }
    private val serverSwitch = Switch(context).apply { text = "Webserver" ; textSize = 20f; setTextColor(WHITE) }
    private val serverStatus = TextView(context).apply { textSize = 15f; setTextColor(MUTED) }
    private val credentials = TextView(context).apply { textSize = 15f; setTextColor(WHITE) }
    private val message = TextView(context).apply { textSize = 15f; setTextColor(WHITE) }
    private val panel = LinearLayout(context)
    private var updatingSwitch = false

    var isOpen = false
        private set

    init {
        visibility = View.GONE
        addView(
            View(context).apply { setBackgroundColor(SCRIM); setOnClickListener { close() } },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        panel.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PANEL)
            setPadding(dp(24), dp(32), dp(24), dp(24))
            isClickable = true // swallow touches so they do not hit the scrim underneath
            addView(TextView(context).apply { text = "Dashboard"; textSize = 26f; setTextColor(WHITE) })
            addView(serverSwitch, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(32) })
            addView(serverStatus, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) })
            addView(credentials, rowParams(16))
            addView(button("Neues Passwort erzeugen") { restartAutoClose(); listener.onNewPassword() }, rowParams(8))
            addView(button("Konfiguration exportieren") { restartAutoClose(); listener.onExport() }, rowParams(32))
            addView(button("Konfiguration importieren") { restartAutoClose(); listener.onImport() }, rowParams(8))
            addView(message, rowParams(16))
            addView(button("Schließen") { close() }, rowParams(32))
        }
        addView(panel, LayoutParams(dp(PANEL_WIDTH_DP), LayoutParams.MATCH_PARENT, Gravity.START))
        serverSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            restartAutoClose()
            listener.onServerToggle(checked)
        }
    }

    /** Reflects the real server state; does not trigger [onServerToggle]. */
    fun showServer(running: Boolean, text: String) {
        updatingSwitch = true
        serverSwitch.isChecked = running
        updatingSwitch = false
        serverStatus.text = text
    }

    /** Shows the portal login so it can be read off the board when opening the portal from another device. */
    fun showCredentials(password: String) {
        credentials.text = "Portal-Login\nBenutzer: ${BasicAuth.USER}\nPasswort: $password"
    }

    /** Shows the result of the last export/import action. */
    fun showMessage(text: String) {
        message.text = text
        restartAutoClose()
    }

    fun open() {
        isOpen = true
        visibility = View.VISIBLE
        panel.animate().cancel()
        panel.translationX = -dp(PANEL_WIDTH_DP).toFloat()
        panel.animate().translationX(0f).setDuration(ANIMATION_MS).start()
        restartAutoClose()
    }

    fun close() {
        isOpen = false
        handler.removeCallbacks(autoClose)
        panel.animate().cancel()
        panel.animate().translationX(-dp(PANEL_WIDTH_DP).toFloat()).setDuration(ANIMATION_MS)
            .withEndAction { if (!isOpen) visibility = View.GONE }
            .start()
    }

    private fun restartAutoClose() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, AUTO_CLOSE_MS)
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(context).apply { text = label; setOnClickListener { onClick() } }

    private fun rowParams(topMarginDp: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(topMarginDp) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val MUTED = 0xFFAAB2C0.toInt()
private const val PANEL = 0xFF1E222B.toInt()
private const val SCRIM = 0x80000000.toInt()
