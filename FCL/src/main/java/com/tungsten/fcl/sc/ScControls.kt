package com.tungsten.fcl.sc

import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.util.Logging
import org.json.JSONObject
import java.io.File
import java.util.logging.Level

/**
 * SC: our default touch layout (assets/controllers/00000000.json) adds the server's Cobblemon keys: Pokémon (M, also
 * opens the starter choice), Lanzar (R) and the party arrows. FCL writes the default only when no layout exists, so a
 * player who already opened an older app keeps the old one without those buttons; this replaces it once, when the
 * saved default is older than the bundled one. The replaced file is kept as 00000000.json.anterior (FCL only reads
 * .json files), in case it had been edited. Layouts players made themselves (other ids) are never touched.
 */
object ScControls {
    private const val DEFAULT = "00000000.json"

    /** Background thread, before Controllers.init() reads the layouts from disk. */
    @JvmStatic
    fun updateDefault() {
        try {
            val saved = File(FCLPath.CONTROLLER_DIR, DEFAULT)
            if (!saved.isFile) return // FCL writes the bundled one itself
            val bundled = ScControls::class.java.getResourceAsStream("/assets/controllers/$DEFAULT")
                ?.bufferedReader()?.use { it.readText() } ?: return
            val oldCode = runCatching { JSONObject(saved.readText()).optInt("versionCode", 0) }.getOrDefault(0)
            val newCode = JSONObject(bundled).optInt("versionCode", 0)
            if (oldCode >= newCode) return
            saved.copyTo(File(saved.parentFile, "$DEFAULT.anterior"), overwrite = true)
            // Written next to it and swapped in one step: an interrupted write never leaves a broken layout
            val tmp = File(saved.parentFile, "$DEFAULT.sc-tmp")
            tmp.writeText(bundled)
            if (!tmp.renameTo(saved)) {
                tmp.delete()
                Logging.LOG.log(Level.WARNING, "SC: could not swap in the new default touch layout; the old one stays")
                return
            }
            Logging.LOG.log(Level.INFO, "SC: default touch layout updated $oldCode -> $newCode")
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "SC: could not update the default touch layout", e)
        }
    }
}
