package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tungsten.fcl.control.GameMenu
import com.tungsten.fclauncher.utils.FCLPath
import java.io.File

/**
 * SC: the in-game menu in our design (Codex, approved by Felix 27-10) instead of FCL's two drawers. The floating
 * button (FCL's MenuView, with our lens icon) opens a page (assets/sc_ui/menu-juego.html) over the game: Controles,
 * Cámara, Imagen and Ayuda, each option applied with FCL's own handler through GameMenu.scAccion, so nothing here is a
 * new setting. «Opciones avanzadas» still opens FCL's full menu.
 */
object ScMenuJuego {
    const val ACTIVO = true
    private const val URL = "file:///android_asset/sc_ui/menu-juego.html"
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun abrir(activity: Activity, menu: GameMenu) {
        webView?.let { it.visibility = android.view.View.VISIBLE; it.bringToFront(); it.evaluateJavascript("window.scMenu && scMenu.refrescar()", null); return }
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.TRANSPARENT)
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
        wv.addJavascriptInterface(Puente(activity, menu), "SCM")
        activity.findViewById<ViewGroup>(android.R.id.content).addView(
            wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        wv.elevation = 2000f
        wv.bringToFront()
        wv.loadUrl(URL)
        webView = wv
    }

    @JvmStatic
    fun cerrar() {
        webView?.post { webView?.visibility = android.view.View.GONE }
    }

    private class Puente(private val activity: Activity, private val menu: GameMenu) {
        private fun enUi(f: () -> Unit) = activity.runOnUiThread { runCatching(f) }

        @JavascriptInterface
        fun estado(): String {
            var r = "{}"
            val latch = java.util.concurrent.CountDownLatch(1)
            activity.runOnUiThread { r = runCatching { menu.scEstado() }.getOrDefault("{}"); latch.countDown() }
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            return r
        }

        @JavascriptInterface
        fun accion(clase: String, tipo: String, tag: String, valor: String) = enUi { menu.scAccion(clase, tipo, tag, valor) }

        @JavascriptInterface
        fun cerrar() = ScMenuJuego.cerrar()

        @JavascriptInterface
        fun editar() = enUi { ScMenuJuego.cerrar(); menu.scAccion("izq", "switch", "EDIT_MODE", "1") }

        @JavascriptInterface
        fun avanzadas() = enUi { ScMenuJuego.cerrar(); menu.scAbrirAvanzadas() }

        /** The server's default layout again; FCL loads layouts at start, so it applies on the next game. */
        @JavascriptInterface
        fun restaurar(): Boolean = runCatching {
            val f = File(FCLPath.CONTROLLER_DIR, "00000000.json")
            val bundled = ScMenuJuego::class.java.getResourceAsStream("/assets/controllers/00000000.json")!!.bufferedReader().use { it.readText() }
            if (f.isFile) f.copyTo(File(f.parentFile, "00000000.json.anterior"), overwrite = true)
            val tmp = File(f.parentFile, "00000000.json.sc-tmp")
            tmp.writeText(bundled)
            tmp.renameTo(f)
        }.getOrDefault(false)

        @JavascriptInterface
        fun salir() = enUi { android.os.Process.killProcess(android.os.Process.myPid()) }
    }
}
