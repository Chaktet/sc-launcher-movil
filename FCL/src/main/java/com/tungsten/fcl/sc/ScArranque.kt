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

    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun mostrar(activity: Activity) {
        // a game that died before its first frame leaves the old activity's page behind: start over
        if (webView?.context === activity) return
        webView = null
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
        wv.animate().alpha(0f).setDuration(250).withEndAction {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }.start()
    }
}
