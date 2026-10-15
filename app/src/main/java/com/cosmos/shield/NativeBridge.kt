package com.cosmos.shield

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Exposé à la page web sous le nom `window.AndroidBridge`. */
class NativeBridge(private val act: MainActivity) {
    private val ctx: Context = act.applicationContext

    @Volatile var pendingGate: String? = null
    @Volatile var pendingOpen: String? = null

    @JavascriptInterface
    fun isRestrictedSettingsActive(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return try {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                "android:access_restricted_settings",
                android.os.Process.myUid(),
                ctx.packageName
            )
            mode != AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun getPermissions(): String = JSONObject().apply {
        put("usage", Usage.hasAccess(ctx))
        put("overlay", Settings.canDrawOverlays(ctx))
        put("notif", ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        put("battery", ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName))
        put("restricted", isRestrictedSettingsActive())
    }.toString()

    @JavascriptInterface
    fun openSettings(type: String) {
        val pkg = Uri.parse("package:" + ctx.packageName)
        val intent = when (type) {
            "usage" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg)
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            "restricted" -> {
                act.runOnUiThread {
                    Toast.makeText(ctx, "Appuyez sur les 3 points (⋮) en haut à droite > Autoriser les paramètres restreints", Toast.LENGTH_LONG).show()
                }
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            }
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        if (start(intent)) return
        // Certains constructeurs refusent l'URI « package: » : on retente sans.
        if (type == "usage" && start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) return
        if (type == "overlay" && start(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))) return
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
    }

    private fun start(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) { false }

    @JavascriptInterface
    fun requestNotifications() { act.runOnUiThread { act.requestNotifications() } }

    @JavascriptInterface
    fun getUsage(): String {
        if (!Usage.hasAccess(ctx)) return ""
        val r = Usage.today(ctx)
        val per = JSONObject()
        r.perApp.forEach { (pkg, ms) ->
            val min = (ms / 60_000L).toInt()
            if (min > 0) per.put(pkg, min)
        }
        return JSONObject().put("totalMin", (r.totalMs / 60_000L).toInt()).put("perApp", per).toString()
    }

    @JavascriptInterface
    fun getInstalledApps(): String {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { Pair(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val arr = JSONArray()
        apps.forEach { arr.put(JSONObject().put("pkg", it.first).put("name", it.second)) }
        return arr.toString()
    }

    @JavascriptInterface
    fun setShieldConfig(json: String) {
        if (json == Prefs.rawConfig(ctx)) return
        Prefs.saveConfig(ctx, json)
        ShieldService.sync(ctx)
    }

    @JavascriptInterface
    fun startSession(pkg: String, mins: Int) { Prefs.startSession(ctx, pkg, mins) }

    @JavascriptInterface
    fun endSession(pkg: String) { Prefs.endSession(ctx, pkg) }

    @JavascriptInterface
    fun getSession(): String {
        val s = Prefs.activeSession(ctx) ?: return ""
        return JSONObject().put("pkg", s.first).put("endsAt", s.second).toString()
    }

    @JavascriptInterface
    fun launchApp(pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return start(i)
    }

    @JavascriptInterface
    fun goHome() {
        start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    /** Lu (et vidé) par la page : { gate: "com.pkg", open: "morning" } */
    @JavascriptInterface
    fun consumeIntent(): String {
        val gate = pendingGate
        val open = pendingOpen
        pendingGate = null
        pendingOpen = null
        if (gate == null && open == null) return ""
        val o = JSONObject()
        if (gate != null) o.put("gate", gate)
        if (open != null) o.put("open", open)
        return o.toString()
    }

    @JavascriptInterface
    fun copyText(text: String) {
        act.runOnUiThread {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Cosmos Shield", text))
        }
    }

    @JavascriptInterface
    fun restoreColors() { GrayscaleOverlay.hide(ctx) }

    @JavascriptInterface
    fun toast(msg: String) {
        act.runOnUiThread { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun getGrayscaleIntensity(): String = Prefs.grayscaleIntensity(ctx).toString()

    @JavascriptInterface
    fun setGrayscaleIntensity(intensity: Int) {
        Prefs.setGrayscaleIntensity(ctx, intensity)
    }

    @JavascriptInterface
    fun getNotifSettings(): String = JSONObject().apply {
        put("curfew", Prefs.notifCurfew(ctx))
        put("morning", Prefs.notifMorning(ctx))
        put("shield", Prefs.notifShield(ctx))
    }.toString()

    @JavascriptInterface
    fun setNotifSettings(json: String) {
        try {
            val o = JSONObject(json)
            if (o.has("curfew")) Prefs.setNotifCurfew(ctx, o.getBoolean("curfew"))
            if (o.has("morning")) Prefs.setNotifMorning(ctx, o.getBoolean("morning"))
            if (o.has("shield")) {
                Prefs.setNotifShield(ctx, o.getBoolean("shield"))
                ShieldService.updateNotification(ctx)
            }
            Reminders.scheduleAll(ctx)
        } catch (e: Exception) { /* invalid JSON */ }
    }

    @JavascriptInterface
    fun openShieldNotificationSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, ShieldService.CHANNEL)
        }
        if (!start(intent)) {
            start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }
}
