package com.tungsten.fcl.sc

import android.content.Context
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fcl.setting.VersionSetting
import com.tungsten.fclcore.util.platform.MemoryUtils
import org.json.JSONObject
import java.io.File

/**
 * SC: the game's heap (-Xmx). FCL's «automatic» memory takes 80 % of the memory FREE at launch, which with other apps
 * open was ~1.5 GB: the resource reload of Cobblemon + our pack ran out of heap (OutOfMemoryError while parsing
 * Cobblemon's animations), Minecraft dropped every resource pack and the player played without SC-Pack (27-10, CI
 * emulator and Felix's S25 Ultra). Free memory says nothing about what the game needs, so automatic mode gives each
 * profile what its pack needs, capped by the TOTAL memory of the phone; a player's manual setting is kept as it is.
 *
 * Rule (MB): Móvil wants 3072 and needs at least 2048 (1.5 GB failed); Móvil PRO wants 4096 and needs 3072 (twice the
 * mods and the full 123 MB pack). Cap = min(total − 1536, 70 % of total): Android, the GPU driver and the JVM's own
 * native memory need the rest. The heap only grows when used (-Xms stays 512), so a high -Xmx costs nothing until the
 * game needs it. Below the minimum the page warns the player and suggests the light profile.
 */
object ScMemoria {
    private const val PREFS = "sc"
    private const val KEY_LOG_VISTO = "pack_oom_log_visto"

    private class Necesidad(val minimo: Int, val ideal: Int)

    private fun necesidad(perfil: String) =
        if (perfil == ScPack.PROFILE_PRO) Necesidad(3072, 4096) else Necesidad(2048, 3072)

    private fun tope(total: Int) = minOf(total - 1536, total * 7 / 10).coerceAtLeast(512)

    /** Heap in MB for the automatic mode. */
    private fun automatica(c: Context, perfil: String): Int {
        val total = MemoryUtils.getTotalDeviceMemory(c)
        return minOf(necesidad(perfil).ideal, tope(total))
    }

    /** Hook in FCLGameRepository.getLaunchOptions: the -Xmx in MB for [version], or -1 to keep FCL's own value. */
    @JvmStatic
    fun heapMb(c: Context, version: String, vs: VersionSetting): Int {
        if (version != ScPack.INSTANCE || !vs.isAutoMemory) return -1
        return automatica(c, ScPack.installedProfile(c))
    }

    /** For the page (estado().ram): what the game will get and whether it is enough for the chosen profile. */
    fun estado(c: Context, vs: VersionSetting): JSONObject {
        val perfil = ScPack.profile(c)
        val n = necesidad(perfil)
        val total = MemoryUtils.getTotalDeviceMemory(c)
        val usada = if (vs.isAutoMemory) automatica(c, perfil) else vs.maxMemory
        val o = JSONObject().put("usada", usada).put("necesaria", n.minimo)
        if (usada < n.minimo) {
            o.put("aviso", when {
                !vs.isAutoMemory && tope(total) >= n.minimo ->
                    "Has puesto $usada MB de memoria y el modpack necesita al menos ${n.minimo} MB para cargar el pack " +
                        "de texturas. Activa «Automática» en Ajustes."
                perfil == ScPack.PROFILE_PRO ->
                    "Tu móvil tiene poca memoria para Móvil PRO: puede que el juego entre sin las texturas del servidor. " +
                        "Te recomendamos el modpack Móvil (ligero) en Ajustes."
                else ->
                    "Tu móvil tiene poca memoria para el modpack: puede que el juego entre sin las texturas del servidor. " +
                        "Cierra las demás apps antes de jugar."
            })
        }
        return o
    }

    /**
     * At start: did the last game drop our pack because it ran out of memory? Minecraft logs «Caught error loading
     * resourcepacks» followed by the OutOfMemoryError and goes on without the packs. Reported once per game log.
     */
    fun packFallido(c: Context): String {
        val log = runCatching {
            File(Profiles.getSelectedProfile().repository.getRunDirectory(ScPack.INSTANCE), "logs/latest.log")
        }.getOrNull()?.takeIf { it.isFile } ?: return ""
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val marca = "${log.lastModified()}:${log.length()}"
        if (p.getString(KEY_LOG_VISTO, null) == marca) return ""
        p.edit().putString(KEY_LOG_VISTO, marca).apply()
        var tras = -1
        var oom = false
        log.bufferedReader().useLines { lines ->
            for ((i, l) in lines.withIndex()) {
                if (l.contains("Caught error loading resourcepacks")) tras = i
                else if (tras >= 0 && i - tras <= 3 && l.contains("OutOfMemoryError")) { oom = true; break }
            }
        }
        if (!oom) return ""
        val vs = runCatching { Profiles.getSelectedProfile().repository.getVersionSetting(ScPack.INSTANCE) }.getOrNull()
        val texto = when {
            vs != null && !vs.isAutoMemory ->
                "La última vez el juego se quedó sin memoria y entraste sin las texturas del servidor. " +
                    "Activa «Memoria automática» en Ajustes o sube la memoria."
            ScPack.installedProfile(c) == ScPack.PROFILE_PRO ->
                "La última vez el juego se quedó sin memoria y entraste sin las texturas del servidor. " +
                    "Cierra las demás apps antes de jugar o cambia al modpack Móvil (ligero) en Ajustes."
            else ->
                "La última vez el juego se quedó sin memoria y entraste sin las texturas del servidor. " +
                    "Cierra las demás apps antes de jugar."
        }
        return JSONObject().put("texto", texto).toString()
    }
}
