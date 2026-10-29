package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * SC: our «Minecraft is starting» screen (Codex design, approved by Felix) instead of FCL's theme background and blue
 * spinner. It covers the game activity from the moment it opens until Minecraft draws its first frame
 * (GameMenu.onGraphicOutput), so the touch buttons are not shown over an empty screen. Nothing is measurable in this
 * phase, so the page only shows the real elapsed time and rotating tips (assets/sc_ui/arranque.html).
 */
object ScArranque {
    const val ACTIVO = true
    private const val URL = "file:///android_asset/sc_ui/arranque.html"
    private var webView: WebView? = null
    // slow start measured on the phone (Felix 28-10: «1500 segundos» on a Redmi 13C): FCL's preparation (ScCarga) and
    // the time from the game activity to Minecraft's first frame, sent as SC-47 when either is long
    private var inicio = 0L
    private var contexto: android.content.Context? = null
    @Volatile @JvmStatic var segundosPreparacion = 0L
    private const val LENTO_S = 120

    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun mostrar(activity: Activity) {
        // a game that died before its first frame leaves the old activity's page behind: start over
        if (webView?.context === activity) return
        webView = null
        inicio = System.currentTimeMillis()
        contexto = activity.applicationContext
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.parseColor("#6E1B2C"))
        wv.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        // swallows touches so nothing underneath reacts before the game exists
        wv.setOnTouchListener { _, _ -> true }
        activity.findViewById<ViewGroup>(android.R.id.content).addView(
            wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        wv.elevation = 3000f
        wv.bringToFront()
        wv.loadUrl(URL)
        webView = wv
    }

    /** First game frame (UI thread); also called again when the surface is recreated, so it must be idempotent. */
    @JvmStatic
    fun quitar() {
        val wv = webView ?: return
        webView = null
        avisarSiLento()
        wv.animate().alpha(0f).setDuration(250).withEndAction {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }.start()
    }

    private fun avisarSiLento() {
        val c = contexto ?: return
        val arranque = (System.currentTimeMillis() - inicio) / 1000
        val prep = segundosPreparacion
        segundosPreparacion = 0
        if (inicio == 0L || (arranque < LENTO_S && prep < LENTO_S)) return
        val modelo = android.os.Build.MODEL.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        // minutes (capped at 60) keep the values few enough for sc-stats' per-value counts
        ScAvisos.enviar(c, ScAvisos.ARRANQUE_LENTO, mapOf(
            "t" to (arranque / 60).coerceAtMost(60).toString(), "u" to (prep / 60).coerceAtMost(60).toString(),
            "m" to modelo, "g" to runCatching { ScAuto.gama(c) }.getOrDefault("x"),
            "y" to (com.tungsten.fclcore.util.platform.MemoryUtils.getTotalDeviceMemory(c) / 1024).toString()))
    }
}
