package io.github.sreekumartp.kissai

import android.content.Intent
import android.webkit.JavascriptInterface

/** Methods the atlas page can call as `window.AtlasNative.*`. */
class AtlasBridge(private val activity: MainActivity) {

    /** Opens the Android share sheet with a topic's syllabus and resource links. */
    @JavascriptInterface
    fun share(title: String, text: String) {
        activity.runOnUiThread {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_title)))
        }
    }

    /** Stores the committed goal's study plan, used to word the weekly reminder. */
    @JavascriptInterface
    fun reminderPlan(json: String) {
        Reminders.savePlan(activity.applicationContext, json)
    }

    /** Turns the weekly reminder on or off. day: 1 = Monday … 7 = Sunday. Calls back window.atlasReminderResult(ok). */
    @JavascriptInterface
    fun setReminder(on: Boolean, day: Int, hour: Int, minute: Int) {
        activity.runOnUiThread { activity.setReminder(on, day, hour, minute) }
    }

    /** True when the app may show notifications (always true before Android 13). */
    @JavascriptInterface
    fun notificationsAllowed(): Boolean = Reminders.canNotify(activity.applicationContext)

    /** Saves an arXiv paper as a PDF for reading offline. */
    @JavascriptInterface
    fun savePaper(arxivId: String, title: String): Boolean = Papers.save(activity.applicationContext, arxivId, title)

    /** "none", "saving", "saved" or "failed". */
    @JavascriptInterface
    fun paperStatus(arxivId: String): String = Papers.status(activity.applicationContext, arxivId)

    /** Opens a saved paper; false if it isn't saved or no PDF viewer is installed. */
    @JavascriptInterface
    fun openPaper(arxivId: String): Boolean = Papers.open(activity, arxivId)

    /** Deletes a saved paper. */
    @JavascriptInterface
    fun removePaper(arxivId: String) = Papers.remove(activity.applicationContext, arxivId)

    /** Shows the reminder now, so the learner can see what it looks like. */
    @JavascriptInterface
    fun testReminder() {
        Reminders.notifyNow(activity.applicationContext)
    }
}
