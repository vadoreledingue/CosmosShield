package com.cosmos.shield

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Overlay-based grayscale filter. Draws a semi-transparent gray layer
 * over other apps using SYSTEM_ALERT_WINDOW (no ADB/root needed).
 * The intensity controls how "washed out" the screen appears.
 */
object GrayscaleOverlay {
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun canDraw(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun show(c: Context, intensity: Int) {
        if (!canDraw(c)) return
        val alpha = (intensity * 2.2).toInt().coerceIn(0, 220)
        handler.post {
            try {
                if (overlayView != null) {
                    overlayView?.setBackgroundColor(Color.argb(alpha, 128, 128, 128))
                    return@post
                }
                val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = View(c).apply {
                    setBackgroundColor(Color.argb(alpha, 128, 128, 128))
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                params.alpha = 1f
                wm.addView(view, params)
                overlayView = view
            } catch (e: Exception) { /* overlay permission may have been revoked */ }
        }
    }

    fun hide(c: Context) {
        handler.post {
            val view = overlayView ?: return@post
            try {
                val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (e: Exception) { /* view may not be attached */ }
            overlayView = null
        }
    }
}
