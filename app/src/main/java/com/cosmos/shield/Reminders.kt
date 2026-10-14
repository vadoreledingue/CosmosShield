package com.cosmos.shield

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Rappels quotidiens : couvre-feu à 21h30 et bilan du matin à 8h00. */
object Reminders {
    private const val CHANNEL = "cosmos_reminders"
    const val NIGHT = "night"
    const val MORNING = "morning"

    fun scheduleAll(c: Context) {
        schedule(c, NIGHT)
        schedule(c, MORNING)
    }

    fun schedule(c: Context, type: String) {
        val (h, m) = if (type == NIGHT) Pair(21, 30) else Pair(8, 0)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Alarme inexacte (pas de permission spéciale) : peut dériver de quelques minutes en mode Doze.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, alarmIntent(c, type))
    }

    private fun alarmIntent(c: Context, type: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, if (type == NIGHT) 100 else 101,
            Intent(c, ReminderReceiver::class.java).putExtra("type", type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun show(c: Context, type: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels", NotificationManager.IMPORTANCE_HIGH))
        if (!nm.areNotificationsEnabled()) return

        val open = Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (type == MORNING) open.putExtra(MainActivity.EXTRA_OPEN, "morning")
        val pi = PendingIntent.getActivity(
            c, if (type == NIGHT) 200 else 201, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (type == NIGHT) "🌙 21h30 — Couvre-feu digital" else "☀️ Bilan du matin"
        val text = if (type == NIGHT) "Pose ton téléphone, passe en mode nuit et prends ton livre avant de dormir."
                   else "Note ton sommeil et valide ta journée d'hier."
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cosmos)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(if (type == NIGHT) 20 else 21, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val type = i.getStringExtra("type") ?: return
        Reminders.show(c, type)
        Reminders.schedule(c, type) // reprogramme le lendemain
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminders.scheduleAll(c)
        ShieldService.sync(c)
    }
}
