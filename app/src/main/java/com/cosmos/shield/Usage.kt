package com.cosmos.shield

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.util.Calendar

/** Temps d'écran réel via UsageStatsManager (permission « Accès aux données d'utilisation »). */
object Usage {
    const val EV_RESUMED = 1      // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
    const val EV_PAUSED = 2       // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
    const val EV_SCREEN_OFF = 16  // SCREEN_NON_INTERACTIVE
    const val EV_STOPPED = 23     // ACTIVITY_STOPPED

    class Result(val totalMs: Long, val perApp: Map<String, Long>)

    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun homePackages(c: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return c.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    /** Temps passé au premier plan depuis minuit, par application. */
    fun today(c: Context): Result {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(cal.timeInMillis, now)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val acc = HashMap<String, Long>()

        fun close(pkg: String, ts: Long) {
            val start = open.remove(pkg) ?: return
            if (ts > start) acc[pkg] = (acc[pkg] ?: 0L) + (ts - start)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                EV_RESUMED -> if (!open.containsKey(pkg)) open[pkg] = e.timeStamp
                EV_PAUSED, EV_STOPPED -> close(pkg, e.timeStamp)
                EV_SCREEN_OFF -> for (k in ArrayList(open.keys)) close(k, e.timeStamp)
            }
        }
        for (k in ArrayList(open.keys)) close(k, now)

        val skip = homePackages(c) + setOf("com.android.systemui", c.packageName)
        val perApp = acc.filterKeys { it !in skip }
        return Result(perApp.values.sum(), perApp)
    }
}
