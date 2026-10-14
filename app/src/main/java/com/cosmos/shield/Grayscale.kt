package com.cosmos.shield

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Noir & blanc système (correction des couleurs « monochromie »).
 * Nécessite WRITE_SECURE_SETTINGS, à accorder une fois par ADB :
 *   adb shell pm grant com.cosmos.shield android.permission.WRITE_SECURE_SETTINGS
 */
object Grayscale {
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"

    fun canWrite(c: Context): Boolean =
        c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    private fun isOn(c: Context): Boolean = Settings.Secure.getInt(c.contentResolver, ENABLED, 0) == 1

    fun set(c: Context, on: Boolean) {
        if (!canWrite(c)) return
        try {
            val cr = c.contentResolver
            if (on) {
                if (!isOn(c)) {
                    Settings.Secure.putInt(cr, MODE, 0)
                    Settings.Secure.putInt(cr, ENABLED, 1)
                    Prefs.setGrayByUs(c, true)
                }
            } else if (Prefs.grayByUs(c)) {
                if (isOn(c)) Settings.Secure.putInt(cr, ENABLED, 0)
                Prefs.setGrayByUs(c, false)
            }
        } catch (e: SecurityException) { /* permission retirée */ }
    }

    /** Sécurité : remet les couleurs normales quoi qu'il arrive. */
    fun forceOff(c: Context) {
        if (!canWrite(c)) return
        try {
            Settings.Secure.putInt(c.contentResolver, ENABLED, 0)
            Prefs.setGrayByUs(c, false)
        } catch (e: SecurityException) { }
    }
}
