package com.tungsten.fcl.sc

import android.content.Context
import com.tungsten.fclcore.util.Logging
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.logging.Level

/**
 * SC: anonymous error notices, the same channel as the desktop launcher (sc-launcher/docs/ERRORES.md, «Avisos anónimos
 * de error»; Felix 27-10: «como en PC»). A failure sends GET /launcher/informe.txt?c=SC-xx&v=<app>&p=movil|movilpro&a=android
 * to an empty file, first through directo.servidorcobblemon.es and then the normal route; tools/sc-stats.py counts
 * distinct players per code without keeping IPs, and the watchdog DMs Felix when one code hits 5 players in an hour.
 * Nothing personal travels: a fixed code, the app version, the profile and, when useful, an exception class name.
 * Ajustes › «Enviar errores anónimos» turns it off (on by default, like the desktop launcher); the same switch decides
 * -Dsc.avisos for the game, so sc-avisos-cliente sends its own notices (textures, pack, crashes) too.
 */
object ScAvisos {
    // Phone codes (ERRORES.md): SC-40 install/update of the modpack failed, SC-41 a download stopped (0 B/s for 3 min),
    // SC-42 «Reparar archivos» failed, SC-43 unexpected error preparing the game, SC-44 no space on the phone
    const val INSTALAR = "SC-40"
    const val DESCARGA_PARADA = "SC-41"
    const val REPARAR = "SC-42"
    const val INESPERADO = "SC-43"
    const val SIN_ESPACIO = "SC-44"

    private const val PREFS = "sc"
    private const val KEY_ENVIAR = "enviar_errores"
    private val HOSTS = listOf("https://directo.servidorcobblemon.es", "https://descargas.servidorcobblemon.es")
    private val SANO = Regex("^[A-Za-z0-9._-]{1,60}$")
    private val enviados = ConcurrentHashMap<String, Long>()
    private val fondo = Executors.newSingleThreadExecutor { r -> Thread(r, "SC Avisos").apply { isDaemon = true } }

    fun activo(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENVIAR, true)

    fun setActivo(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENVIAR, on).apply()

    /** Fire and forget; one notice per code and minute, never blocks the caller. extra: one-letter keys only. */
    fun enviar(context: Context, codigo: String, extra: Map<String, String> = emptyMap()) {
        if (!activo(context)) return
        val ahora = System.currentTimeMillis()
        val antes = enviados[codigo]
        if (antes != null && ahora - antes < 60_000) return
        enviados[codigo] = ahora
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
        val perfil = if (ScPack.profile(context) == ScPack.PROFILE_PRO) "movilpro" else "movil"
        val q = linkedMapOf("c" to codigo, "v" to version, "p" to perfil, "r" to "normal", "a" to "android")
        for ((k, v) in extra) if (Regex("^[a-z]$").matches(k) && k !in q && SANO.matches(v)) q[k] = v
        val query = q.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        fondo.execute {
            for (host in HOSTS) {
                val ok = runCatching {
                    val c = URL("$host/launcher/informe.txt?$query").openConnection() as HttpURLConnection
                    c.connectTimeout = 5000
                    c.readTimeout = 5000
                    c.setRequestProperty("User-Agent", "SC-Launcher-Movil/$version")
                    try { c.responseCode == 200 } finally { c.disconnect() }
                }.getOrDefault(false)
                if (ok) break
            }
            Logging.LOG.log(Level.INFO, "SC: anonymous notice $codigo sent")
        }
    }

    /** A class name is the only detail of an exception that travels (its message may carry paths). */
    fun clase(e: Throwable): String = e.javaClass.simpleName.takeIf { SANO.matches(it) } ?: "Otro"
}
