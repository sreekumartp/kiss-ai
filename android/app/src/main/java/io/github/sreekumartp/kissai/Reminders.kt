package io.github.sreekumartp.kissai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Weekly study reminders for the learner's committed goal.
 *
 * The page sends the fixed study plan (goal name, start date, hours per week and, for each
 * topic, when the plan expects it done and whether it is learned). When a reminder fires it
 * works out "on track / N behind / N ahead" for that moment, so it stays accurate even if the
 * app has not been opened. WorkManager keeps the weekly schedule across restarts.
 */
object Reminders {
    private const val PREFS = "reminders"
    private const val CHANNEL = "weekly"
    private const val WORK = "weekly-reminder"
    private const val NOTIFICATION_ID = 1001

    fun savePlan(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("plan", json).apply()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** day: 1 = Monday … 7 = Sunday. */
    fun schedule(context: Context, day: Int, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        val dow = DayOfWeek.of(day.coerceIn(1, 7))
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(dow))
            .atTime(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val delay = ChronoUnit.MINUTES.between(now, next).coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Title and text for the reminder, or null when there is no plan. */
    fun message(context: Context): Pair<String, String>? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("plan", null) ?: return null
        val plan = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val goal = plan.optString("goal").ifEmpty { return null }
        val hpw = plan.optDouble("hpw", 10.0)
        val start = runCatching { LocalDate.parse(plan.optString("start")) }.getOrNull() ?: return null
        val items = plan.optJSONArray("items") ?: return null
        val days = ChronoUnit.DAYS.between(start, LocalDate.now())
        val planned = maxOf(0.0, days / 7.0 * hpw)
        var expected = 0
        var learned = 0
        var next: JSONObject? = null
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            if (it.optDouble("end") <= planned + 0.01) expected++
            if (it.optBoolean("learned")) learned++ else if (next == null) next = it
        }
        val nextText = next?.let { " Next up: ${it.optString("label")} (about ${it.optInt("hours")} h)." } ?: ""
        val diff = learned - expected
        val text = when {
            items.length() > 0 && learned == items.length() -> "Goal complete. Well done!"
            days < 0 -> "Your plan starts on $start.$nextText"
            diff < 0 -> "${-diff} topic${if (diff < -1) "s" else ""} behind this week.$nextText"
            diff == 0 -> "On track.$nextText"
            else -> "$diff topic${if (diff > 1) "s" else ""} ahead.$nextText"
        }
        return goal to text
    }

    fun notifyNow(context: Context) {
        if (!canNotify(context)) return
        val (title, text) = message(context) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.reminder_channel_desc) })
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission was withdrawn after the check; nothing to show.
        }
    }
}

/** Runs once a week and shows the reminder. */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Reminders.notifyNow(applicationContext)
        return Result.success()
    }
}
