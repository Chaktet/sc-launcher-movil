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
 *  - stuttering (low FPS) or a hot phone → one step down the performance ladder ([antesDeJugar]), smooth games one up;
 *  - weak phones (memory, cores, GPU: [gama]) start a few steps down;
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
    private const val K_NIVEL = "rend_nivel"
    private const val K_BUENO = "rend_bueno"
    private const val K_BUENAS = "rend_buenas"
    private const val K_GAMA_HECHA = "rend_gama_hecha"
    private const val K_ESCALA_ORIGINAL = "rend_escala_original"
    private const val K_GPU = "gpu"

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

    // ---- before each game: performance ladder and safe mode ----

    /**
     * Felix 28-10, «escalera automática»: a performance level 0..5 that each laggy game (under 20 FPS for two minutes,
     * or a hot phone) moves one step down and three smooth games in a row (over 45 FPS for 10 minutes) one step up.
     *  1 fast graphics, no clouds or shadows, fewer particles, no biome blend
     *  2 + view distance at most 4, simulation 5, no mipmaps
     *  3 + game resolution 75 % (FCL's window scale: the biggest FPS gain on a phone)
     *  4 + view distance 2, minimal particles, resolution 60 %
     *  5 + the Móvil Lite modpack (when on Móvil and the player did not pick a profile himself)
     * The first JUGAR after this version starts at the level of the phone's class ([gama]).
     */
    fun antesDeJugar(c: Context, opciones: File) {
        if (consumirModoSeguro(c)) {
            opcion(opciones, "renderDistance", "2"); opcion(opciones, "simulationDistance", "5")
            opcion(opciones, "graphicsMode", "0"); opcion(opciones, "particles", "2"); opcion(opciones, "entityShadows", "false")
            ponerNota(c, "Modo seguro: gráficos básicos y distancia mínima. Puedes subirlos en las opciones del juego.")
            return
        }
        val pr = p(c)
        if (!pr.getBoolean(K_GAMA_HECHA, false)) {
            val inicial = when (gama(c)) { "baja" -> 2; "media" -> 1; else -> 0 }
            pr.edit().putBoolean(K_GAMA_HECHA, true).apply()
            if (inicial > nivel(c)) {
                aplicarNivel(c, opciones, inicial)
                ponerNota(c, "Hemos ajustado los gráficos a tu móvil para que el juego vaya fluido. Puedes cambiarlos en las opciones del juego.")
                return
            }
        }
        val lento = pr.getString(K_LENTO, null)
        val bueno = pr.getBoolean(K_BUENO, false)
        pr.edit().remove(K_LENTO).remove(K_BUENO).apply()
        if (lento != null) {
            pr.edit().putInt(K_BUENAS, 0).apply()
            val n = nivel(c)
            if (n >= NIVEL_MAX) return
            aplicarNivel(c, opciones, n + 1)
            ponerNota(c, (if (lento == "calor") "La última vez el móvil se calentó" else "La última vez el juego iba a tirones") +
                (if (n + 1 == NIVEL_MAX && ScPack.profile(c) == ScPack.PROFILE_LITE) ": cambiamos al modpack Móvil Lite y bajamos los gráficos."
                 else ": hemos bajado los gráficos un poco para que vaya más fluido."))
        } else if (bueno) {
            val buenas = pr.getInt(K_BUENAS, 0) + 1
            if (buenas < 3 || nivel(c) == 0) { pr.edit().putInt(K_BUENAS, buenas).apply(); return }
            pr.edit().putInt(K_BUENAS, 0).apply()
            subirNivel(c, opciones)
        }
    }

    private const val NIVEL_MAX = 5
    private fun nivel(c: Context) = p(c).getInt(K_NIVEL, 0)

    /** Goes down to [n], applying every step up to it (each only lowers what is above its value). */
    private fun aplicarNivel(c: Context, opciones: File, n: Int) {
        val dist = { max: Int -> leer(opciones, "renderDistance")?.toIntOrNull()?.let { if (it > max) opcion(opciones, "renderDistance", "$max") } }
        if (n >= 1) {
            opcion(opciones, "graphicsMode", "0"); opcion(opciones, "renderClouds", "\"false\"")
            opcion(opciones, "entityShadows", "false"); opcion(opciones, "biomeBlendRadius", "0")
            if ((leer(opciones, "particles")?.toIntOrNull() ?: 0) < 1) opcion(opciones, "particles", "1")
        }
        if (n >= 2) { dist(4); opcion(opciones, "simulationDistance", "5"); opcion(opciones, "mipmapLevels", "0") }
        if (n >= 3) escala(c, 0.75)
        if (n >= 4) { dist(2); opcion(opciones, "particles", "2"); escala(c, 0.6) }
        var final = n
        if (n >= 5) {
            if (ScPack.profile(c) == ScPack.PROFILE_LIGHT && !p(c).getBoolean(K_ELEGIDO, false)) ScPack.setProfile(c, ScPack.PROFILE_LITE)
            else final = 4
        }
        p(c).edit().putInt(K_NIVEL, final).apply()
    }

    /** One step back up: the resolution and one more view distance come back (graphics stay fast). */
    private fun subirNivel(c: Context, opciones: File) {
        val n = nivel(c) - 1
        if (n < 4) escala(c, 0.75, subir = true)
        if (n < 3) escala(c, p(c).getFloat(K_ESCALA_ORIGINAL, 1f).toDouble(), subir = true)
        val d = leer(opciones, "renderDistance")?.toIntOrNull()
        val tope = when (n) { 0, 1 -> distanciaPorMemoria(c); 2, 3 -> 4; else -> 2 }
        if (d != null && d < tope) opcion(opciones, "renderDistance", "${d + 1}")
        p(c).edit().putInt(K_NIVEL, n).apply()
        ponerNota(c, "El juego va fluido: hemos subido un poco la calidad.")
    }

    /** FCL's game resolution (menu_setting.json «windowScale», read at launch by LauncherHelper). Only lowers it,
     * unless [subir]; the player's own value is kept to restore it. A missing file is left alone (FCL's default 100 %). */
    private fun escala(c: Context, valor: Double, subir: Boolean = false) {
        runCatching {
            val f = File(com.tungsten.fclauncher.utils.FCLPath.FILES_DIR, "menu_setting.json")
            if (!f.isFile) return
            val j = org.json.JSONObject(f.readText())
            val actual = j.optDouble("windowScale", 1.0)
            if (!p(c).contains(K_ESCALA_ORIGINAL)) p(c).edit().putFloat(K_ESCALA_ORIGINAL, actual.toFloat()).apply()
            if (if (subir) valor <= actual else valor >= actual) return
            j.put("windowScale", valor)
            f.writeText(j.toString(2))
        }
    }

    private fun distanciaPorMemoria(c: Context): Int {
        val total = MemoryUtils.getTotalDeviceMemory(c)
        return when { total >= 11 * 1024 -> 6; total >= 7 * 1024 -> 5; total >= 5 * 1024 -> 4; else -> 3 }
    }

    // ---- the phone's class (Felix 28-10, «ajustes según el móvil») ----

    /**
     * «baja», «media» or «alta» from the memory, the CPU cores and the GPU (read once through an offscreen EGL context
     * and kept in the preferences). Weak GPUs: Mali-4xx/T/G31-G57, PowerVR, Adreno up to 61x.
     */
    fun gama(c: Context): String {
        val total = MemoryUtils.getTotalDeviceMemory(c)
        val nucleos = Runtime.getRuntime().availableProcessors()
        val g = gpu(c)
        val gpuFloja = Regex("Mali-(4\\d\\d|T\\d+|G3\\d|G5[0-7])\\b|PowerVR|Adreno \\(TM\\) ([1-5]\\d\\d|6[01]\\d)\\b").containsMatchIn(g)
        val gpuMedia = Regex("Mali-G(6\\d|7[0-6])\\b|Adreno \\(TM\\) 6[2-4]\\d\\b").containsMatchIn(g)
        return when {
            total < 4608 || nucleos <= 4 || gpuFloja -> "baja"
            total < 7168 || gpuMedia -> "media"
            else -> "alta"
        }
    }

    fun gpu(c: Context): String {
        p(c).getString(K_GPU, null)?.let { return it }
        val nombre = runCatching {
            val d = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY)
            android.opengl.EGL14.eglInitialize(d, IntArray(1), 0, IntArray(1), 1)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            android.opengl.EGL14.eglChooseConfig(d, intArrayOf(android.opengl.EGL14.EGL_RENDERABLE_TYPE, 4,
                android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT, android.opengl.EGL14.EGL_NONE), 0,
                configs, 0, 1, IntArray(1), 0)
            val ctx = android.opengl.EGL14.eglCreateContext(d, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT,
                intArrayOf(android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE), 0)
            val sup = android.opengl.EGL14.eglCreatePbufferSurface(d, configs[0],
                intArrayOf(android.opengl.EGL14.EGL_WIDTH, 1, android.opengl.EGL14.EGL_HEIGHT, 1, android.opengl.EGL14.EGL_NONE), 0)
            android.opengl.EGL14.eglMakeCurrent(d, sup, sup, ctx)
            val r = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER).orEmpty()
            android.opengl.EGL14.eglMakeCurrent(d, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT)
            android.opengl.EGL14.eglDestroySurface(d, sup); android.opengl.EGL14.eglDestroyContext(d, ctx)
            android.opengl.EGL14.eglTerminate(d)
            r
        }.getOrNull().orEmpty()
        if (nombre.isNotBlank()) p(c).edit().putString(K_GPU, nombre).apply()
        return nombre
    }

    private fun limpio(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(60).ifEmpty { "x" }

    /**
     * While playing: every 5 s the FPS (FCL's counter) and, from Android 10, the phone's thermal state. After two
     * minutes of warm-up, a game that averages under 20 FPS for two minutes, or a phone that reaches «severe» heat,
     * leaves a note for the next JUGAR and sends the anonymous SC-46 (FPS, level, phone and GPU). A game that has run
     * 10 minutes at over 45 FPS on average marks itself smooth. Written as it happens, so a crash does not lose it.
     */
    @JvmStatic
    fun vigilarRendimiento(activity: Activity) {
        val c = activity.applicationContext
        Thread({
            runCatching {
                Thread.sleep(120_000)
                val muestras = ArrayDeque<Int>()
                var suma = 0L; var cuenta = 0
                val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
                val lento = { causa: String, fps: Int ->
                    p(c).edit().putString(K_LENTO, causa).remove(K_BUENO).apply()
                    ScAvisos.enviar(c, "SC-46", mapOf("x" to causa, "f" to "$fps", "n" to "${nivel(c)}",
                        "m" to limpio(Build.MODEL), "g" to limpio(p(c).getString(K_GPU, null) ?: "")))
                }
                while (!activity.isFinishing) {
                    Thread.sleep(5_000)
                    if (Build.VERSION.SDK_INT >= 29 && pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                        lento("calor", if (cuenta > 0) (suma / cuenta).toInt() else 0); return@Thread
                    }
                    val fps = FCLBridge.getFps()
                    if (fps <= 0) continue
                    suma += fps; cuenta++
                    muestras.addLast(fps); if (muestras.size > 24) muestras.removeFirst()
                    if (muestras.size == 24 && muestras.average() < 20) { lento("fps", muestras.average().toInt()); return@Thread }
                    if (cuenta >= 120 && suma / cuenta >= 45 && !p(c).getBoolean(K_BUENO, false)) p(c).edit().putBoolean(K_BUENO, true).apply()
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
