package com.focuslock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/**
 * Runs only while something is blocking (a timer session or a scheduled window) and stops itself after.
 * Every ~0.8s it reads new UsageStats foreground events; a blocked app opening shows BlockActivity.
 * Focus time is measured from timestamps (screen on or off, minus emergency breaks), so it stays
 * accurate while you study away from the phone. A short watchdog alarm restarts the service if the
 * user force-stops the app from the "Active apps" list.
 */
class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usm: UsageStatsManager
    private var running = false
    private var lastEventTs = 0L
    private var lastBlockAt = 0L
    private var lastTick = 0L
    private var lastBlockEnd = 0L
    private var lastArm = 0L
    private var accMs = 0L
    private var currentFg: String? = null
    private var needCheck = true

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val block = currentBlock(this@BlockerService, now)
            creditFocus(now)
            if (block == null) { shutdown(); return }
            lastBlockEnd = block.end
            lastTick = now
            if (accMs >= 20_000) flush()
            if (now - lastArm >= 5_000) { Scheduler.armWatchdog(this@BlockerService, now + 15_000); lastArm = now }
            poll(now, now < FocusStore.breakUntil(this@BlockerService))
            handler.postDelayed(this, 800)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usm = getSystemService(UsageStatsManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        val now = System.currentTimeMillis()
        val block = currentBlock(this, now)
        if (block == null) { shutdown(); return START_NOT_STICKY }
        if (!running) {
            running = true
            lastEventTs = now - 30 * 60_000L   // replay recent events to learn which app is open now
            lastTick = now
            lastBlockEnd = block.end
            lastArm = 0L
            currentFg = null
            needCheck = true
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    /** Adds the time since the last tick, never past the end of the block and never during a break. */
    private fun creditFocus(now: Long) {
        val upTo = minOf(now, lastBlockEnd)
        var ms = upTo - lastTick
        if (ms <= 0) return
        val overlap = minOf(upTo, FocusStore.breakUntil(this)) - maxOf(lastTick, FocusStore.breakStart(this))
        if (overlap > 0) ms -= overlap
        if (ms > 0) accMs += ms
    }

    @Suppress("DEPRECATION")
    private fun poll(now: Long, onBreak: Boolean) {
        val events = usm.queryEvents(lastEventTs - 2000, now)
        val e = UsageEvents.Event()
        var maxTs = lastEventTs
        var newFg = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.timeStamp <= lastEventTs) continue
            if (e.timeStamp > maxTs) maxTs = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { currentFg = e.packageName; newFg = true }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == currentFg) currentFg = null
            }
        }
        lastEventTs = maxTs
        if (onBreak) { needCheck = true; return }   // re-check as soon as the break ends
        val fg = currentFg
        if ((newFg || needCheck) && fg != null && fg in FocusStore.blockedApps(this) && now - lastBlockAt > 1200) {
            lastBlockAt = now
            FocusStore.addBlocked(this)
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        needCheck = false
    }

    private fun flush() {
        if (accMs > 0) { FocusStore.addFocusMs(this, accMs); accMs = 0 }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("focus", "Focus session", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "focus")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Focus mode is on")
            .setContentText("Your selected apps are blocked")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun shutdown() {
        handler.removeCallbacks(tick)
        flush()
        FocusStore.finalizeIfDone(this)
        Scheduler.cancelWatchdog(this)
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Scheduler.sync(this)   // re-arm the next schedule alarm
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        flush()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
