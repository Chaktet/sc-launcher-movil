package com.tungsten.fcl.sc

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.tungsten.fclauncher.bridge.FCLBridge
import com.tungsten.fclcore.util.platform.MemoryUtils
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * SC: what the app corrects by itself between games (Felix 27-10: «detección de errores automáticos y correcciones
 * automáticas»). Each game leaves a note here (how it closed, how it ran) and the next JUGAR acts on it:
 *  - closed by a broken mod → the files are repaired before playing (ScPack);
 *  - closed by the graphics → another renderer is tried (ScPack.selectAndConfigure);
 *  - two closes in a row → the error screen offers «modo seguro» (basic graphics, minimum view distance);
 *  - stuttering (low FPS) or a hot phone → the view distance goes down one step and the player is told;
 *  - the first install picks the profile and the view distance from the phone's memory.
 * Everything lives in the «sc» preferences; options.txt is only changed through [opcion].
 */
object ScAuto {
    private const val PREFS = "sc"
    private const val K_REPARAR = "auto_reparar"
    private const val K_RENDER = "auto_render"
    private const val K_SEGUIDOS = "cierres_seguidos"
    private const val K_SEGURO = "modo_seguro"
    private const val K_LENTO = "auto_lento"
    private const val K_NOTA = "auto_nota"
    private const val K_INICIAL = "auto_inicial_hecho"
    private const val K_ELEGIDO = "perfil_elegido"
    private const val K_SIN_MEMORIA = "pack_sin_memoria"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- how the last game closed (ScCierre, GameMenu.onExit) ----

    @JvmStatic
    fun cierreConError(c: Context, causa: String) {
        val e = p(c).edit()
        if (causa == "mods" || causa == "mixin") e.putBoolean(K_REPARAR, true)
        if (causa == "graficos" || causa == "nativo") e.putBoolean(K_RENDER, true)
        e.putInt(K_SEGUIDOS, p(c).getInt(K_SEGUIDOS, 0) + 1).commit()
    }

    @JvmStatic
    fun cierreNormal(c: Context) { p(c).edit().putInt(K_SEGUIDOS, 0).commit() }

    fun cierresSeguidos(c: Context) = p(c).getInt(K_SEGUIDOS, 0)

    fun consumirReparar(c: Context): Boolean = p(c).getBoolean(K_REPARAR, false).also { if (it) p(c).edit().remove(K_REPARAR).apply() }
    fun consumirRender(c: Context): Boolean = p(c).getBoolean(K_RENDER, false).also { if (it) p(c).edit().remove(K_RENDER).apply() }

    fun pedirModoSeguro(c: Context) { p(c).edit().putBoolean(K_SEGURO, true).apply() }
    fun consumirModoSeguro(c: Context): Boolean = p(c).getBoolean(K_SEGURO, false).also { if (it) p(c).edit().remove(K_SEGURO).apply() }

    /** A line for the home page about what was corrected («» when nothing), shown once. */
    fun nota(c: Context): String = p(c).getString(K_NOTA, null).orEmpty().also { if (it.isNotEmpty()) p(c).edit().remove(K_NOTA).apply() }
    fun ponerNota(c: Context, texto: String) { p(c).edit().putString(K_NOTA, texto).apply() }

    // ---- first install: profile and view distance from the phone's memory ----

    /** Ajustes: the player picked a profile, so it is never changed for him again. */
    fun perfilElegido(c: Context) { p(c).edit().putBoolean(K_ELEGIDO, true).apply() }

    /**
     * Before each install check: a phone that cannot give the game the memory the Móvil pack needs (or whose last game
     * dropped the pack out of memory) plays Móvil Lite, unless the player chose a profile in Ajustes.
     */
    fun perfilPorMemoria(c: Context) {
        if (p(c).getBoolean(K_ELEGIDO, false) || ScPack.profile(c) != ScPack.PROFILE_LIGHT) return
        if (ScMemoria.cabeMovil(c) && !p(c).getBoolean(K_SIN_MEMORIA, false)) return
        ScPack.setProfile(c, ScPack.PROFILE_LITE)
        p(c).edit().remove(K_SIN_MEMORIA).apply()
        ponerNota(c, "Tu móvil tiene poca memoria: usamos el modpack Móvil Lite (sin skins de Pokémon) para que veas todas " +
            "las texturas del servidor. Puedes cambiarlo en Ajustes.")
    }

    /** ScMemoria: the last game dropped the pack because it ran out of memory. */
    fun sinMemoria(c: Context) { p(c).edit().putBoolean(K_SIN_MEMORIA, true).apply() }

    /** Before the first install, when the player never chose a profile: strong phones get PRO. */
    fun perfilInicial(c: Context) {
        if (p(c).getBoolean(K_INICIAL, false) || ScPack.installedVersion(c) != null || p(c).contains("perfil")) return
        val total = MemoryUtils.getTotalDeviceMemory(c)
        ScPack.setProfile(c, if (total >= 11 * 1024) ScPack.PROFILE_PRO else ScPack.PROFILE_LIGHT)
    }

    /** Right after the first install: a view distance this phone can carry (the pack ships 3). */
    fun ajustesIniciales(c: Context, opciones: File) {
        if (p(c).getBoolean(K_INICIAL, false)) return
        val total = MemoryUtils.getTotalDeviceMemory(c)
        val distancia = when { total >= 11 * 1024 -> 6; total >= 7 * 1024 -> 5; total >= 5 * 1024 -> 4; else -> 3 }
        opcion(opciones, "renderDistance", distancia.toString())
        p(c).edit().putBoolean(K_INICIAL, true).apply()
    }

    // ---- before each game: performance and safe mode ----

    /** Applies what the last game asked for (stutter or heat → shorter view; safe mode → minimum settings). */
    fun antesDeJugar(c: Context, opciones: File) {
        if (consumirModoSeguro(c)) {
            opcion(opciones, "renderDistance", "2"); opcion(opciones, "simulationDistance", "5")
            opcion(opciones, "graphicsMode", "0"); opcion(opciones, "particles", "2"); opcion(opciones, "entityShadows", "false")
            ponerNota(c, "Modo seguro: gráficos básicos y distancia mínima. Puedes subirlos en las opciones del juego.")
            return
        }
        val lento = p(c).getString(K_LENTO, null) ?: return
        p(c).edit().remove(K_LENTO).apply()
        val actual = leer(opciones, "renderDistance")?.toIntOrNull() ?: return
        if (actual <= 2) return
        opcion(opciones, "renderDistance", (actual - 1).toString())
        ponerNota(c, (if (lento == "calor") "La última vez el móvil se calentó" else "La última vez el juego iba a tirones") +
            ": hemos bajado la distancia de visión a ${actual - 1}.")
    }

    /**
     * While playing: every 5 s the FPS (FCL's counter) and, from Android 10, the phone's thermal state. After two
     * minutes of warm-up, a game that averages under 20 FPS for two minutes, or a phone that reaches «severe» heat,
     * leaves a note for the next JUGAR. Written as it happens, so a crash does not lose it.
     */
    @JvmStatic
    fun vigilarRendimiento(activity: Activity) {
        val c = activity.applicationContext
        Thread({
            runCatching {
                Thread.sleep(120_000)
                val muestras = ArrayDeque<Int>()
                val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
                while (!activity.isFinishing) {
                    Thread.sleep(5_000)
                    if (Build.VERSION.SDK_INT >= 29 && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                        p(c).edit().putString(K_LENTO, "calor").apply(); return@Thread
                    }
                    val fps = FCLBridge.getFps()
                    if (fps <= 0) continue
                    muestras.addLast(fps); if (muestras.size > 24) muestras.removeFirst()
                    if (muestras.size == 24 && muestras.average() < 20) { p(c).edit().putString(K_LENTO, "fps").apply(); return@Thread }
                }
            }
        }, "SC rendimiento").apply { isDaemon = true }.start()
    }

    // ---- options.txt ----

    fun leer(f: File, clave: String): String? =
        runCatching { f.readLines(StandardCharsets.UTF_8).firstOrNull { it.startsWith("$clave:") }?.substringAfter(':') }.getOrNull()

    fun opcion(f: File, clave: String, valor: String) {
        if (!f.isFile) return
        val lineas = f.readLines(StandardCharsets.UTF_8).toMutableList()
        val i = lineas.indexOfFirst { it.startsWith("$clave:") }
        if (i >= 0) lineas[i] = "$clave:$valor" else lineas.add("$clave:$valor")
        f.writeText(lineas.joinToString("\n", postfix = "\n"), StandardCharsets.UTF_8)
    }
}
