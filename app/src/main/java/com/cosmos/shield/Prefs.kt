package com.cosmos.shield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShieldApp(val pkg: String, val name: String, val grayscale: Boolean, val breathGate: Boolean)

/** Stockage natif : config du bouclier (poussée par la page web) et sessions actives. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cosmos_native", Context.MODE_PRIVATE)

    fun rawConfig(c: Context): String = sp(c).getString("config", "[]") ?: "[]"
    fun saveConfig(c: Context, json: String) { sp(c).edit().putString("config", json).apply() }

    fun loadConfig(c: Context): Map<String, ShieldApp> {
        val out = LinkedHashMap<String, ShieldApp>()
        try {
            val arr = JSONArray(rawConfig(c))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkg = o.getString("pkg")
                out[pkg] = ShieldApp(pkg, o.optString("name", pkg), o.optBoolean("grayscale", true), o.optBoolean("breathGate", true))
            }
        } catch (e: Exception) { /* config invalide : on ignore */ }
        return out
    }

    private fun sessions(c: Context): JSONObject =
        try { JSONObject(sp(c).getString("sessions", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun saveSessions(c: Context, s: JSONObject) { sp(c).edit().putString("sessions", s.toString()).apply() }

    fun startSession(c: Context, pkg: String, mins: Int) {
        val s = sessions(c)
        s.put(pkg, System.currentTimeMillis() + mins * 60_000L)
        saveSessions(c, s)
    }

    fun endSession(c: Context, pkg: String) {
        val s = sessions(c)
        if (s.has(pkg)) { s.remove(pkg); saveSessions(c, s) }
    }

    fun sessionActive(c: Context, pkg: String): Boolean = sessions(c).optLong(pkg, 0L) > System.currentTimeMillis()

    /** Session la plus longue encore active : (paquet, fin en ms) */
    fun activeSession(c: Context): Pair<String, Long>? {
        val s = sessions(c)
        val now = System.currentTimeMillis()
        var best: Pair<String, Long>? = null
        val keys = s.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val end = s.optLong(k, 0L)
            if (end > now && (best == null || end > best.second)) best = Pair(k, end)
        }
        return best
    }



    fun notifAsked(c: Context): Boolean = sp(c).getBoolean("notif_asked", false)
    fun setNotifAsked(c: Context) { sp(c).edit().putBoolean("notif_asked", true).apply() }

    // Grayscale overlay intensity (0-100)
    fun grayscaleIntensity(c: Context): Int = sp(c).getInt("gray_intensity", 80)
    fun setGrayscaleIntensity(c: Context, v: Int) { sp(c).edit().putInt("gray_intensity", v.coerceIn(0, 100)).apply() }

    // Notification settings
    fun notifCurfew(c: Context): Boolean = sp(c).getBoolean("notif_curfew", true)
    fun setNotifCurfew(c: Context, v: Boolean) { sp(c).edit().putBoolean("notif_curfew", v).apply() }
    fun notifMorning(c: Context): Boolean = sp(c).getBoolean("notif_morning", true)
    fun setNotifMorning(c: Context, v: Boolean) { sp(c).edit().putBoolean("notif_morning", v).apply() }
    fun notifShield(c: Context): Boolean = sp(c).getBoolean("notif_shield", true)
    fun setNotifShield(c: Context, v: Boolean) { sp(c).edit().putBoolean("notif_shield", v).apply() }

    // Sleep & Curfew hours settings
    fun curfewTime(c: Context): String = sp(c).getString("curfew_time", "22:00") ?: "22:00"
    fun setCurfewTime(c: Context, v: String) { sp(c).edit().putString("curfew_time", v).apply() }
    fun morningTime(c: Context): String = sp(c).getString("morning_time", "08:00") ?: "08:00"
    fun setMorningTime(c: Context, v: String) { sp(c).edit().putString("morning_time", v).apply() }

    // Sleep block (night app lockdown)
    fun sleepBlockEnabled(c: Context): Boolean = sp(c).getBoolean("sleep_block_enabled", true)
    fun setSleepBlockEnabled(c: Context, v: Boolean) { sp(c).edit().putBoolean("sleep_block_enabled", v).apply() }
    fun sleepBlockStart(c: Context): String = sp(c).getString("sleep_block_start", "22:00") ?: "22:00"
    fun setSleepBlockStart(c: Context, v: String) { sp(c).edit().putString("sleep_block_start", v).apply() }
    fun sleepBlockEnd(c: Context): String = sp(c).getString("sleep_block_end", "08:00") ?: "08:00"
    fun setSleepBlockEnd(c: Context, v: String) { sp(c).edit().putString("sleep_block_end", v).apply() }
    fun sleepGrayscale(c: Context): Boolean = sp(c).getBoolean("sleep_grayscale", true)
    fun setSleepGrayscale(c: Context, v: Boolean) { sp(c).edit().putBoolean("sleep_grayscale", v).apply() }

    fun parseTime(time: String, defH: Int, defM: Int): Pair<Int, Int> {
        val parts = time.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull() ?: defH
            val m = parts[1].toIntOrNull() ?: defM
            return Pair(h.coerceIn(0, 23), m.coerceIn(0, 59))
        }
        return Pair(defH, defM)
    }

    fun isTimeInRange(curH: Int, curM: Int, startStr: String, endStr: String): Boolean {
        val (sh, sm) = parseTime(startStr, 22, 0)
        val (eh, em) = parseTime(endStr, 8, 0)
        val cur = curH * 60 + curM
        val start = sh * 60 + sm
        val end = eh * 60 + em
        return if (start <= end) {
            cur in start until end
        } else {
            cur >= start || cur < end
        }
    }

    fun isSleepBlockActiveNow(c: Context): Boolean {
        if (!sleepBlockEnabled(c)) return false
        val cal = java.util.Calendar.getInstance()
        return isTimeInRange(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), sleepBlockStart(c), sleepBlockEnd(c))
    }
}

