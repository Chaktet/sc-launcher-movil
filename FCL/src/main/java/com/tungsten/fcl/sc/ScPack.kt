package com.tungsten.fcl.sc

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.gson.JsonParser
import com.tungsten.fcl.activity.MainActivity
import com.tungsten.fcl.game.ModpackHelper
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.task.FetchTask
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.task.TaskExecutor
import com.tungsten.fclcore.task.TaskListener
import com.tungsten.fclcore.util.Logging
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * SC: keeps the server's mobile modpack installed and up to date, then launches straight into the server
 * (PLAN-APP-ANDROID.md, phase 3).
 *
 * movil/sc-pack.json (written by auto_descargas.js on OVH-A) gives the version, the VERSIONED .mrpack URL and its
 * sha256. A new version is downloaded, its sha256 checked, and applied with FCL's own modpack update task (which
 * backs up and rolls back on failure) to one fixed instance. Afterwards every remote file of modrinth.index.json is
 * checked against its sha1 (FCL skips files that already exist without checking them) and re-downloaded if wrong.
 * The installed version is only recorded once all of that succeeded, so an interrupted update is simply redone.
 */
object ScPack {
    const val INSTANCE = "SC Cobblemon"
    const val SERVER = "mc.servidorcobblemon.es"
    private const val PREFS = "sc"
    private const val KEY_VERSION = "pack_version"
    private const val KEY_PROFILE = "perfil"               // the profile the player picked in Ajustes
    private const val KEY_INSTALLED_PROFILE = "pack_perfil" // the profile the installed pack belongs to

    /**
     * The two phone modpacks (Felix 27-10): «Móvil», the light default, and «Móvil PRO», the PC PRO set without the
     * desktop-only mods (built on the website by movil_pro.js). Both install into the same instance, so switching is a
     * pack update: the mods of the other profile go, options.txt and the worlds, screenshots and controls stay.
     */
    const val PROFILE_LIGHT = "movil"
    const val PROFILE_PRO = "pro"
    private val MANIFESTS = mapOf(
        PROFILE_LIGHT to "https://descargas.servidorcobblemon.es/movil/sc-pack.json",
        PROFILE_PRO to "https://descargas.servidorcobblemon.es/movil/sc-pack-pro.json"
    )
    private val DEBUG_MANIFEST_KEYS = mapOf(PROFILE_LIGHT to "debug_manifest_url", PROFILE_PRO to "debug_manifest_pro_url")
    private val ALLOWED_PACK_HOSTS = setOf("descargas.servidorcobblemon.es")
    private val ALLOWED_FILE_HOSTS = setOf("cdn.modrinth.com", "descargas.servidorcobblemon.es")
    private val OVERRIDE_DIRS = listOf("overrides/", "client-overrides/")
    private val PACK_OPTION_KEYS = setOf("resourcePacks", "incompatibleResourcePacks")

    private val running = AtomicBoolean(false)

    // «Cancelar» / «Reintentar» while the pack downloads or installs (a player on a dropping connection had to close
    // the app to try again, 27-10): stops our own download and FCL's install task
    @Volatile private var cancelado = false
    @Volatile private var tareaActual: TaskExecutor? = null
    @Volatile private var conexionActual: HttpURLConnection? = null

    fun cancelar() {
        if (!running.get()) return
        cancelado = true
        tareaActual?.cancel()
        runCatching { conexionActual?.disconnect() }
    }

    private fun siCancelado() { if (cancelado) throw ScError("Cancelado", "") }

    /**
     * Debug builds only (CI emulator test): prefs "sc" may point at a test manifest (debug_manifest_url, any host)
     * and skip launching the game after preparing it (debug_no_launch). Release builds ignore both.
     */
    private fun debugPrefs(context: Context) =
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) else null

    private class Manifest(val version: String, val url: String, val sha256: String, val size: Long)


    private fun ui(texto: String, pct: Int, detalle: String = "") {
        if (enFondo) return // the background check works without touching the page
        ScUi.js("scApp.progreso(${JSONObject.quote(texto)}, $pct, ${JSONObject.quote(detalle)})")
    }

    // ---- In the background (Felix 27-10): on Wi-Fi the next pack is downloaded before JUGAR, and once a day the
    // installed files are checked (sha1 of the downloads, size+CRC of the bundled mods/packs) and the broken ones fixed.
    @Volatile private var enFondo = false
    @Volatile private var jugarPendiente: MainActivity? = null
    @Volatile private var ultimoFondo = 0L
    private const val KEY_REVISADO = "revision_silenciosa"

    fun prepararEnFondo(activity: MainActivity) {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoFondo < 30 * 60_000 || !ScAppUpdate.sinCoste(activity)) return
        if (!running.compareAndSet(false, true)) return
        ultimoFondo = ahora
        enFondo = true
        Schedulers.io().execute {
            try {
                val perfil = profile(activity)
                val repo = Profiles.getSelectedProfile().repository
                if (!repo.isLoaded) repo.refreshVersions()
                val m = runCatching { fetchManifest(MANIFESTS.getValue(perfil), null) }.getOrNull() ?: return@execute
                val instalado = installedVersion(activity) == m.version && installedProfile(activity) == perfil && repo.hasVersion(INSTANCE)
                if (!instalado) {
                    download(m, silencioso = true) // JUGAR will find it ready
                } else {
                    val p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    if (ahora - p.getLong(KEY_REVISADO, 0) > 20 * 3_600_000L) {
                        val cache = File(FCLPath.CACHE_DIR, "sc-pack-${m.version}.mrpack").takeIf { it.isFile && it.length() == m.size }
                        if (cache != null) verifyFiles(cache, repo.getRunDirectory(INSTANCE))
                        p.edit().putLong(KEY_REVISADO, ahora).apply()
                    }
                }
            } catch (e: Throwable) {
                Logging.LOG.log(Level.INFO, "SC pack: background work stopped", e)
            } finally {
                enFondo = false
                running.set(false)
                jugarPendiente?.let { a -> jugarPendiente = null; playWhenReady(a) }
            }
        }
    }

    fun installedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_VERSION, null)

    /** Installs from before the profiles (0.1.0) have the light pack. */
    fun installedProfile(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_INSTALLED_PROFILE, null) ?: PROFILE_LIGHT

    fun profile(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILE, null)?.takeIf { it in MANIFESTS } ?: PROFILE_LIGHT

    /** The new profile is installed on the next JUGAR. */
    fun setProfile(context: Context, profile: String) {
        if (profile !in MANIFESTS) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PROFILE, profile).apply()
    }

    /** JUGAR: makes sure the pack is current (installing/updating it if needed) and then launches the game. */
    fun playWhenReady(activity: MainActivity, forceRepair: Boolean = false) {
        if (!running.compareAndSet(false, true)) {
            // the background check is running: JUGAR starts as soon as it ends (it never repeats its work)
            if (enFondo && !forceRepair) { jugarPendiente = activity; ScUi.js("scApp.progreso('Terminando una revisión de archivos…', -1, '')") }
            return
        }
        cancelado = false
        ScServicioDescarga.empezar(activity)
        Schedulers.io().execute {
            try {
                prepare(activity, forceRepair)
                val noLaunch = debugPrefs(activity)?.getBoolean("debug_no_launch", false) == true
                Schedulers.androidUIThread().execute {
                    ScUi.js("scApp.listo()")
                    if (forceRepair) ScUi.js("scApp.dialogo('Reparar archivos', 'Todos los archivos están bien.', '', 'Entendido', '')")
                    else if (!noLaunch) activity.scLaunch()
                }
            } catch (e: ScError) {
                if (e.titulo == "Cancelado" || cancelado) { ScUi.js("scApp.cancelado()"); return@execute }
                // no notice for being offline: that is the player's connection, not something we can fix
                val codigo = when {
                    e.titulo == "Sin conexión" -> null
                    e.titulo == "Falta espacio" -> ScAvisos.SIN_ESPACIO
                    forceRepair -> ScAvisos.REPARAR
                    else -> ScAvisos.INSTALAR
                }
                codigo?.let { ScAvisos.enviar(activity, it, mapOf("k" to e.titulo.lowercase().replace(Regex("[^a-z]+"), "_").trim('_').take(40))) }
                ScUi.js("scApp.error(${JSONObject.quote(e.titulo)}, ${JSONObject.quote(e.texto)})")
            } catch (e: Throwable) {
                Logging.LOG.log(Level.WARNING, "SC pack: unexpected error", e)
                ScAvisos.enviar(activity, ScAvisos.INESPERADO, mapOf("n" to ScAvisos.clase(e)))
                ScUi.js("scApp.error('No se pudo preparar el juego', ${JSONObject.quote(e.message ?: e.javaClass.simpleName)})")
            } finally {
                ScServicioDescarga.terminar(activity)
                running.set(false)
            }
        }
    }

    private fun prepare(activity: MainActivity, forceRepair: Boolean) {
        val profile = Profiles.getSelectedProfile()
        val repo = profile.repository
        if (!repo.isLoaded) repo.refreshVersions()
        // Up to 0.1.4 every pack update left a full copy of the instance in .minecraft/backup (FCL's update task):
        // free that space, on its own thread so JUGAR does not wait for thousands of deletions
        File(repo.baseDirectory, "backup").listFiles { f -> f.isDirectory && f.name.startsWith("$INSTANCE-") }
            ?.takeIf { it.isNotEmpty() }
            ?.let { viejas -> Thread({ viejas.forEach { it.deleteRecursively() } }, "SC backups").apply { isDaemon = true }.start() }
        ui("Comprobando el modpack…", -1)

        ScAuto.perfilInicial(activity) // first install: the profile this phone can carry (only if never chosen)
        val perfil = profile(activity)
        val debugUrl = debugPrefs(activity)?.getString(DEBUG_MANIFEST_KEYS.getValue(perfil), null)?.takeIf { it.isNotBlank() }
        val manifest = runCatching { fetchManifest(MANIFESTS.getValue(perfil), debugUrl) }.getOrElse {
            Logging.LOG.log(Level.WARNING, "SC pack: manifest not reachable", it)
            null
        }
        val installed = installedVersion(activity)
        val hasInstance = repo.hasVersion(INSTANCE)

        if (manifest == null) {
            if (forceRepair) throw ScError("Sin conexión", "Hace falta internet para comprobar y reparar los archivos.")
            // Offline: playing is still possible with what is installed (the server will say if it is too old).
            if (hasInstance && installed != null) { antesDeJugar(activity, repo, null); return }
            throw ScError("Sin conexión", "Hace falta internet para descargar el modpack la primera vez.")
        }

        if (!hasInstance || installed != manifest.version || installedProfile(activity) != perfil) {
            val mrpack = download(manifest)
            val runDir = repo.getRunDirectory(INSTANCE)
            val playerOptions = if (hasInstance) readOptions(File(runDir, "options.txt")) else null
            // From here the instance is half one pack, half the other until this finishes: forget what was installed,
            // so a failure (or going back to the old profile) installs again instead of launching a mix
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_VERSION).remove(KEY_INSTALLED_PROFILE).commit()
            // A first install cut short (app closed while it downloaded) leaves the instance without modpack.cfg, and
            // FCL's update needs it («No se pudo preparar el juego · …/modpack.cfg», seen 27-10): install it from
            // scratch over the folder instead, with a clean mods/ so nothing of the half install stays
            val update = hasInstance && repo.getModpackConfiguration(INSTANCE).isFile
            if (hasInstance && !update) {
                File(runDir, "mods").deleteRecursively()
                // FCL refuses to install over a version that has no modpack.cfg («Version … already exists», CI 27-10):
                // its version file goes too, so it installs as new; options.txt and the rest of the folder stay
                repo.getVersionJson(INSTANCE).delete()
                repo.refreshVersions()
            }
            install(profile, mrpack, update = update)
            verifyFiles(mrpack, runDir)
            playerOptions?.let { mergeOptions(File(runDir, "options.txt"), it) }
            guardarOpcionesDelPack(activity, mrpack)
            if (!hasInstance) ScAuto.ajustesIniciales(activity, File(runDir, "options.txt"))
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_VERSION, manifest.version).putString(KEY_INSTALLED_PROFILE, perfil).apply()
            File(FCLPath.CACHE_DIR).listFiles { f -> f.name.startsWith("sc-pack-") }?.forEach { if (it != mrpack) it.delete() }
        } else if (forceRepair) {
            verifyFiles(download(manifest), repo.getRunDirectory(INSTANCE))
        }
        antesDeJugar(activity, repo, manifest)
    }

    /**
     * Felix 27-10, automatic corrections before every game: the server's packs active again and in their order
     * (Minecraft drops them all when a reload fails), a repair after a game closed by a broken mod, and what the last
     * game asked for (shorter view after stutter or heat, safe mode). Then the instance is pointed at the server.
     */
    private fun antesDeJugar(activity: MainActivity, repo: com.tungsten.fcl.game.FCLGameRepository, manifest: Manifest?) {
        val runDir = repo.getRunDirectory(INSTANCE)
        val opciones = File(runDir, "options.txt")
        if (ScAuto.consumirReparar(activity)) {
            val cache = manifest?.let { runCatching { download(it) }.getOrNull() }
                ?: File(FCLPath.CACHE_DIR, "sc-pack-${installedVersion(activity)}.mrpack").takeIf { it.isFile }
            if (cache != null) {
                ui("Revisando los archivos tras el último fallo…", -1)
                verifyFiles(cache, runDir)
                ScAuto.ponerNota(activity, "El juego se cerró por un archivo dañado: lo hemos revisado y reparado.")
            }
        }
        reponerPacks(activity, opciones)
        ScAuto.antesDeJugar(activity, opciones)
        selectAndConfigure(serverFor(activity))
    }

    private const val KEY_PACK_OPCIONES = "pack_opciones"

    /** The pack's own resourcePacks lines, kept to put them back before every game. */
    private fun guardarOpcionesDelPack(c: Context, mrpack: File) {
        val lineas = runCatching {
            ZipFile(mrpack).use { z ->
                val e = OVERRIDE_DIRS.firstNotNullOfOrNull { z.getEntry(it + "options.txt") } ?: return
                z.getInputStream(e).bufferedReader().readLines().filter { l -> PACK_OPTION_KEYS.any { l.startsWith("$it:") } }
            }
        }.getOrNull() ?: return
        if (lineas.isNotEmpty()) c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PACK_OPCIONES, lineas.joinToString("\n")).apply()
    }

    private fun reponerPacks(c: Context, opciones: File) {
        var guardadas = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACK_OPCIONES, null)
        if (guardadas == null) {
            // installs from before 0.1.14: read them from the cached pack once
            File(FCLPath.CACHE_DIR, "sc-pack-${installedVersion(c)}.mrpack").takeIf { it.isFile }?.let { guardarOpcionesDelPack(c, it) }
            guardadas = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACK_OPCIONES, null) ?: return
        }
        for (l in guardadas.split("\n")) if (l.contains(':')) ScAuto.opcion(opciones, l.substringBefore(':'), l.substringAfter(':'))
    }

    /** options.txt as ordered key → line ("key:value"), or null when there is none. */
    private fun readOptions(f: File): LinkedHashMap<String, String>? {
        if (!f.isFile) return null
        val m = LinkedHashMap<String, String>()
        f.readLines(StandardCharsets.UTF_8).forEach { l -> l.substringBefore(':', "").takeIf { it.isNotEmpty() }?.let { m[it] = l } }
        return m
    }

    /**
     * A pack update ships its own options.txt; the player's settings (keys, video, sound, language…) are kept on top
     * of it. Only the resource pack lists come from the pack, so a new pack order still applies.
     */
    private fun mergeOptions(f: File, player: Map<String, String>) {
        val fromPack = readOptions(f) ?: LinkedHashMap()
        for ((k, line) in player) if (k !in PACK_OPTION_KEYS) fromPack[k] = line
        f.writeText(fromPack.values.joinToString("\n", postfix = "\n"), StandardCharsets.UTF_8)
    }

    private fun fetchManifest(url: String, debugUrl: String?): Manifest {
        val j = JSONObject(httpText(debugUrl ?: url))
        val m = Manifest(j.getString("version"), j.getString("url"), j.getString("sha256").lowercase(), j.getLong("size"))
        val u = URL(m.url)
        require(debugUrl != null || (u.protocol == "https" && u.host in ALLOWED_PACK_HOSTS)) { "pack URL not allowed: ${m.url}" }
        require(Regex("^[0-9a-f]{64}$").matches(m.sha256)) { "bad sha256" }
        return m
    }

    /**
     * Downloads the .mrpack into the cache (kept until the next version) and checks its sha256. Built for bad mobile
     * connections (Felix 27-10, «descargas a prueba de cortes»): a cut resumes where it stopped (HTTP Range on the .part
     * file), it retries by itself with growing waits, and from the second try it also uses the direct route
     * (directo.servidorcobblemon.es, the same files without Cloudflare, as the desktop launcher does). A background
     * prefetch and JUGAR never download the same file at the same time (descargaLock).
     */
    private val descargaLock = java.util.concurrent.locks.ReentrantLock()

    private fun download(m: Manifest, silencioso: Boolean = false): File {
        val dir = File(FCLPath.CACHE_DIR).apply { mkdirs() }
        val target = File(dir, "sc-pack-${m.version}.mrpack")
        if (target.length() == m.size && sha256(target) == m.sha256) return target
        if (!descargaLock.tryLock()) {
            if (!silencioso) ui("Terminando la descarga que empezó en segundo plano…", -1)
            descargaLock.lock()
        }
        try {
            if (target.length() == m.size && sha256(target) == m.sha256) return target
            if (dir.usableSpace < m.size * 3) {
                throw ScError("Falta espacio", "Libera unos ${m.size * 3 / 1_048_576} MB en el móvil y vuelve a pulsar JUGAR.")
            }
            val part = File(dir, target.name + ".part")
            if (part.length() > m.size) part.delete()
            val urls = listOf(m.url) + listOfNotNull(
                URL(m.url).takeIf { it.host == "descargas.servidorcobblemon.es" }?.let { m.url.replaceFirst("://descargas.", "://directo.") })
            var ultimo: Exception? = null
            for (intento in 0 until 5) {
                siCancelado()
                if (intento > 0) {
                    val espera = longArrayOf(0, 3, 8, 15, 30)[intento]
                    if (!silencioso) ui("Se cortó la conexión · reintentando en $espera s (intento ${intento + 1} de 5)", -1)
                    for (s in 0 until espera) { siCancelado(); Thread.sleep(1000) }
                }
                val url = urls[if (intento == 0) 0 else intento % urls.size]
                try {
                    descargarTramo(m, url, part, silencioso)
                    if (part.length() == m.size) break
                } catch (e: ScError) {
                    throw e
                } catch (e: Exception) {
                    ultimo = e
                    Logging.LOG.log(Level.INFO, "SC pack: download try ${intento + 1} failed at ${part.length()} bytes", e)
                }
            }
            if (part.length() != m.size) {
                if (ultimo != null) MainActivity.getInstance()?.let { ScAvisos.enviar(it, ScAvisos.DESCARGA_PARADA, mapOf("n" to ScAvisos.clase(ultimo!!))) }
                throw ScError("No se pudo descargar", "La conexión se cortó varias veces. Lo descargado se guarda: pulsa Reintentar y seguirá donde se quedó.")
            }
            if (!silencioso) ui("Comprobando la descarga…", -1)
            if (sha256(part) != m.sha256) {
                part.delete()
                throw ScError("Descarga dañada", "El modpack no llegó completo. Vuelve a intentarlo.")
            }
            target.delete()
            if (!part.renameTo(target)) throw ScError("No se pudo guardar", "No se pudo guardar el modpack en el móvil.")
            return target
        } finally {
            descargaLock.unlock()
        }
    }

    /** One try: continues the .part file from its length (Range) or starts again if the server ignores the range. */
    private fun descargarTramo(m: Manifest, url: String, part: File, silencioso: Boolean) {
        val desde = part.length()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        if (desde > 0) c.setRequestProperty("Range", "bytes=$desde-")
        conexionActual = c
        try {
            val code = c.responseCode
            val anadir = desde > 0 && code == 206
            if (code != 200 && code != 206) throw java.io.IOException("HTTP $code")
            java.io.FileOutputStream(part, anadir).use { out ->
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = if (anadir) desde else 0L
                    var lastPct = -1
                    while (true) {
                        siCancelado()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (done + n > m.size) throw java.io.IOException("more bytes than announced")
                        out.write(buf, 0, n)
                        done += n
                        val pct = (done * 100 / m.size).toInt()
                        if (pct != lastPct && !silencioso) {
                            lastPct = pct
                            ui("Descargando el modpack ${m.version} · ${done / 1_048_576} / ${m.size / 1_048_576} MB", pct)
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    /** Installs or updates the instance with FCL's own modpack tasks and waits for them, reporting progress. */
    private fun install(profile: com.tungsten.fcl.setting.Profile, mrpack: File, update: Boolean) {
        val modpack = ModpackHelper.readModpackManifest(mrpack.toPath(), StandardCharsets.UTF_8)
        val task: Task<*> = if (update) {
            // Not FCL's update task: it first copies the whole instance (~400 MB, 10,000+ files) to .minecraft/backup
            // as a single step («0 / 1 pasos» for minutes, Felix 27-10) and never deletes those copies, so every pack
            // update filled the phone a bit more. The Modrinth install task updates by itself: with the old
            // modpack.cfg it removes the files the new pack dropped and keeps the rest.
            com.tungsten.fclcore.mod.modrinth.ModrinthInstallTask(profile.getDependency(), mrpack, modpack,
                modpack.manifest as com.tungsten.fclcore.mod.modrinth.ModrinthManifest, INSTANCE)
        } else {
            ModpackHelper.getInstallTask(profile, mrpack, INSTANCE, modpack)
        }
        val texto = if (update) "Actualizando el modpack…" else "Instalando el modpack…"
        ui(texto, 0)
        val latch = CountDownLatch(1)
        var ok = false
        var error: Exception? = null
        var finished = 0
        lateinit var executor: TaskExecutor
        // The last steps are the big downloads (Cobblemon alone is 140 MB) and can take minutes on a slow connection:
        // the download speed shows they are moving (players reported «stuck at 99 %», 27-10)
        val speed = java.util.concurrent.atomic.AtomicInteger(0)
        fun paint() {
            val total = executor.taskCount.coerceAtLeast(1)
            val mbs = if (speed.get() > 0) " · " + "%.1f".format(speed.get() / 1_048_576.0) + " MB/s" else ""
            val detail = if (total - finished in 1..50 && speed.get() > 0) "Descargando los últimos archivos$mbs" else "$finished / $total pasos$mbs"
            ui(texto, (finished * 100 / total).coerceAtMost(99), detail)
        }
        executor = task.executor(object : TaskListener() {
            override fun onFinished(task: Task<*>) {
                finished++
                paint()
            }

            override fun onStop(success: Boolean, executor: TaskExecutor) {
                ok = success
                error = executor.exception
                latch.countDown()
            }
        })
        // registered once executor exists (the speed timer fires every second from its own thread); kept in a local
        // so the weak registration lives until the install ends
        // a download that stays at 0 B/s for 3 minutes is reported once (SC-41), with the step it stopped at
        val movido = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val avisado = AtomicBoolean(false)
        val speedHandler = java.util.function.Consumer<FetchTask.SpeedEvent> { e ->
            runCatching {
                speed.set(e.speed); paint()
                val ahora = System.currentTimeMillis()
                if (e.speed > 0) movido.set(ahora)
                else if (ahora - movido.get() > 180_000 && avisado.compareAndSet(false, true))
                    MainActivity.getInstance()?.let { ScAvisos.enviar(it, ScAvisos.DESCARGA_PARADA, mapOf("s" to "$finished-${executor.taskCount}")) }
            }
        }
        FetchTask.speedEvent.channel(FetchTask.SpeedEvent::class.java).registerWeak(speedHandler)
        tareaActual = executor
        siCancelado()
        executor.start()
        latch.await()
        tareaActual = null
        siCancelado()
        FetchTask.speedEvent.channel(FetchTask.SpeedEvent::class.java).unregister(speedHandler)
        if (!ok) {
            Logging.LOG.log(Level.WARNING, "SC pack: install/update failed", error)
            // the class of FCL's failure says what broke (download, disk, bad file); the ScError below adds SC-40
            error?.let { err -> MainActivity.getInstance()?.let { ScAvisos.enviar(it, ScAvisos.INSTALAR, mapOf("n" to ScAvisos.clase(err))) } }
            throw ScError(if (update) "No se pudo actualizar" else "No se pudo instalar",
                "Ha fallado la instalación del modpack. Vuelve a intentarlo; si sigue fallando, usa «Reparar archivos».")
        }
        profile.repository.refreshVersions()
    }

    /**
     * Checks every remote file of the pack (modrinth.index.json, sha1) in the instance folder and downloads again
     * the missing or wrong ones. Paths are confined to the instance folder.
     */
    private fun verifyFiles(mrpack: File, runDir: File) {
        val index = ZipFile(mrpack).use { z ->
            val e = z.getEntry("modrinth.index.json") ?: throw ScError("Modpack no válido", "Al modpack le falta su índice.")
            z.getInputStream(e).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        }
        val files = index.getAsJsonArray("files")
        val root = runDir.canonicalFile
        files.forEachIndexed { i, el ->
            val f = el.asJsonObject
            val env = f.getAsJsonObject("env")
            if (env != null && env.get("client")?.asString == "unsupported") return@forEachIndexed
            val rel = f.get("path").asString
            val dest = File(root, rel).canonicalFile
            if (!dest.path.startsWith(root.path + File.separator)) throw ScError("Modpack no válido", "Ruta no permitida: $rel")
            val sha1 = f.getAsJsonObject("hashes").get("sha1").asString.lowercase()
            ui("Comprobando archivos…", i * 100 / files.size(), dest.name)
            if (dest.isFile && hash(dest, "SHA-1") == sha1) return@forEachIndexed
            val url = f.getAsJsonArray("downloads").map { it.asString }
                .firstOrNull { runCatching { URL(it) }.getOrNull()?.let { u -> u.protocol == "https" && u.host in ALLOWED_FILE_HOSTS } == true }
                ?: throw ScError("Modpack no válido", "Sin descarga permitida para ${dest.name}.")
            ui("Descargando ${dest.name}…", i * 100 / files.size())
            val tmp = File(dest.path + ".sc-tmp")
            dest.parentFile?.mkdirs()
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                try {
                    if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                    c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it, 64 * 1024) } }
                } finally {
                    c.disconnect()
                }
            }.onFailure { tmp.delete(); throw ScError("No se pudo descargar", "Falló la descarga de ${dest.name}. Revisa tu conexión.") }
            if (hash(tmp, "SHA-1") != sha1) {
                tmp.delete()
                throw ScError("Descarga dañada", "${dest.name} llegó dañado. Vuelve a intentarlo.")
            }
            dest.delete()
            if (!tmp.renameTo(dest)) throw ScError("No se pudo guardar", "No se pudo guardar ${dest.name}.")
        }
        verifyOverrides(mrpack, root)
    }

    /**
     * Files shipped inside the .mrpack (overrides/), not in the download list: our mobile pack carries Cobblemon,
     * sc-avisos-cliente and the resource pack this way. Only mods/ and resourcepacks/ are checked (size + CRC-32 from
     * the zip) and restored from the .mrpack itself; configs and options.txt belong to the player and are left alone.
     */
    private fun verifyOverrides(mrpack: File, root: File) {
        ZipFile(mrpack).use { z ->
            val entries = z.entries().toList().filter { e ->
                !e.isDirectory && OVERRIDE_DIRS.any { d -> e.name.startsWith(d) } &&
                    e.name.substringAfter('/').let { it.startsWith("mods/") || it.startsWith("resourcepacks/") }
            }
            entries.forEachIndexed { i, e ->
                val rel = e.name.substringAfter('/')
                val dest = File(root, rel).canonicalFile
                if (!dest.path.startsWith(root.path + File.separator)) throw ScError("Modpack no válido", "Ruta no permitida: $rel")
                ui("Comprobando archivos…", i * 100 / entries.size, dest.name)
                if (dest.isFile && dest.length() == e.size && crc32(dest) == e.crc) return@forEachIndexed
                val tmp = File(dest.path + ".sc-tmp")
                dest.parentFile?.mkdirs()
                z.getInputStream(e).use { inp -> tmp.outputStream().use { inp.copyTo(it, 64 * 1024) } }
                if (tmp.length() != e.size || crc32(tmp) != e.crc) {
                    tmp.delete()
                    throw ScError("Modpack dañado", "${dest.name} está dañado en el modpack. Vuelve a intentarlo.")
                }
                dest.delete()
                if (!tmp.renameTo(dest)) throw ScError("No se pudo guardar", "No se pudo guardar ${dest.name}.")
            }
        }
    }

    private fun crc32(f: File): Long {
        val c = CRC32()
        f.inputStream().use { inp ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                c.update(buf, 0, n)
            }
        }
        return c.value
    }

    /**
     * The server JUGAR enters. Debug builds only: debug_no_server makes the game stop at its title screen (the CI
     * emulator checks the menus there and must not join the real server with its test account).
     */
    private fun serverFor(context: Context) =
        if (debugPrefs(context)?.getBoolean("debug_no_server", false) == true) "" else SERVER

    /** Selects our instance and points it at the server (quickPlay: JUGAR enters the server directly). */
    private fun selectAndConfigure(server: String) {
        val latch = CountDownLatch(1)
        Schedulers.androidUIThread().execute {
            try {
                val profile = Profiles.getSelectedProfile()
                profile.selectedVersion = INSTANCE
                profile.repository.specializeVersionSetting(INSTANCE)?.let { vs ->
                    if (vs.serverIp != server) vs.serverIp = server
                    // the last game closed because of the graphics: try the next renderer (Felix 27-10, automatic)
                    MainActivity.getInstance()?.let { a ->
                        if (ScAuto.consumirRender(a)) {
                            val lista = com.mio.manager.RendererManager.rendererList
                            if (lista.size > 1) {
                                val i = lista.indexOfFirst { it.id == vs.renderer }
                                val otro = lista[(i + 1).mod(lista.size)]
                                vs.renderer = otro.id
                                ScAuto.ponerNota(a, "La última vez fallaron los gráficos: ahora probamos el modo «${otro.name}». Puedes cambiarlo en Ajustes.")
                            }
                        }
                    }
                    // the game's own notices (sc-avisos-cliente) follow the app's switch, as -Dsc.avisos does on the PC
                    val avisos = "-Dsc.avisos=" + (if (MainActivity.getInstance()?.let { ScAvisos.activo(it) } != false) "1" else "0")
                    val args = (vs.javaArgs.split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith("-Dsc.avisos=") } + avisos).joinToString(" ")
                    if (vs.javaArgs != args) vs.javaArgs = args
                }
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

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

    private fun sha256(f: File) = hash(f, "SHA-256")

    private fun hash(f: File, alg: String): String {
        val md = MessageDigest.getInstance(alg)
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
