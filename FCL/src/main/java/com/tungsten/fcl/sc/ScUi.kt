package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tungsten.fcl.activity.MainActivity

/**
 * SC: our launcher screens (assets/sc_ui, same PokéNav design as the desktop launcher) shown as a full-screen layer
 * on top of FCL's MainActivity. FCL's own UI stays underneath, intact, and is reached through
 * «Ajustes → Opciones avanzadas»; going back from FCL's home screen brings our layer back.
 * Keeping MainActivity alive underneath matters: much of FCL calls MainActivity.getInstance().
 */
object ScUi {
    private const val START_URL = "file:///android_asset/sc_ui/index.html"

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun attach(activity: MainActivity) {
        // Debug builds only: lets the CI emulator test drive and inspect the page (DevTools protocol over adb)
        if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.parseColor("#B22839"))
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
        }
        wv.webViewClient = object : WebViewClient() {
            // The layer only ever shows our own bundled page; links go through SC.abrir() to the browser.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith("file:///android_asset/sc_ui/")
        }
        wv.addJavascriptInterface(ScBridge(activity, wv), "SC")
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        (activity.binding.root as ViewGroup).addView(
            wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        // FCL's menus and start button have elevation; ours must sit above every one of them
        wv.elevation = 1000f
        wv.translationZ = 1000f
        wv.bringToFront()
        wv.loadUrl(START_URL)
        webView = wv
    }

    @JvmStatic
    fun isShowing(): Boolean = webView?.visibility == View.VISIBLE

    @JvmStatic
    fun show() {
        webView?.apply {
            visibility = View.VISIBLE
            bringToFront()
            evaluateJavascript("window.scApp && scApp.refrescar()", null)
        }
    }

    @JvmStatic
    fun hide() {
        webView?.visibility = View.GONE
    }

    /** Runs JS in our page (main thread). */
    @JvmStatic
    fun js(code: String) {
        webView?.post { webView?.evaluateJavascript(code, null) }
    }

    /**
     * Back button. Returns true when handled: inside our screens the page decides (closes dialogs, returns to Jugar);
     * from FCL's home screen it brings our layer back instead of leaving the app.
     */
    @JvmStatic
    fun onBack(activity: MainActivity, onPageDidNotHandle: () -> Unit): Boolean {
        val wv = webView ?: return false
        if (isShowing()) {
            wv.evaluateJavascript("window.scApp ? scApp.atras() : false") { handled ->
                if (handled != "true") onPageDidNotHandle()
            }
            return true
        }
        if (activity.scIsOnHome()) {
            show()
            return true
        }
        return false
    }

    @JvmStatic
    fun detach() {
        ScMicrosoft.cancel()
        webView?.destroy()
        webView = null
    }
}
