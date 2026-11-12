package com.tungsten.fcl.sc

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tungsten.fclcore.util.Logging
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * SC: our «Minecraft is starting» screen (Codex design, approved by Felix) instead of FCL's theme background and blue
 * spinner. It covers the game activity from the moment it opens until Minecraft draws its first frame
 * (GameMenu.onGraphicOutput), so the touch buttons are not shown over an empty screen.
 *
 * Since 0.1.25 (docs/PLAN-ARRANQUE-RAPIDO.md, phases 0 and 2a) it also follows the game's own output (GameMenu.onLog,
 * the same lines that go to latest_game.log) and stamps each start phase with a monotonic clock: the page shows the
 * real stage and, when this phone has a previous start of the same profile and pack, the time left; SC-47 carries the
 * seconds of each phase; every start writes one «SC arranque:» line to the app log (support zip), fast ones too.
 */
object ScArranque {
    const val ACTIVO = true
    private const val URL = "file:///android_asset/sc_ui/arranque.html"
    private var webView: WebView? = null
    // slow start measured on the phone (Felix 28-10: «1500 segundos» on a Redmi 13C): FCL's preparation (ScCarga) and
    // the time from the game activity to Minecraft's first frame, sent as SC-47 when either is long
    private var inicio = 0L
    private var contexto: Context? = null
    @Volatile @JvmStatic var segundosPreparacion = 0L
    // when this game drew its first frame (0 = not yet): ScAuto only starts measuring FPS and heat after it, because
    // loading takes 2-13 min on phones and its 0 FPS / heat are not the game's performance (08-11)
    @Volatile @JvmStatic var primerFotograma = 0L
    private const val LENTO_S = 120
    // after the first frame Minecraft still runs its resource reload; without ModernFix's «Game took» line the notice
    // goes out this long after the first frame
    private const val ESPERA_MENU_MS = 10 * 60_000L

    // ---- per-phase timeline (phase 0). Keys are SC-47 extras: free letters that sc-stats counts (CAMPOS_PISTA in
    // sc-launcher/tools/sc-stats.py) and that ScAvisos does not reserve (c v p r a), apart from t u m g y already used.
    // l «Loading N mods» · s the screens check's own duration · k first resource reload · d first frame
    // h menu ready (ModernFix) · e Showdown started · n resource reloads up to h · f first start (Fabric remaps jars)
    private val marcas = ConcurrentHashMap<String, Long>() // key -> ms since the game activity opened
    @Volatile private var inicioMono = 0L
    @Volatile private var segundosPantallas = -1.0
    @Volatile private var recargas = 0
    @Volatile private var primeraVez = false
    @Volatile private var mods = 0
    @Volatile private var siguiendo = false
    @Volatile private var enviado = false
    private var segundosHastaFotograma = 0L
    private var segundosPrep = 0L
    private var claveHistorial = ""
    // read once on the UI thread before the game starts: gama() may initialise EGL the first time, never from the log thread
    private var gama = "x"
    private val main = Handler(Looper.getMainLooper())
    private val RE_MODS = Regex("Loading (\\d+) mods")
    private val RE_PANTALLAS = Regex("\\[SC Menus] screens .*checked in ([0-9.]+) s")
    private const val PREFS = "sc_arranque"
    // a slow start's SC-47, kept until it is sent: the game can close before the menu or the 10-min fallback
    private const val K_PENDIENTE = "sc47_pendiente"
    // stdout arrives in chunks that can cut a line in two: markers are looked for in whole lines only (Codex 12-11)
    private val resto = StringBuilder()

    @SuppressLint("SetJavaScriptEnabled")
    @JvmStatic
    fun mostrar(activity: Activity) {
        // a game that died before its first frame leaves the old activity's page behind: start over
        if (webView?.context === activity) return
        webView = null
        primerFotograma = 0L
        inicio = System.currentTimeMillis()
        inicioMono = SystemClock.elapsedRealtime()
        contexto = activity.applicationContext
        marcas.clear()
        segundosPantallas = -1.0
        recargas = 0
        primeraVez = false
        mods = 0
        enviado = false
        synchronized(resto) { resto.setLength(0) }
        enviarPendiente(activity.applicationContext)
        segundosPrep = segundosPreparacion
        segundosPreparacion = 0
        claveHistorial = runCatching {
            "h_" + ScPack.installedProfile(activity) + "_" + (ScPack.installedVersion(activity) ?: "x")
        }.getOrDefault("h_x")
        gama = runCatching { ScAuto.gama(activity) }.getOrDefault("x")
        siguiendo = true
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
            override fun onPageFinished(view: WebView, url: String?) = pintar()
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

    /**
     * Every chunk the game prints (GameMenu.onLog, the JVM's output thread). Only a few contains() per chunk, and
     * nothing at all once the menu is ready: this runs while the phone is busiest.
     */
    @JvmStatic
    fun linea(log: String) {
        if (!siguiendo) return
        val ms = SystemClock.elapsedRealtime() - inicioMono
        val lineas: List<String>
        synchronized(resto) {
            resto.append(log)
            val fin = resto.lastIndexOf("\n")
            if (fin < 0) {
                if (resto.length > 16_384) resto.setLength(0) // no newline for 16 KB: not a log line worth keeping
                return
            }
            lineas = resto.substring(0, fin).split('\n')
            resto.delete(0, fin + 1)
        }
        for (l in lineas) if (siguiendo) unaLinea(l, ms)
    }

    private fun unaLinea(log: String, ms: Long) {
        var cambio = false
        if (!primeraVez && log.contains("preparing JARs on first launch")) { primeraVez = true; cambio = true }
        if (!marcas.containsKey("l") && log.contains(" mods")) RE_MODS.find(log)?.let {
            mods = it.groupValues[1].toIntOrNull() ?: 0
            marcas["l"] = ms; cambio = true
        }
        if (!marcas.containsKey("s") && log.contains("[SC Menus] screens")) {
            marcas["s"] = ms
            segundosPantallas = RE_PANTALLAS.find(log)?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
            cambio = true
        }
        if (log.contains("Reloading ResourceManager")) {
            recargas++
            if (marcas.putIfAbsent("k", ms) == null) cambio = true
        }
        if (!marcas.containsKey("e") && log.contains("Showdown has been started")) { marcas["e"] = ms; cambio = true }
        if (log.contains("seconds to start") && log.contains("Game took")) {
            marcas["h"] = ms
            siguiendo = false
            registrar("menu")
            main.post { enviarSiLento() }
            return
        }
        if (cambio) {
            registrar("tramo")
            main.post { pintar() }
        }
    }

    /** First game frame (UI thread); also called again when the surface is recreated, so it must be idempotent. */
    @JvmStatic
    fun quitar() {
        if (primerFotograma == 0L) {
            primerFotograma = System.currentTimeMillis()
            marcas.putIfAbsent("d", SystemClock.elapsedRealtime() - inicioMono)
            segundosHastaFotograma = (primerFotograma - inicio) / 1000
            guardarHistorial()
            registrar("fotograma")
            guardarPendiente()
            // the menu is ready later (resource reload under Minecraft's own loading bar): ModernFix's «Game took» line
            // sends SC-47 then; this is the fallback when that line never comes
            val esta = inicioMono
            main.postDelayed({ if (inicioMono == esta) { siguiendo = false; enviarSiLento() } }, ESPERA_MENU_MS)
        }
        val wv = webView ?: return
        webView = null
        wv.animate().alpha(0f).setDuration(250).withEndAction {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }.start()
    }

    // ---- phase 0: SC-47 and the app-log line ----

    /** Seconds in steps (5 s up to 1 min, 20 s up to 5 min, 1 min up to 15 min, then 900): < 50 values per key. */
    internal fun tramo(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> (s / 5 * 5).toString()
            s < 300 -> (s / 20 * 20).toString()
            s < 900 -> (s / 60 * 60).toString()
            else -> "900"
        }
    }

    private fun extras(c: Context): Map<String, String> {
        val modelo = android.os.Build.MODEL.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val m = linkedMapOf(
            // minutes (capped at 60) keep the values few enough for sc-stats' per-value counts
            "t" to (segundosHastaFotograma / 60).coerceAtMost(60).toString(),
            "u" to (segundosPrep / 60).coerceAtMost(60).toString(),
            "m" to modelo, "g" to gama,
            "y" to (com.tungsten.fclcore.util.platform.MemoryUtils.getTotalDeviceMemory(c) / 1024).toString())
        for (k in listOf("l", "k", "d", "h", "e")) m[k] = marcas[k]?.let { tramo(it) } ?: "x"
        m["s"] = segundosPantallas.takeIf { it >= 0 }?.let { tramo((it * 1000).toLong()) } ?: "x"
        m["n"] = recargas.coerceAtMost(9).toString()
        m["f"] = if (primeraVez) "1" else "0"
        return m
    }

    /** One line per phase change in the app log (fcl.log, in the support zip): the SC-47 query as it would be sent. */
    private fun registrar(momento: String) {
        val c = contexto ?: return
        runCatching {
            Logging.LOG.log(Level.INFO, "SC arranque: $momento " + ScAvisos.consulta(c, ScAvisos.ARRANQUE_LENTO, extras(c)))
        }
    }

    private fun lento(): Boolean =
        segundosHastaFotograma >= LENTO_S || segundosPrep >= LENTO_S || (marcas["h"] ?: 0L) >= LENTO_S * 1000L

    private fun enviarSiLento() {
        val c = contexto ?: return
        if (enviado || inicio == 0L) return
        enviado = true
        prefs(c).edit().remove(K_PENDIENTE).apply()
        if (!lento()) return
        ScAvisos.enviar(c, ScAvisos.ARRANQUE_LENTO, extras(c))
    }

    /** At the first frame of a start already known to be slow: what would be sent now, in case the game closes first. */
    private fun guardarPendiente() {
        val c = contexto ?: return
        if (!lento()) return
        runCatching { prefs(c).edit().putString(K_PENDIENTE, JSONObject(extras(c) as Map<*, *>).toString()).apply() }
    }

    /** The previous start's SC-47 that never went out (the game closed before the menu): sent on the next start. */
    private fun enviarPendiente(c: Context) {
        val p = prefs(c)
        val txt = p.getString(K_PENDIENTE, null) ?: return
        p.edit().remove(K_PENDIENTE).apply()
        runCatching {
            val o = JSONObject(txt)
            val m = linkedMapOf<String, String>()
            for (k in o.keys()) m[k] = o.getString(k)
            ScAvisos.enviar(c, ScAvisos.ARRANQUE_LENTO, m)
        }
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- phase 2a: the page's stage, progress and time left ----

    private fun historial(c: Context, frio: Boolean): JSONObject? = runCatching {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(claveHistorial + if (frio) "_frio" else "_caliente", null)
            ?.let { JSONObject(it) }?.takeIf { it.optLong("d") > 0 }
    }.getOrNull()

    /** The phases of this start up to its first frame, for the next start of the same profile, pack and kind. */
    private fun guardarHistorial() {
        val c = contexto ?: return
        val d = marcas["d"] ?: return
        runCatching {
            val o = JSONObject()
            for (k in listOf("l", "s", "k")) marcas[k]?.takeIf { it < d }?.let { o.put(k, it) }
            o.put("d", d)
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(claveHistorial + if (primeraVez) "_frio" else "_caliente", o.toString()).apply()
        }
    }

    /** UI thread: hands the page what is known so far; the page animates between calls by itself. */
    private fun pintar() {
        val wv = webView ?: return
        val c = contexto ?: return
        val o = JSONObject()
        o.put("ahora", SystemClock.elapsedRealtime() - inicioMono)
        o.put("mods", mods)
        o.put("primera", primeraVez)
        val m = JSONObject()
        for ((k, v) in marcas) m.put(k, v)
        o.put("marcas", m)
        historial(c, primeraVez)?.let { o.put("hist", it) }
        wv.evaluateJavascript("window.scArranque && window.scArranque($o)", null)
    }
}
