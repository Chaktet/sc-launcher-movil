package com.tungsten.fcl.sc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.tungsten.fcl.activity.SplashActivity
import org.json.JSONObject
import java.io.File

/**
 * SC: the game closed with an error. FCL kills the app's process and showed its own grey crash screen; instead the
 * cause is saved, the app opens again (ScReabrir, in the ":crash" process so it survives the kill) and our home page
 * shows the error screen designed by Codex (approved by Felix 27-10): the cause in plain Spanish, read from the game
 * log, «Volver a jugar», «Enviar informe» (anonymous SC-45) and «Ver detalles» (the last lines of the log).
 */
object ScCierre {
    private const val PREFS = "sc"
    private const val KEY = "cierre_pendiente"

    /** Called by GameMenu.onExit (game thread) right before the process is killed. */
    @JvmStatic
    fun alSalir(activity: Activity, exitCode: Int, logPath: String?) {
        val log = runCatching { logPath?.let { File(it) }?.takeIf { it.isFile }?.let { leerCola(it) } }.getOrNull().orEmpty()
        val (causa, texto) = causa(log, exitCode)
        val j = JSONObject().put("codigo", exitCode).put("causa", causa).put("texto", texto)
            .put("detalles", log.lines().filter { it.isNotBlank() }.takeLast(14).joinToString("\n"))
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, j.toString()).commit()
        activity.startActivity(Intent(activity, ScReabrir::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    /** For the page at start: the pending error as JSON, once ("" when there is none). */
    fun pendiente(c: Context): String {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val s = p.getString(KEY, null) ?: return ""
        p.edit().remove(KEY).apply()
        return s
    }

    private fun leerCola(f: File): String {
        val max = 64 * 1024L
        f.inputStream().use { inp ->
            if (f.length() > max) inp.skip(f.length() - max)
            return inp.readBytes().toString(Charsets.UTF_8)
        }
    }

    /** A short key (travels in the anonymous notice) and the line the player reads. */
    private fun causa(log: String, exitCode: Int): Pair<String, String> = when {
        Regex("OutOfMemoryError|Java heap space|GC overhead limit|lowmemorykiller|Out of memory").containsMatchIn(log) ->
            "memoria" to "Tu móvil se quedó sin memoria. Cierra otras apps o prueba el modpack Móvil (ligero) en Ajustes."
        Regex("Incompatible mods found|Mod resolution failed|requires .* which is missing|Mod discovery failed").containsMatchIn(log) ->
            "mods" to "Un mod del modpack no arrancó. Pulsa «Reparar archivos» en el inicio y vuelve a intentarlo."
        Regex("(?i)GLFW error|EGL_|eglMakeCurrent|GL_OUT_OF_MEMORY|OpenGL|Failed to create (the )?(window|context)|libGL|MobileGlues|vulkan").containsMatchIn(log) ->
            "graficos" to "La tarjeta gráfica del móvil dio un error. Prueba otro renderizador en Ajustes."
        Regex("Mixin apply failed|MixinApplyError|InvalidInjectionException").containsMatchIn(log) ->
            "mixin" to "Un mod falló al cargar. Pulsa «Reparar archivos» en el inicio y vuelve a intentarlo."
        Regex("SIGSEGV|SIGABRT|Fatal signal").containsMatchIn(log) ->
            "nativo" to "El juego se cerró por un fallo del sistema del móvil. Vuelve a intentarlo; si se repite, prueba otro renderizador en Ajustes."
        else -> "otro" to "El juego se cerró con un error (código $exitCode). Vuelve a intentarlo; si se repite, envíanos el informe."
    }
}

/** Lives in the ":crash" process (survives the kill of the game's process) and opens the app again. */
class ScReabrir : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }, 600)
    }
}
