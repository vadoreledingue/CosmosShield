package com.cosmos.shield

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLConnection

class MainActivity : Activity() {
    companion object {
        const val EXTRA_GATE = "gate_pkg"
        const val EXTRA_OPEN = "open"
        private const val REQ_NOTIF = 7
        private const val HOST = "appassets.androidplatform.net"
    }

    private lateinit var web: WebView
    private lateinit var bridge: NativeBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = NativeBridge(this)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isLongClickable = false
            setOnLongClickListener { true }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true       // localStorage : sauvegarde des données de l'app
                allowFileAccess = false
                allowContentAccess = false
                textZoom = 100
                setSupportZoom(false)
            }
            webViewClient = AssetClient()
            webChromeClient = DialogChromeClient()
            addJavascriptInterface(bridge, "AndroidBridge")
        }
        setContentView(web)

        if (savedInstanceState == null) readIntent(intent)
        web.loadUrl("https://$HOST/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(i: Intent?) {
        if (i == null) return
        i.getStringExtra(EXTRA_GATE)?.let { bridge.pendingGate = it; i.removeExtra(EXTRA_GATE) }
        i.getStringExtra(EXTRA_OPEN)?.let { bridge.pendingOpen = it; i.removeExtra(EXTRA_OPEN) }
    }

    override fun onResume() {
        super.onResume()
        Reminders.scheduleAll(this)
        ShieldService.sync(this)
        web.evaluateJavascript("window.__onNativeResume && window.__onNativeResume()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(function(){return window.__onBack ? window.__onBack() : false})()") { r ->
            if (r != "true") moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Appelé par le pont (thread UI). */
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !Prefs.notifAsked(this)
        ) {
            Prefs.setNotifAsked(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            Reminders.scheduleAll(this)
            web.evaluateJavascript("window.__onNativeResume && window.__onNativeResume()", null)
        }
    }

    /** Sert les fichiers de assets/www sous https://appassets.androidplatform.net/ (origine stable → localStorage persistant). */
    private inner class AssetClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host != HOST) return empty(403, "Forbidden")   // aucun accès réseau externe
            var path = (url.path ?: "/").removePrefix("/")
            if (path.isEmpty()) path = "index.html"
            return try {
                val stream = assets.open("www/$path")
                val mime = mimeOf(path)
                WebResourceResponse(mime, if (mime.startsWith("text/") || mime.endsWith("javascript")) "utf-8" else null, stream)
            } catch (e: IOException) {
                empty(404, "Not Found")
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.host != HOST

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recreate()
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
        }

        private fun empty(code: Int, reason: String) =
            WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))

        private fun mimeOf(path: String): String = when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".svg") -> "image/svg+xml"
            else -> URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream"
        }
    }

    /** Sans cela, alert() et confirm() sont ignorés par la WebView. */
    private inner class DialogChromeClient : WebChromeClient() {
        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.confirm() }
                .show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }
    }
}
