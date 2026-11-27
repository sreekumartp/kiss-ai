package io.github.sreekumartp.kissai

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.Executors

/**
 * Hosts the AI & ML Knowledge Atlas. The page and everything it needs (d3, fonts)
 * ship inside the APK and are served from a virtual https origin, so the app works
 * offline and localStorage (study progress) persists between launches. Newer pages
 * published on the website are picked up automatically by [LiveUpdate].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val liveUpdate by lazy { LiveUpdate(applicationContext) }
    private val updateExecutor = Executors.newSingleThreadExecutor()

    // A reminder waiting for the notification permission answer (Android 13+).
    private var pendingReminder: IntArray? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val r = pendingReminder; pendingReminder = null
        if (granted && r != null) Reminders.schedule(applicationContext, r[0], r[1], r[2])
        reminderResult(granted)
    }

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", liveUpdate.pathHandler())
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A WebView ignores its own padding when laying out web content, so the
        // system-bar insets go on a container around it. Otherwise the page's
        // bottom menu sits under the navigation bar.
        webView = WebView(this)
        val root = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.atlas_surface))
            addView(webView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(root)

        // Keep page content clear of the status bar, navigation bar, display
        // cutouts and the on-screen keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            // Respect the user's system font size.
            textZoom = (resources.configuration.fontScale * 100).toInt()
        }
        webView.setBackgroundColor(getColor(R.color.atlas_bg))
        webView.addJavascriptInterface(AtlasBridge(this), "AtlasNative")
        webView.webViewClient = AtlasClient()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(START_URL)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Let the page close its popup, search list or curriculum view first.
                webView.evaluateJavascript("window.atlasBack ? window.atlasBack() : false") { handled ->
                    if (handled != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        checkForUpdate()
    }

    /** Looks for a newer published page in the background; the page reloads itself when idle. */
    private fun checkForUpdate() {
        updateExecutor.execute {
            if (liveUpdate.check()) {
                runOnUiThread {
                    if (!isDestroyed) {
                        webView.evaluateJavascript(
                            "window.atlasUpdateReady ? window.atlasUpdateReady() : location.reload()", null)
                    }
                }
            }
        }
    }

    /** Schedules or cancels the weekly reminder, asking for notification permission first if needed. */
    fun setReminder(on: Boolean, day: Int, hour: Int, minute: Int) {
        if (!on) { Reminders.cancel(applicationContext); reminderResult(true); return }
        if (Reminders.canNotify(this)) {
            Reminders.schedule(applicationContext, day, hour, minute); reminderResult(true)
        } else {
            pendingReminder = intArrayOf(day, hour, minute)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun reminderResult(ok: Boolean) {
        if (!isDestroyed) webView.evaluateJavascript("window.atlasReminderResult && window.atlasReminderResult($ok)", null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        webView.settings.textZoom = (newConfig.fontScale * 100).toInt()
        // Light/dark switches recreate the activity's theme colours via AppCompat.
        webView.setBackgroundColor(getColor(R.color.atlas_bg))
    }

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    /** Opens a learning resource in a Custom Tab, falling back to any browser. */
    fun openExternal(uri: Uri) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class AtlasClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
            if (url.scheme == "http" || url.scheme == "https") {
                openExternal(url)
            }
            return true
        }
    }

    companion object {
        private const val START_URL = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/index.html"
    }
}
