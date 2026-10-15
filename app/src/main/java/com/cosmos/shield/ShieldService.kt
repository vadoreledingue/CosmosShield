package com.cosmos.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Surveille l'app au premier plan (1 fois / seconde).
 *  - app surveillée + « grayscale »  -> écran en noir & blanc
 *  - app surveillée + « breath gate » sans session valide -> ouvre le sas de respiration
 */
class ShieldService : Service() {
    private var exec: ScheduledExecutorService? = null
    @Volatile private var cfg: Map<String, ShieldApp> = emptyMap()
    private var fg: String? = null
    private var lastTs = 0L
    private var lastGate = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Bouclier actif", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SILENT, "Bouclier discret", NotificationManager.IMPORTANCE_MIN))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cfg = Prefs.loadConfig(this)
        val n = buildNotification(cfg.size)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        if (intent?.action == ACTION_UPDATE_NOTIF) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, n)
            return START_STICKY
        }
        if (exec == null) {
            lastTs = System.currentTimeMillis() - 2 * 3600_000L
            exec = Executors.newSingleThreadScheduledExecutor().also {
                it.scheduleWithFixedDelay({ safeTick() }, 0, 1, TimeUnit.SECONDS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        exec?.shutdownNow()
        exec = null
        GrayscaleOverlay.hide(this)
        super.onDestroy()
    }

    private fun buildNotification(count: Int): Notification {
        val showNotif = Prefs.notifShield(this)
        val targetChannel = if (showNotif) CHANNEL else CHANNEL_SILENT
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = Notification.Builder(this, targetChannel)
            .setSmallIcon(R.drawable.ic_stat_cosmos)
            .setContentIntent(pi)
            .setOngoing(true)

        if (showNotif) {
            builder.setContentTitle("Cosmos Shield actif")
                .setContentText(if (count == 1) "1 application surveillée" else "$count applications surveillées")
        } else {
            builder.setContentTitle("Cosmos Shield")
                .setContentText("Protection active en arrière-plan")
        }
        return builder.build()
    }

    private fun safeTick() {
        try { tick() } catch (e: Exception) { /* on ne doit jamais tuer le thread */ }
    }

    private fun tick() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        if (!Usage.hasAccess(this)) return

        updateForeground()
        val app = fg?.let { cfg[it] }

        val isSleepBlock = Prefs.isSleepBlockActiveNow(this)
        val isSleepGray = isSleepBlock && Prefs.sleepGrayscale(this)

        if (app == null) {
            if (isSleepGray) {
                GrayscaleOverlay.show(this, Prefs.grayscaleIntensity(this))
            } else {
                GrayscaleOverlay.hide(this)
            }
            return
        }

        if (app.grayscale || isSleepGray) {
            GrayscaleOverlay.show(this, Prefs.grayscaleIntensity(this))
        } else {
            GrayscaleOverlay.hide(this)
        }

        val now = System.currentTimeMillis()
        val sessionAllowed = !isSleepBlock && Prefs.sessionActive(this, app.pkg)
        if (app.breathGate && !sessionAllowed && now - lastGate > 2500) {
            lastGate = now
            if (isSleepBlock) {
                Prefs.endSession(this, app.pkg)
            }
            launchGate(app.pkg)
        }
    }

    /** Met à jour `fg` (application au premier plan) à partir des événements d'usage récents. */
    private fun updateForeground() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastTs, now + 1000)
        val e = UsageEvents.Event()
        var maxTs = lastTs
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                Usage.EV_RESUMED -> fg = e.packageName
                Usage.EV_PAUSED -> if (fg == e.packageName) fg = null
            }
            if (e.timeStamp > maxTs) maxTs = e.timeStamp
        }
        lastTs = maxTs
    }

    private fun launchGate(pkg: String) {
        // L'affichage par-dessus les autres apps autorise le démarrage d'une activité depuis l'arrière-plan.
        if (!Settings.canDrawOverlays(this)) return
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_GATE, pkg)
        startActivity(i)
    }

    companion object {
        const val CHANNEL = "cosmos_shield"
        const val CHANNEL_SILENT = "cosmos_shield_silent"
        const val ACTION_UPDATE_NOTIF = "UPDATE_NOTIF"
        private const val NOTIF_ID = 1

        fun updateNotification(c: Context) {
            val i = Intent(c, ShieldService::class.java).setAction(ACTION_UPDATE_NOTIF)
            try { c.startService(i) } catch (e: Exception) { }
        }

        /** Démarre ou arrête le service selon les permissions et la configuration. */
        fun sync(c: Context) {
            val i = Intent(c, ShieldService::class.java)
            val should = Usage.hasAccess(c) && Prefs.loadConfig(c).isNotEmpty()
            if (should) {
                try { c.startForegroundService(i) } catch (e: Exception) { /* app en arrière-plan : réessai au prochain affichage */ }
            } else {
                c.stopService(i)
                GrayscaleOverlay.hide(c)
            }
        }
    }
}
