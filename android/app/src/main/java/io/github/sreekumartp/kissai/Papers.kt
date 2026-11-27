package io.github.sreekumartp.kissai

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment

/**
 * Saves arXiv papers as PDFs for reading offline.
 *
 * Files go to the app's own Downloads folder (no storage permission needed; removed when the
 * app is uninstalled) through the system DownloadManager, which shows progress and finishes
 * even if the app is closed. Each paper's download id is remembered so the page can show
 * "Saving…", "Open offline" or "Retry".
 */
object Papers {
    private const val PREFS = "papers"
    private val ID = Regex("^[0-9]{4}\\.[0-9]{4,5}(v[0-9]+)?$")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(DownloadManager::class.java)

    /** Starts saving a paper; arxivId looks like 2005.11401. Returns false for an invalid id. */
    fun save(context: Context, arxivId: String, title: String): Boolean {
        if (!ID.matches(arxivId)) return false
        val clean = title.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().take(60).ifEmpty { "paper" }
        val request = DownloadManager.Request(Uri.parse("https://arxiv.org/pdf/$arxivId"))
            .setTitle(title.take(100))
            .setDescription("arXiv $arxivId · K.I.S.S AI")
            .setMimeType("application/pdf")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "$arxivId - $clean.pdf")
        val id = manager(context).enqueue(request)
        prefs(context).edit().putLong(arxivId, id).apply()
        return true
    }

    /** "none", "saving", "saved" or "failed". */
    fun status(context: Context, arxivId: String): String {
        val id = prefs(context).getLong(arxivId, -1L)
        if (id < 0) return "none"
        manager(context).query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c == null || !c.moveToFirst()) { prefs(context).edit().remove(arxivId).apply(); return "none" }
            return when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> "saved"
                DownloadManager.STATUS_FAILED -> "failed"
                else -> "saving"
            }
        }
    }

    /** Opens a saved paper in a PDF viewer. Returns false if it isn't saved or no viewer exists. */
    fun open(context: Context, arxivId: String): Boolean {
        val id = prefs(context).getLong(arxivId, -1L)
        if (id < 0) return false
        val uri = manager(context).getUriForDownloadedFile(id) ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Deletes a saved paper. */
    fun remove(context: Context, arxivId: String) {
        val id = prefs(context).getLong(arxivId, -1L)
        if (id >= 0) manager(context).remove(id)
        prefs(context).edit().remove(arxivId).apply()
    }
}
