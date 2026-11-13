package com.focuslock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** All local persistence: apps, session, strict mode, schedules, breaks, goal and statistics. */
object FocusStore {
    private const val PREFS = "focuslock"
    private const val K_APPS = "apps"
    private const val K_END = "end_at"
    private const val K_TOTAL = "total_ms"
    private const val K_MIN = "minutes"
    private const val K_STRICT = "strict"
    private const val K_GOAL = "goal_min"
    private const val K_BREAK = "break_until"
    private const val K_COOL = "cooldown_until"
    private const val K_SCHED = "schedules"
    private const val K_COUNTED = "counted_end"
    private const val K_BREAK_START = "break_start"
    private const val K_REMINDED = "reminded_start"
    const val COOLDOWN_MS = 30 * 60_000L

    fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- apps & timer ----
    fun blockedApps(c: Context): Set<String> =
        prefs(c).getStringSet(K_APPS, emptySet())?.toSet() ?: emptySet()
    fun setBlockedApps(c: Context, apps: Set<String>) =
        prefs(c).edit().putStringSet(K_APPS, HashSet(apps)).apply()

    fun minutes(c: Context) = prefs(c).getInt(K_MIN, 45)
    fun setMinutes(c: Context, m: Int) = prefs(c).edit().putInt(K_MIN, m).apply()

    fun endAt(c: Context) = prefs(c).getLong(K_END, 0L)
    fun totalMs(c: Context) = prefs(c).getLong(K_TOTAL, 0L)

    fun start(c: Context, minutes: Int): Long {
        val total = minutes * 60_000L
        val end = System.currentTimeMillis() + total
        prefs(c).edit().putLong(K_END, end).putLong(K_TOTAL, total).putInt(K_MIN, minutes).commit()
        return end
    }
    fun stop(c: Context) { prefs(c).edit().putLong(K_END, 0L).commit() }

    // ---- strict mode & goal ----
    fun strict(c: Context) = prefs(c).getBoolean(K_STRICT, false)
    fun setStrict(c: Context, v: Boolean) = prefs(c).edit().putBoolean(K_STRICT, v).commit()
    fun goalMin(c: Context) = prefs(c).getInt(K_GOAL, 240)
    fun setGoalMin(c: Context, m: Int) = prefs(c).edit().putInt(K_GOAL, m).apply()

    // ---- emergency break ----
    fun breakUntil(c: Context) = prefs(c).getLong(K_BREAK, 0L)
    fun cooldownUntil(c: Context) = prefs(c).getLong(K_COOL, 0L)
    fun breakStart(c: Context) = prefs(c).getLong(K_BREAK_START, 0L)
    fun reminded(c: Context) = prefs(c).getLong(K_REMINDED, 0L)
    fun setReminded(c: Context, start: Long) = prefs(c).edit().putLong(K_REMINDED, start).commit()

    fun startBreak(c: Context, minutes: Int): Boolean {
        val now = System.currentTimeMillis()
        if (now < breakUntil(c) || now < cooldownUntil(c) || currentBlock(c, now) == null) return false
        val until = now + minutes * 60_000L
        prefs(c).edit().putLong(K_BREAK, until).putLong(K_BREAK_START, now).putLong(K_COOL, until + COOLDOWN_MS).commit()
        return true
    }
    fun endBreak(c: Context) {
        val now = System.currentTimeMillis()
        prefs(c).edit().putLong(K_BREAK, now).putLong(K_COOL, now + COOLDOWN_MS).commit()
    }

    // ---- schedules ----
    private var cacheRaw: String? = null
    private var cacheList: List<Schedule> = emptyList()

    @Synchronized
    fun schedules(c: Context): List<Schedule> {
        val raw = prefs(c).getString(K_SCHED, "[]") ?: "[]"
        if (raw == cacheRaw) return cacheList
        val list = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Schedule(o.getLong("id"), o.getInt("days"), o.getInt("s"), o.getInt("e"), o.getBoolean("on"))
            }
        }.getOrDefault(emptyList())
        cacheRaw = raw; cacheList = list
        return list
    }

    fun setSchedules(c: Context, list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("days", it.days)
                .put("s", it.startMin).put("e", it.endMin).put("on", it.enabled))
        }
        prefs(c).edit().putString(K_SCHED, arr.toString()).commit()
    }

    // ---- statistics (per local day) ----
    fun today(): String = LocalDate.now().toString()

    private fun bump(c: Context, prefix: String, by: Long) {
        val k = prefix + today()
        prefs(c).edit().putLong(k, prefs(c).getLong(k, 0L) + by).apply()
    }
    fun addFocusMs(c: Context, ms: Long) = bump(c, "f_", ms)
    fun addBlocked(c: Context) = bump(c, "b_", 1)

    fun focusSec(c: Context, date: String) = prefs(c).getLong("f_$date", 0L) / 1000
    fun sessions(c: Context, date: String) = prefs(c).getLong("s_$date", 0L).toInt()
    fun blockedCount(c: Context, date: String) = prefs(c).getLong("b_$date", 0L).toInt()

    /** Counts a timer session as completed once it ran to zero (never when stopped early). */
    fun finalizeIfDone(c: Context) {
        val end = endAt(c)
        if (end > 0 && System.currentTimeMillis() >= end && prefs(c).getLong(K_COUNTED, 0L) != end) {
            prefs(c).edit().putLong(K_COUNTED, end).apply()
            bump(c, "s_", 1)
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
