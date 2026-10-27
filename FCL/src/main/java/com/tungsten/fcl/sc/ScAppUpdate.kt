package com.tungsten.fcl.sc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tungsten.fcl.activity.MainActivity
import com.tungsten.fclcore.util.Logging
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level

/**
 * SC: the app updates itself from our website (PLAN-APP-ANDROID.md, phase 5; no Google Play).
 *
 * movil/app/sc-app.json = {"versionCode", "versionName", "notas", "apks": {"<abi>": {"url", "sha256", "size"}}}.
 * Every apk url is versioned (downloads are cached for a year). The APK is checked against the manifest's SHA-256
 * before Android's installer is opened, and Android itself refuses an update not signed with our key.
 * FCL's own updater (FCL-Team's version map) is switched off in UpdateChecker.check().
 */
object ScAppUpdate {
    /** FCL's own updater stays off (UpdateChecker.check). */
    const val REPLACES_FCL_UPDATER = true

    private const val MANIFEST_URL ="https://descargas.servidorcobblemon.es/movil/app/sc-app.json"
    private val ALLOWED_HOSTS = setOf("descargas.servidorcobblemon.es")

    private data class Pending(val versionCode: Long, val versionName: String, val url: String, val sha256: String, val size: Long)

    @Volatile
    private var pending: Pending? = null

    private fun debuggable(c: Context) = (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun currentVersionCode(c: Context): Long {
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** Background thread. Returns {"version", "notas"} when a newer app exists for this phone, "" otherwise. */
    fun check(c: Context): String {
        val url = (if (debuggable(c)) c.getSharedPreferences("sc", Context.MODE_PRIVATE).getString("debug_app_manifest_url", null) else null)
            ?.takeIf { it.isNotBlank() } ?: MANIFEST_URL
        return try {
            val m = JSONObject(httpText(url))
            val code = m.getLong("versionCode")
            if (code <= currentVersionCode(c)) return ""
            val apks = m.getJSONObject("apks")
            val abi = Build.SUPPORTED_ABIS.firstOrNull { apks.has(it) } ?: return ""
            val a = apks.getJSONObject(abi)
            val apkUrl = a.getString("url")
            val u = URL(apkUrl)
            if (!debuggable(c) && (u.protocol != "https" || u.host !in ALLOWED_HOSTS)) return ""
            val p = Pending(code, m.getString("versionName"), apkUrl, a.getString("sha256").lowercase(), a.getLong("size"))
            pending = p
            JSONObject().put("version", p.versionName).put("notas", m.optString("notas")).toString()
        } catch (e: Exception) {
            Logging.LOG.log(Level.INFO, "SC app update: manifest not reachable", e)
            ""
        }
    }

    /** Wi-Fi (or any unmetered network): only then the ~170 MB update downloads without asking. */
    fun sinCoste(c: Context): Boolean =
        runCatching { !(c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)

    /** Automatic installs at most every 10 min: Android's own prompt sends the app to the background and back,
     * which checks again, so a player who declines would otherwise get it again at once. */
    @Volatile private var ultimoAuto = 0L
    fun puedeAuto(): Boolean { val now = System.currentTimeMillis(); if (now - ultimoAuto < 10 * 60_000) return false; ultimoAuto = now; return true }

    private val busy = AtomicBoolean(false)
    private const val MAX_APK = 300L * 1024 * 1024

    /** Background thread. Downloads the pending APK (with progress in our page) and opens Android's installer. */
    fun downloadAndInstall(activity: MainActivity) {
        val p = pending ?: return
        if (!busy.compareAndSet(false, true)) return // one download/install at a time
        try {
            if (p.size <= 0 || p.size > MAX_APK) throw ScError("No se pudo actualizar", "La nueva versión anunciada no es válida.")
            val dir = File(activity.cacheDir, "sc-app").apply { mkdirs() }
            dir.listFiles()?.filter { !it.name.contains(p.versionName) }?.forEach { it.delete() }
            val apk = File(dir, "SC-Launcher-${p.versionName}.apk")
            if (!(apk.length() == p.size && sha256(apk) == p.sha256)) {
                if (dir.usableSpace < p.size * 2) throw ScError("Falta espacio", "Libera unos ${p.size * 2 / 1_048_576} MB y vuelve a intentarlo.")
                val part = File(dir, apk.name + ".part")
                val c = URL(p.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                try {
                    if (c.responseCode != 200) throw ScError("No se pudo descargar", "El servidor respondió ${c.responseCode}. Vuelve a intentarlo más tarde.")
                    c.inputStream.use { inp ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var total = 0L
                            var last = -1
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                if (total + n > p.size) throw ScError("Descarga dañada", "La nueva versión llegó dañada. Vuelve a intentarlo.")
                                out.write(buf, 0, n)
                                total += n
                                val pct = (total * 100 / p.size).toInt()
                                if (pct != last) {
                                    last = pct
                                    progress("Descargando SC Launcher ${p.versionName}", pct, "${total / 1_048_576}/${p.size / 1_048_576} MB")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    part.delete()
                    throw e
                } finally {
                    c.disconnect()
                }
                if (part.length() != p.size || sha256(part) != p.sha256) {
                    part.delete()
                    throw ScError("Descarga dañada", "La nueva versión llegó dañada. Vuelve a intentarlo.")
                }
                apk.delete()
                if (!part.renameTo(apk)) throw ScError("No se pudo guardar", "No se pudo guardar la nueva versión.")
            }
            verifyApk(activity, apk, p)
            ScUi.js("scApp.listo()")
            activity.runOnUiThread { openInstaller(activity, apk) }
        } catch (e: ScError) {
            ScUi.js("scApp.error(${JSONObject.quote(e.titulo)}, ${JSONObject.quote(e.texto)})")
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "SC app update failed", e)
            ScUi.js("scApp.error('No se pudo actualizar la app', 'Revisa tu conexión y vuelve a intentarlo.')")
        } finally {
            busy.set(false)
        }
    }

    /**
     * The downloaded APK must be this same app (package), signed with the same certificate as the installed one,
     * and exactly the announced version, newer than ours. Otherwise it is deleted and never shown to the installer.
     */
    private fun verifyApk(c: Context, apk: File, p: Pending) {
        val pm = c.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val mine = pm.getPackageInfo(c.packageName, flags)
        val ok = archive != null && archive.packageName == c.packageName &&
            versionCode(archive) == p.versionCode && p.versionCode > currentVersionCode(c) &&
            certs(archive).let { it.isNotEmpty() && it == certs(mine) }
        if (!ok) {
            apk.delete()
            throw ScError("Actualización no válida", "La nueva versión no está firmada por el servidor. No se ha instalado nada.")
        }
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun certs(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else @Suppress("DEPRECATION") info.signatures
        return sigs.orEmpty().map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun openInstaller(activity: MainActivity, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            // Android asks once to allow installing from this app; the verified APK stays, «Continuar» goes on with it
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
            ScUi.js("scApp.dialogo('Permite instalar la actualización', 'Activa «Permitir de esta fuente» para SC Launcher, vuelve aquí y pulsa Continuar.', 'Más tarde', 'Continuar', 'actualizarApp')")
            return
        }
        // Felix 27-10: «automático como en PC». Android's PackageInstaller session instead of the old «open the APK»
        // intent: from Android 12 an app that installed itself (installer of record, set by the first update done
        // this way) may update itself with USER_ACTION_NOT_REQUIRED, without the prompt. The first time, or on older
        // Android, the status comes back PENDING_USER_ACTION and Android's own confirmation is shown.
        val pi = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(activity.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
        }
        try {
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 256 * 1024) }
                    s.fsync(out)
                }
                // The status goes to an activity, not a receiver (Felix 27-10: after 0.1.11 installed itself the app stayed
                // closed): Android's installer delivers it, and it may open a screen, so on success the app opens again
                // by itself in its new version. ScResultadoInstalacion also shows Android's confirmation when it is needed.
                val app = activity.applicationContext
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getActivity(app, id, Intent(app, ScResultadoInstalacion::class.java), flags)
                s.commit(status.intentSender)
            }
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "SC app update: session failed, falling back to the installer screen", e)
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".provider", apk)
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun progress(texto: String, pct: Int, detalle: String) =
        ScUi.js("scApp.progreso(${JSONObject.quote(texto)}, $pct, ${JSONObject.quote(detalle)})")

    private fun httpText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Receives the result of the app's own update from Android's installer (ScAppUpdate): shows Android's confirmation
 * when it asks for one, opens the app again in its new version when it worked, and reports a failure in our page.
 */
class ScResultadoInstalacion : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> runCatching {
                startActivity(Intent(this, com.tungsten.fcl.activity.SplashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            else -> {
                val msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Logging.LOG.log(Level.WARNING, "SC app update: install failed: $msg")
                // cancelled by the player is not an error worth a dialog
                if (i.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) != PackageInstaller.STATUS_FAILURE_ABORTED)
                    ScUi.js("scApp.error('No se pudo actualizar la app', 'Android no pudo instalar la nueva versión. Vuelve a intentarlo.')")
            }
        }
        finish()
    }
}
