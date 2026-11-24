package io.github.sreekumartp.kissai

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the page up to date without a Play release.
 *
 * The app ships a copy of the page in its assets. When the published website has a newer
 * build (its version.json differs), the new index.html is downloaded into app storage and
 * served instead of the bundled one, from the same origin, so study progress in
 * localStorage is kept and the app still works offline. Only the web page is updated;
 * no app code is downloaded.
 *
 * A downloaded page belongs to the app version it was fetched under: after the app itself
 * is updated, the bundled page is used again until the site publishes something newer.
 */
class LiveUpdate(private val context: Context) {

    private val dir = File(context.filesDir, "live")
    private val liveHtml = File(dir, "index.html")
    private val liveMeta = File(dir, "meta.json")
    @Volatile private var lastCheck = 0L

    private val bundledBuild: String by lazy {
        runCatching {
            context.assets.open("version.json").bufferedReader().use { JSONObject(it.readText()).optString("build") }
        }.getOrDefault("")
    }

    /** The downloaded page, if there is one that is valid for this app version. */
    private fun activeLive(): File? {
        if (!liveHtml.isFile || !liveMeta.isFile) return null
        val meta = runCatching { JSONObject(liveMeta.readText()) }.getOrNull() ?: return null
        if (meta.optString("base") != bundledBuild) { clear(); return null }
        return liveHtml
    }

    private fun currentBuild(): String =
        activeLive()?.let { runCatching { JSONObject(liveMeta.readText()).optString("build") }.getOrNull() }
            ?: bundledBuild

    private fun clear() { liveHtml.delete(); liveMeta.delete() }

    /** Asset handler that serves the downloaded page in place of the bundled index.html. */
    fun pathHandler(): WebViewAssetLoader.PathHandler {
        val assets = WebViewAssetLoader.AssetsPathHandler(context)
        return WebViewAssetLoader.PathHandler { path ->
            if (path == "index.html") {
                activeLive()?.let { return@PathHandler WebResourceResponse("text/html", "utf-8", FileInputStream(it)) }
            }
            assets.handle(path)
        }
    }

    /**
     * Checks the website for a newer page and downloads it. Blocking: call off the main thread.
     * Returns true when the page to show has changed, so the WebView should reload.
     */
    fun check(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCheck < MIN_INTERVAL_MS) return false
        lastCheck = now
        return runCatching {
            val stamp = "?t=$now"
            val remote = JSONObject(fetch("$SITE/version.json$stamp")).optString("build")
            if (remote.isEmpty() || remote == currentBuild()) return@runCatching false
            if (remote == bundledBuild) { clear(); return@runCatching true }
            val html = fetch("$SITE/index.html$stamp")
            // Only accept a complete page of the expected build.
            if (html.length < 100_000 || !html.contains("window.ATLAS_BUILD=\"$remote\"") || !html.contains("</html>")) {
                return@runCatching false
            }
            dir.mkdirs()
            val tmp = File(dir, "index.html.part")
            tmp.writeText(html)
            if (!tmp.renameTo(liveHtml)) { tmp.delete(); return@runCatching false }
            liveMeta.writeText(JSONObject().put("build", remote).put("base", bundledBuild).toString())
            true
        }.getOrDefault(false)
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val SITE = "https://sreekumartp.github.io/kiss-ai"
        private const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    }
}
