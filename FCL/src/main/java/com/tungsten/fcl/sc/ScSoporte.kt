package com.tungsten.fcl.sc

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.StatFs
import androidx.core.content.FileProvider
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.util.Logging
import com.tungsten.fclcore.util.platform.MemoryUtils
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * SC: «Informe para soporte» (Felix 28-10: «un zip de acceso rápido para que lo descarguen y nos lo envíen»). One zip
 * with what we need to see a problem the anonymous notices miss: the launcher and game logs, the newest crash reports
 * and JVM crash files, the FCL menu settings, options.txt and a phone/app summary. It goes out only through Android's
 * share menu, chosen by the player. Every file is text and passes through [limpia] first: launch arguments and
 * Microsoft responses carry access/refresh tokens, and the logs may hold the Android account path.
 */
object ScSoporte {
    private const val MAX_BYTES = 2 * 1024 * 1024 // per file: the last 2 MB (a game log can grow to tens of MB)
    private val fondo = Executors.newSingleThreadExecutor { r -> Thread(r, "SC Soporte").apply { isDaemon = true } }

    // tokens in launch arguments, JSON bodies, headers and URLs; a JWT anywhere
    private val SECRETOS = listOf(
        Regex("(--(?:accessToken|clientId|xuid|session)\\s+)\\S+", RegexOption.IGNORE_CASE),
        Regex("(\"(?:access_?token|refresh_?token|id_?token|token|xuid|clientToken|Authorization)\"\\s*:\\s*\")[^\"]*", RegexOption.IGNORE_CASE),
        Regex("(\\b(?:access_?token|refresh_?token|id_?token|code|code_verifier)=)[^&\\s\"]+", RegexOption.IGNORE_CASE),
        Regex("(Authorization:\\s*(?:Bearer|XBL3\\.0)?\\s*)\\S.*", RegexOption.IGNORE_CASE),
        Regex("()eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]*"),
        Regex("()(?:EwA|M\\.C\\d|M\\.R\\d)[A-Za-z0-9!*_.$-]{40,}"), // Microsoft opaque tokens
    )

    fun limpia(texto: String): String = SECRETOS.fold(texto) { t, r -> r.replace(t) { m -> m.groupValues[1] + "<oculto>" } }

    /** Builds the zip on a background thread and opens the share menu; errors go back to the page as a dialog. */
    fun crearYCompartir(activity: Activity) {
        fondo.execute {
            try {
                val zip = crear(activity)
                activity.runOnUiThread {
                    val uri = FileProvider.getUriForFile(activity, activity.packageName + ".provider", zip)
                    val enviar = Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "Informe SC Launcher móvil")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity.startActivity(Intent.createChooser(enviar, "Enviar el informe a soporte"))
                }
            } catch (e: Throwable) {
                Logging.LOG.log(Level.WARNING, "SC soporte: report failed", e)
                ScUi.js("scApp.dialogo('Informe para soporte', ${JSONObject.quote("No se pudo crear el informe (" + e.javaClass.simpleName + ").")}, '', 'Entendido', '')")
            }
        }
    }

    private fun crear(activity: Activity): File {
        val dir = File(activity.cacheDir, "soporte").apply { mkdirs() }
        // old reports go after an hour: a newer one must not delete a zip the receiving app is still reading (Codex 29-10).
        // Requests run one at a time on [fondo], so two taps never write the same file
        val haceUnaHora = System.currentTimeMillis() - 3_600_000
        dir.listFiles()?.filter { it.lastModified() < haceUnaHora }?.forEach { it.delete() }
        val fecha = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(Date())
        val zip = File(dir, "SC-informe-$fecha.zip")
        val runDir = runCatching { Profiles.getSelectedProfile().repository.getRunDirectory(ScPack.INSTANCE) }.getOrNull()
        val archivos = linkedMapOf<String, File>()
        File(FCLPath.LOG_DIR).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.take(6)
            ?.forEach { archivos["launcher/${it.name}"] = it }
        if (runDir != null) {
            File(runDir, "logs/latest.log").takeIf { it.isFile }?.let { archivos["juego/latest.log"] = it }
            File(runDir, "logs/debug.log").takeIf { it.isFile }?.let { archivos["juego/debug.log"] = it }
            File(runDir, "options.txt").takeIf { it.isFile }?.let { archivos["juego/options.txt"] = it }
            File(runDir, "crash-reports").listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.take(3)
                ?.forEach { archivos["crash-reports/${it.name}"] = it }
            runDir.listFiles { f -> f.isFile && f.name.startsWith("hs_err") }?.sortedByDescending { it.lastModified() }?.take(2)
                ?.forEach { archivos["jvm/${it.name}"] = it }
        }
        File(FCLPath.FILES_DIR, "menu_setting.json").takeIf { it.isFile }?.let { archivos["app/menu_setting.json"] = it }

        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            fun escribe(nombre: String, texto: String) {
                out.putNextEntry(ZipEntry(nombre))
                out.write(limpia(texto).toByteArray(StandardCharsets.UTF_8))
                out.closeEntry()
            }
            escribe("informe.txt", resumen(activity, runDir, archivos.keys))
            for ((nombre, f) in archivos) runCatching { escribe(nombre, cola(f)) }
                .onFailure { Logging.LOG.log(Level.INFO, "SC soporte: skipped $nombre", it) }
        }
        return zip
    }

    /** The last [MAX_BYTES] of a text file (the end of a log is where it broke). */
    private fun cola(f: File): String {
        RandomAccessFile(f, "r").use { raf ->
            val desde = (raf.length() - MAX_BYTES).coerceAtLeast(0)
            val buf = ByteArray((raf.length() - desde).toInt())
            raf.seek(desde); raf.readFully(buf)
            val texto = String(buf, StandardCharsets.UTF_8)
            return if (desde > 0) "[… recortado: solo los últimos ${MAX_BYTES / 1024} KB …]\n" + texto.substringAfter('\n') else texto
        }
    }

    private fun libreMb(ruta: String): Long = runCatching { StatFs(ruta).availableBytes / 1_048_576 }.getOrDefault(-1)

    private fun resumen(activity: Activity, runDir: File?, incluidos: Collection<String>): String {
        val pkg = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0) }.getOrNull()
        return buildString {
            appendLine("Informe SC Launcher móvil · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date())}")
            appendLine("App: ${pkg?.versionName} (${if (Build.VERSION.SDK_INT >= 28) pkg?.longVersionCode else 0})")
            appendLine("Móvil: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("RAM total: ${MemoryUtils.getTotalDeviceMemory(activity)} MB · núcleos: ${Runtime.getRuntime().availableProcessors()}")
            appendLine("GPU: ${runCatching { ScAuto.gpu(activity) }.getOrDefault("?")} · gama: ${runCatching { ScAuto.gama(activity) }.getOrDefault("?")}")
            appendLine("Espacio libre: compartido ${libreMb(FCLPath.SC_SHARED_ROOT)} MB · interno ${libreMb(activity.filesDir.absolutePath)} MB")
            appendLine("Perfil elegido: ${ScPack.profile(activity)} · instalado: ${ScPack.installedProfile(activity)} ${ScPack.installedVersion(activity) ?: "(ninguno)"}")
            appendLine("Escalón de rendimiento: ${ScAuto.nivelActual(activity)}")
            appendLine("Carpeta del juego: ${if (runDir?.isDirectory == true) "sí" else "no"}")
            appendLine()
            appendLine("Incluido:")
            incluidos.forEach { appendLine("- $it") }
        }
    }
}
