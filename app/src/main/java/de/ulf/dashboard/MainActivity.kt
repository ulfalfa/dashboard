package de.ulf.dashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File
import java.io.IOException

class MainActivity : Activity() {
    private companion object {
        const val TAG = "Dashboard"
        const val LOAD_TIMEOUT_MS = 30_000L
        const val ERROR_DELAY_MS = 5_000L
        const val SETTINGS_PORT = 2411
        const val CONFIG_ASSET = "dashboards.json"
        const val UI_ASSET = "index.html"
        const val PREFS = "dashboard"
        const val PREF_SERVER_ENABLED = "server_enabled"
        const val EDGE_STRIP_DP = 32
        const val SWIPE_OPEN_DP = 48
        const val REQUEST_EXPORT = 1
        const val REQUEST_IMPORT = 2
        const val EXPORT_FILE_NAME = "dashboards.json"
    }

    private lateinit var container: FrameLayout
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var controller: RotationController? = null
    private lateinit var drawer: SettingsDrawer
    private lateinit var api: ConfigApi
    private lateinit var store: ConfigStore
    private var server: HttpServer? = null
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = createWebView()
        status = TextView(this).apply { setTextColor(0xFFFF5555.toInt()); textSize = 20f }
        drawer = SettingsDrawer(this, object : DrawerListener {
            override fun onServerToggle(enabled: Boolean) = setServerEnabled(enabled)
            override fun onExport() = pickFile(Intent.ACTION_CREATE_DOCUMENT, REQUEST_EXPORT)
            override fun onImport() = pickFile(Intent.ACTION_OPEN_DOCUMENT, REQUEST_IMPORT)
        })
        container = FrameLayout(this)
        setContentView(container.apply {
            addView(webView)
            addView(status)
            addView(edgeStrip(), FrameLayout.LayoutParams(dp(EDGE_STRIP_DP), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START))
            addView(drawer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })

        store = ConfigStore(File(filesDir, CONFIG_ASSET), readAsset(CONFIG_ASSET))
        val pages = try {
            store.load()
        } catch (e: ConfigException) {
            Log.e(TAG, e.message.orEmpty(), e)
            status.text = "${e.message} - fix it via http://<board-ip>:$SETTINGS_PORT or delete the app data"
            emptyList()
        }
        if (pages.isNotEmpty()) applyConfig(pages)
        api = ConfigApi(store, readAsset(UI_ASSET)) { changed -> runOnUiThread { applyConfig(changed) } }
        setServerEnabled(getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_SERVER_ENABLED, true))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webViewClient = DashboardClient()
    }

    /** The renderer process died (crash or OOM kill): the WebView is unusable, so replace it and restart the rotation. */
    private fun replaceWebView() {
        controller?.stop()
        container.removeView(webView)
        webView.destroy()
        webView = createWebView()
        container.addView(webView, 0)
        status.text = "Renderer restarted"
        if (resumed) controller?.start()
    }

    /** Invisible strip on the left edge: swiping right from it opens the drawer. */
    @SuppressLint("ClickableViewAccessibility")
    private fun edgeStrip(): View {
        var downX = 0f
        return View(this).apply {
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downX = event.x
                    MotionEvent.ACTION_MOVE -> if (!drawer.isOpen && event.x - downX > dp(SWIPE_OPEN_DP)) drawer.open()
                }
                true
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pickFile(action: String, requestCode: Int) {
        val intent = Intent(action).addCategory(Intent.CATEGORY_OPENABLE)
        if (requestCode == REQUEST_EXPORT) {
            intent.setType("application/json").putExtra(Intent.EXTRA_TITLE, EXPORT_FILE_NAME)
        } else {
            intent.setType("*/*") // files from USB sticks often have no json mime type
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            drawer.showMessage("Kein Dateiauswahl-Dialog auf diesem Gerät - Export/Import nicht möglich")
        }
    }

    @Deprecated("Result of the system file picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return // picker cancelled
        if (requestCode == REQUEST_EXPORT) exportTo(uri) else importFrom(uri)
    }

    private fun exportTo(uri: Uri) {
        try {
            val out = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Datei nicht beschreibbar")
            out.use { ConfigTransfer.export(store.load(), it) }
            drawer.showMessage("Export erfolgreich")
        } catch (e: IOException) {
            drawer.showMessage("Export fehlgeschlagen: ${e.message} - anderen Speicherort wählen")
        } catch (e: ConfigException) {
            drawer.showMessage("Export fehlgeschlagen: ${e.message}")
        }
    }

    private fun importFrom(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: throw IOException("Datei nicht lesbar")
            val pages = input.use { ConfigTransfer.parseImport(it) } // validated before anything is overwritten
            store.save(pages)
            applyConfig(pages)
            drawer.showMessage("Import erfolgreich: ${pages.size} Seite(n)")
        } catch (e: IOException) {
            drawer.showMessage("Import fehlgeschlagen: ${e.message} - Datei prüfen")
        } catch (e: ConfigException) {
            drawer.showMessage("Import fehlgeschlagen: ${e.message}")
        }
    }

    private fun readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    private fun setServerEnabled(enabled: Boolean) {
        if (enabled) startServer() else stopServer()
    }

    private fun startServer() {
        val httpServer = HttpServer(SETTINGS_PORT, api) { Log.w(TAG, it) }
        try {
            httpServer.start()
        } catch (e: IOException) {
            val message = "Start fehlgeschlagen: ${e.message} - ist Port $SETTINGS_PORT belegt? Schalter erneut betätigen."
            Log.e(TAG, "settings server failed", e)
            status.text = message
            drawer.showServer(false, message)
            return
        }
        server = httpServer
        saveServerEnabled(true)
        drawer.showServer(true, "Läuft: http://${LocalAddress.findIpv4() ?: "<IP unbekannt>"}:$SETTINGS_PORT")
    }

    private fun stopServer() {
        server?.stop()
        server = null
        saveServerEnabled(false)
        drawer.showServer(false, "Gestoppt - Einstellungen nur noch über dieses Menü erreichbar")
    }

    private fun saveServerEnabled(enabled: Boolean) =
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_SERVER_ENABLED, enabled).apply()

    /** Swaps in a new page list; runs on the UI thread. Restarts the rotation if the app is in the foreground. */
    private fun applyConfig(pages: List<Page>) {
        controller?.stop()
        controller = RotationController(
            Rotation(pages), WebViewLoader(), HandlerTimer(), LOAD_TIMEOUT_MS, ERROR_DELAY_MS
        )
        if (resumed) controller?.start()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        resumed = true
        controller?.start()
    }

    override fun onPause() {
        resumed = false
        controller?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    @Deprecated("Kiosk: back button only closes the drawer")
    override fun onBackPressed() {
        if (drawer.isOpen) drawer.close()
    }

    private fun hideSystemBars() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Browser-style zoom independent of the page's viewport meta tag: the WebView is laid out at
     * screen size / zoom (so the page sees a smaller viewport) and scaled back up to fill the screen.
     */
    private fun applyZoom(zoomPercent: Int) {
        val scale = zoomPercent / 100f
        val screen = windowManager.currentWindowMetrics.bounds
        webView.layoutParams = FrameLayout.LayoutParams(
            (screen.width() / scale).toInt(), (screen.height() / scale).toInt()
        )
        webView.pivotX = 0f
        webView.pivotY = 0f
        webView.scaleX = scale
        webView.scaleY = scale
    }

    private inner class WebViewLoader : PageLoader {
        override fun load(page: Page) {
            Log.i(TAG, "load ${page.url} zoom=${page.zoom}")
            status.text = ""
            applyZoom(page.zoom)
            webView.loadUrl(page.url)
        }
    }

    private class HandlerTimer : Timer {
        private val handler = Handler(Looper.getMainLooper())
        private var pending: Runnable? = null

        override fun start(delayMs: Long, action: () -> Unit) {
            cancel()
            pending = Runnable(action).also { handler.postDelayed(it, delayMs) }
        }

        override fun cancel() {
            pending?.let(handler::removeCallbacks)
            pending = null
        }
    }

    private inner class DashboardClient : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            Log.i(TAG, "finished $url")
            controller?.onPageLoaded()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(TAG, "renderer gone, crashed=${detail.didCrash()}")
            replaceWebView()
            return true
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) fail("${request.url}: ${error.description}")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame) fail("${request.url}: HTTP ${response.statusCode}")
        }

        private fun fail(message: String) {
            Log.w(TAG, "failed $message")
            status.text = "Load failed: $message"
            controller?.onPageFailed()
        }
    }
}
