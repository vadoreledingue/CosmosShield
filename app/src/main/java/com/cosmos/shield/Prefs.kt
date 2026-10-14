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

    fun grayByUs(c: Context): Boolean = sp(c).getBoolean("gray_by_us", false)
    fun setGrayByUs(c: Context, v: Boolean) { sp(c).edit().putBoolean("gray_by_us", v).apply() }

    fun notifAsked(c: Context): Boolean = sp(c).getBoolean("notif_asked", false)
    fun setNotifAsked(c: Context) { sp(c).edit().putBoolean("notif_asked", true).apply() }
}
