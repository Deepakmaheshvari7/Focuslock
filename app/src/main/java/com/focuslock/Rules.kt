package com.focuslock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** days: bit0 = Monday ... bit6 = Sunday. Times are minutes since midnight; end < start means overnight. */
data class Schedule(val id: Long, val days: Int, val startMin: Int, val endMin: Int, val enabled: Boolean)

enum class Kind { SESSION, SCHEDULE }
data class Block(val kind: Kind, val start: Long, val end: Long)

private fun dayIndex(now: Long, offset: Int): Int {
    val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Mon=0 ... Sun=6
}

private fun timeAt(now: Long, offset: Int, minute: Int): Long {
    val c = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_YEAR, offset) }
    c.set(Calendar.HOUR_OF_DAY, minute / 60); c.set(Calendar.MINUTE, minute % 60)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun windows(s: Schedule, now: Long): List<Pair<Long, Long>> {
    if (!s.enabled || s.days == 0 || s.startMin == s.endMin) return emptyList()
    val overnight = s.endMin < s.startMin
    val out = ArrayList<Pair<Long, Long>>()
    for (d in -1..7) {
        if ((s.days and (1 shl dayIndex(now, d))) == 0) continue
        out += timeAt(now, d, s.startMin) to timeAt(now, if (overnight) d + 1 else d, s.endMin)
    }
    return out
}

fun isScheduleActive(s: Schedule, now: Long): Boolean =
    windows(s, now).any { now >= it.first && now < it.second }

/** End time of the window of [s] that is running at [now], or null if it isn't running. */
fun activeEnd(s: Schedule, now: Long): Long? =
    windows(s, now).filter { now >= it.first && now < it.second }.maxOfOrNull { it.second }

fun scheduleWindow(list: List<Schedule>, now: Long): Pair<Long, Long>? {
    var start = Long.MAX_VALUE
    var end = 0L
    for (s in list) for ((a, b) in windows(s, now)) {
        if (now >= a && now < b) { start = minOf(start, a); end = maxOf(end, b) }
    }
    return if (end > 0) start to end else null
}

fun nextBoundary(list: List<Schedule>, now: Long): Long? {
    var best = Long.MAX_VALUE
    for (s in list) for ((a, b) in windows(s, now)) {
        if (a > now) best = minOf(best, a)
        if (b > now) best = minOf(best, b)
        if (a - REMIND_MS > now) best = minOf(best, a - REMIND_MS)   // "starting soon" reminder
    }
    return if (best == Long.MAX_VALUE) null else best
}

const val REMIND_MS = 10 * 60_000L

fun nextStart(list: List<Schedule>, now: Long): Long? {
    var best = Long.MAX_VALUE
    for (s in list) for ((a, _) in windows(s, now)) if (a > now) best = minOf(best, a)
    return if (best == Long.MAX_VALUE) null else best
}

/** What is blocking right now: a manual timer session, else a scheduled window, else nothing. */
fun currentBlock(ctx: Context, now: Long): Block? {
    val end = FocusStore.endAt(ctx)
    if (now < end) return Block(Kind.SESSION, end - FocusStore.totalMs(ctx), end)
    return scheduleWindow(FocusStore.schedules(ctx), now)?.let { Block(Kind.SCHEDULE, it.first, it.second) }
}

/** What the user may not change right now. A running schedule window is always locked;
 *  a manual timer session is locked only in strict mode. */
object Locks {
    fun scheduleWindowActive(ctx: Context, now: Long) =
        scheduleWindow(FocusStore.schedules(ctx), now) != null

    /** Blocked apps can be added but not removed while this is true. */
    fun appsLocked(ctx: Context, now: Long): Boolean =
        scheduleWindowActive(ctx, now) || (now < FocusStore.endAt(ctx) && FocusStore.strict(ctx))

    /**
     * Whether [old] may be changed into [new] (null = delete) right now.
     * Strict mode locks every schedule while focus is running. Otherwise only a running schedule is
     * protected: it may be made longer, but never shortened, switched off or deleted.
     */
    fun canChange(ctx: Context, old: Schedule, new: Schedule?, now: Long): Boolean {
        if (FocusStore.strict(ctx) && currentBlock(ctx, now) != null) return false
        val oldEnd = activeEnd(old, now) ?: return true          // not running: free to edit
        if (new == null || !new.enabled) return false
        val newEnd = activeEnd(new, now) ?: return false
        return newEnd >= oldEnd
    }
}

object Scheduler {
    private const val WATCHDOG_REQ = 1

    /** Sets the alarm for the next schedule start/end/reminder and starts the blocker if a window is open now. */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val pi = pending(app, 0)
        app.getSystemService(AlarmManager::class.java).cancel(pi)
        nextBoundary(FocusStore.schedules(app), now)?.let { setAlarm(app, it + 1000, pi) }
        if (currentBlock(app, now) != null) {
            try {
                ContextCompat.startForegroundService(app, Intent(app, BlockerService::class.java))
            } catch (_: Exception) { /* not allowed from background; opening the app starts it */ }
        }
    }

    /** While blocking, the service keeps a short alarm armed. If the user force-closes the app from the
     *  "Active apps" list, Android keeps alarms, so this one restarts the blocker within seconds. */
    fun armWatchdog(ctx: Context, at: Long) = setAlarm(ctx.applicationContext, at, pending(ctx.applicationContext, WATCHDOG_REQ))

    fun cancelWatchdog(ctx: Context) {
        val app = ctx.applicationContext
        app.getSystemService(AlarmManager::class.java).cancel(pending(app, WATCHDOG_REQ))
    }

    /** Posts "study starts soon" once per upcoming window, when within 10 minutes of it. */
    fun remind(ctx: Context) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val start = nextStart(FocusStore.schedules(app), now) ?: return
        if (start - now > REMIND_MS || currentBlock(app, now) != null) return
        if (FocusStore.reminded(app) == start) return
        FocusStore.setReminded(app, start)

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("reminder", "Study reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val mins = ((start - now + 59_999) / 60_000).toInt().coerceAtLeast(1)
        val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(start))
        val open = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            2,
            Notification.Builder(app, "reminder")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Study time starts in $mins min")
                .setContentText("Your study schedule begins at $at. Finish up and get ready.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun pending(ctx: Context, req: Int) = PendingIntent.getBroadcast(
        ctx, req, Intent(ctx, ScheduleReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.sync(context)
        Scheduler.remind(context)
    }
}
