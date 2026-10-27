package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tungsten.fcl.activity.SplashActivity
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * SC: the first start in our design (Codex, approved by Felix 27-10) instead of FCL's welcome, legal agreement,
 * storage notice and «installing components» list. A page (assets/sc_ui/bienvenida.html) sits over SplashActivity:
 * «Empezar» accepts the rules and the licence, asks Android for storage when needed, and the unpacking of Java and the
 * libraries (RuntimeFragment, kept but hidden) is shown with a real bar: components done out of the total, the file
 * being unpacked and the time left measured on this phone. When everything is ready the app goes on by itself.
 */
object ScBienvenida {
    const val ACTIVO = true
    private const val URL = "file:///android_asset/sc_ui/bienvenida.html"

    private var webView: WebView? = null
    private val hechos = AtomicInteger(0)
    @Volatile private var total = 0
    @Volatile private var alEmpezar: (() -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun mostrar(activity: SplashActivity, empezar: () -> Unit) {
        alEmpezar = empezar
        if (webView != null) return
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.parseColor("#B22839"))
        wv.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            textZoom = 100
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith("file:///android_asset/sc_ui/")
        }
        wv.addJavascriptInterface(Puente(activity), "SCB")
        (activity.binding.root as ViewGroup).addView(
            wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        wv.elevation = 1000f
        wv.bringToFront()
        wv.loadUrl(URL)
        webView = wv
    }

    @JvmStatic
    fun visible(): Boolean = webView != null

    /** The page is waiting for Android's storage permission screen. */
    @JvmStatic
    fun permiso() = js("scBienvenida.permiso()")

    /** RuntimeFragment: how many components it is going to unpack. */
    @JvmStatic
    fun empiezaInstalacion(componentes: Int) {
        total = componentes
        hechos.set(0)
        js("scBienvenida.esperando(); scBienvenida.progreso(0, $componentes, '')")
    }

    @JvmStatic
    fun detalle(texto: String) = js("scBienvenida.progreso(${hechos.get()}, $total, ${JSONObject.quote(texto.take(60))})")

    @JvmStatic
    fun componenteListo() {
        val n = hechos.incrementAndGet()
        js("scBienvenida.progreso($n, $total, '')")
        if (n >= total) js("scBienvenida.listo()")
    }

    @JvmStatic
    fun error(texto: String) = js("scBienvenida.error(${JSONObject.quote(texto.take(160))})")

    /** Leaving SplashActivity for the launcher. */
    @JvmStatic
    fun cerrar() {
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        webView = null
    }

    private fun js(code: String) {
        webView?.post { webView?.evaluateJavascript("window.scBienvenida && $code", null) }
    }

    private class Puente(private val activity: SplashActivity) {
        @JavascriptInterface
        fun empezar() { activity.runOnUiThread { alEmpezar?.invoke() } }

        @JavascriptInterface
        fun version(): String = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrNull().orEmpty()

        @JavascriptInterface
        fun abrir(url: String) {
            val u = runCatching { Uri.parse(url) }.getOrNull() ?: return
            if (u.scheme != "https" || u.host !in setOf("servidorcobblemon.es", "github.com")) return
            activity.runOnUiThread { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, u)) } }
        }
    }
}
