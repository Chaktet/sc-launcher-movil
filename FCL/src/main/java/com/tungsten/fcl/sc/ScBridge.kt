package com.tungsten.fcl.sc

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.mio.manager.RendererManager
import com.tungsten.fcl.activity.MainActivity
import com.tungsten.fcl.setting.Accounts
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fcl.setting.VersionSetting
import com.tungsten.fclcore.auth.CharacterSelector
import com.tungsten.fclcore.auth.NoSelectedCharacterException
import com.tungsten.fclcore.auth.microsoft.MicrosoftAccount
import java.util.concurrent.CancellationException
import com.tungsten.fclcore.auth.offline.OfflineAccountFactory
import com.tungsten.fclcore.util.Logging
import com.tungsten.fclcore.util.platform.MemoryUtils
import com.tungsten.fcllibrary.util.LocaleUtils
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * SC: the API our page (assets/sc_ui/sc.js) calls as window.SC. Methods arrive on the WebView's binder thread;
 * anything touching FCL state (accounts, profiles, settings) runs on the main thread, like FCL's own UI does.
 */
class ScBridge(private val activity: MainActivity, private val webView: WebView) {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)

    /** Runs [block] on the main thread and waits for its result (the binder thread may block; main never does). */
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        main.post {
            result = runCatching(block)
            latch.countDown()
        }
        latch.await(10, TimeUnit.SECONDS)
        return result?.getOrThrow() ?: throw IllegalStateException("main thread timeout")
    }

    /** Settings the game launches with: the selected version's own ones, or the global ones it inherits. */
    private fun activeSetting(): VersionSetting {
        val profile = Profiles.getSelectedProfile()
        val vs = profile.getVersionSetting(Profiles.getSelectedVersion())
        return if (vs.isUsesGlobal) profile.globalVersionSetting else vs
    }

    @JavascriptInterface
    fun estado(): String = onMain {
        val o = JSONObject()
        Accounts.getSelectedAccount()?.let { a ->
            o.put("cuenta", JSONObject()
                .put("nombre", a.username)
                .put("uuid", a.uuid.toString())
                .put("tipo", if (a is MicrosoftAccount) "microsoft" else "offline"))
        }
        o.put("versionApp", runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull() ?: "")
        val s = activeSetting()
        o.put("ram", JSONObject()
            .put("auto", s.isAutoMemory)
            .put("max", s.maxMemory)
            .put("total", MemoryUtils.getTotalDeviceMemory(activity))
            .put("pack", ScMemoria.estado(activity, s)))
        val renderers = JSONArray()
        RendererManager.rendererList.forEach { renderers.put(JSONObject().put("id", it.id).put("nombre", it.name)) }
        o.put("renderers", renderers)
        o.put("renderer", s.renderer)
        o.put("idioma", LocaleUtils.getLanguage(activity))
        o.put("pack", ScPack.installedVersion(activity) ?: "")
        o.put("perfil", ScPack.profile(activity))
        o.put("perfilInstalado", ScPack.installedProfile(activity))
        o.put("avisos", ScAvisos.activo(activity))
        o.put("eventos", ScRecordatorios.activo(activity))
        o.toString()
    }

    /** Async GET for the page; only our own hosts, so the page can never be used to reach anything else. */
    @JavascriptInterface
    fun pedir(id: String, url: String) {
        io.execute {
            val (ok, body) = runCatching {
                val u = URL(url)
                require(u.protocol == "https" && u.host in HOSTS_DATOS) { "host not allowed" }
                val c = u.openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 10000
                c.useCaches = false
                c.setRequestProperty("Cache-Control", "no-cache")
                try {
                    if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                    true to c.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    c.disconnect()
                }
            }.getOrElse { false to (it.message ?: "error") }
            ScUi.js("scApp.respuesta(${JSONObject.quote(id)}, $ok, ${JSONObject.quote(body)})")
        }
    }

    @JavascriptInterface
    fun entrarNoPremium(nombre: String): String {
        if (!Regex("^[A-Za-z0-9_]{3,16}$").matches(nombre)) {
            return "El nombre debe tener de 3 a 16 letras, números o guion bajo."
        }
        return onMain {
            val uuid = OfflineAccountFactory.getUUIDFromUserName(nombre)
            val existing = Accounts.getAccounts().firstOrNull { it.uuid == uuid && it !is MicrosoftAccount }
            val account = existing ?: Accounts.FACTORY_OFFLINE.create(nombre, uuid).also { Accounts.addAccount(it) }
            Accounts.setSelectedAccount(account)
            ""
        }
    }

    @JavascriptInterface
    fun entrarMicrosoft() {
        // Phase 4: the Microsoft window (ScMicrosoft) opens from FCL's login; Xbox/Minecraft steps follow when it closes
        io.execute {
            try {
                val selector = CharacterSelector { _, names ->
                    names.firstOrNull() ?: throw NoSelectedCharacterException()
                }
                val account = Accounts.FACTORY_MICROSOFT.create(selector, null, null, null, null)
                onMain {
                    Accounts.getAccounts().firstOrNull { it is MicrosoftAccount && it.uuid == account.uuid }
                        ?.let { Accounts.getAccounts().remove(it) }
                    Accounts.addAccount(account)
                    Accounts.setSelectedAccount(account)
                }
                ScUi.js("scApp.refrescar()")
            } catch (e: Exception) {
                if (e is NoSelectedCharacterException || e is CancellationException) return@execute
                Logging.LOG.log(Level.WARNING, "SC: Microsoft sign-in failed", e)
                val msg = JSONObject.quote(Accounts.localizeErrorMessage(activity, e))
                ScUi.js("scApp.dialogo('No se pudo entrar con Microsoft', $msg, '', 'Entendido', '')")
            }
        }
    }

    @JavascriptInterface
    fun cerrarSesion() {
        onMain {
            Accounts.getSelectedAccount()?.let { Accounts.getAccounts().remove(it) }
            Accounts.setSelectedAccount(Accounts.getAccounts().firstOrNull())
        }
    }

    /** Installs/updates the server modpack when needed (ScPack), then launches straight into the server. */
    @JavascriptInterface
    fun jugar() {
        if (onMain { Accounts.getSelectedAccount() } == null) return
        ScPack.playWhenReady(activity)
    }

    /** Checks every modpack file against its hash and downloads again the broken ones. */
    @JavascriptInterface
    fun reparar() {
        ScPack.playWhenReady(activity, forceRepair = true)
    }

    @JavascriptInterface
    fun avanzado() {
        main.post { ScUi.hide() }
    }

    @JavascriptInterface
    fun abrir(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (uri.scheme != "https" || HOSTS_ENLACES.none { uri.host == it || uri.host?.endsWith(".$it") == true }) return
        main.post {
            runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure { Logging.LOG.log(Level.WARNING, "Cannot open $url", it) }
        }
    }

    @JavascriptInterface
    fun ajuste(clave: String, valor: String) {
        main.post {
            val s = activeSetting()
            when (clave) {
                "ram" -> valor.toIntOrNull()?.let { s.maxMemory = it.coerceIn(512, MemoryUtils.getTotalDeviceMemory(activity)) }
                "ramAuto" -> s.isAutoMemory = valor == "1"
                "renderer" -> if (RendererManager.rendererList.any { it.id == valor }) s.renderer = valor
                "perfil" -> { ScPack.setProfile(activity, valor); ScAuto.perfilElegido(activity) }
                "avisos" -> ScAvisos.setActivo(activity, valor == "1")
                "eventos" -> ScRecordatorios.setActivo(activity, valor == "1")
                "idioma" -> valor.toIntOrNull()?.let {
                    LocaleUtils.changeLanguage(activity, it)
                    // FCL's own screens pick the language up on the next start; ours are always in Spanish.
                    ScUi.js("scApp.dialogo('Idioma', 'El idioma de las opciones avanzadas cambiará al volver a abrir la app.', '', 'Entendido', '')")
                }
            }
        }
    }

    /** Debug builds only (CI): writes the test switches ScPack reads (debug_manifest_url, debug_no_launch). */
    @JavascriptInterface
    fun debug(clave: String, valor: String) {
        if ((activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        if (clave !in setOf("debug_manifest_url", "debug_no_launch", "debug_app_manifest_url", "debug_ms_authorize_url", "debug_no_server", "debug_manifest_pro_url")) return
        val e = activity.getSharedPreferences("sc", android.content.Context.MODE_PRIVATE).edit()
        if (clave == "debug_no_launch" || clave == "debug_no_server") e.putBoolean(clave, valor == "1") else e.putString(clave, valor)
        e.apply()
    }

    @JavascriptInterface
    fun accion(nombre: String) {
        // Dialog actions offered from Java
        when (nombre) {
            "actualizarApp" -> io.execute { ScAppUpdate.downloadAndInstall(activity) }
        }
    }

    /** At start: the game closed with an error last time? ({codigo, causa, texto, detalles} once, or "") */
    @JavascriptInterface
    fun cierrePendiente(): String {
        val s = ScCierre.pendiente(activity)
        if (s.isEmpty()) return s
        // Felix 27-10: the notice goes by itself (anonymous: cause, exit code, phone model), no button needed
        val j = runCatching { JSONObject(s) }.getOrNull() ?: return s
        val modelo = android.os.Build.MODEL.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        ScAvisos.enviar(activity, ScAvisos.CIERRE, mapOf("n" to j.optString("causa", "otro"), "x" to j.optInt("codigo").toString(), "m" to modelo))
        return j.put("enviado", true).put("seguidos", ScAuto.cierresSeguidos(activity)).toString()
    }

    /** Event reminders 15 min before (the page passes the events it shows) and the notification permission once. */
    @JavascriptInterface
    fun programarEventos(eventos: String) { ScRecordatorios.programar(activity, eventos); ScRecordatorios.pedirPermiso(activity) }

    /** Regions shown as restarting: tell the player when they are open again. regiones: names, comma separated. */
    @JavascriptInterface
    fun vigilarReinicio(regiones: String) { ScRecordatorios.vigilarReinicio(activity, regiones) }

    /** On Wi-Fi: the next pack downloaded before JUGAR and a daily check of the files (ScPack.prepararEnFondo). */
    @JavascriptInterface
    fun prepararEnFondo() { ScPack.prepararEnFondo(activity) }

    /** What the app corrected by itself since the last game («» when nothing), shown once on the home screen. */
    @JavascriptInterface
    fun notaAuto(): String = ScAuto.nota(activity)

    /** «Entrar en modo seguro» of the error screen: the next game starts with minimum settings. */
    @JavascriptInterface
    fun modoSeguro() { ScAuto.pedirModoSeguro(activity) }

    /** At start: the last game dropped our pack for lack of memory? ({texto} once, or "") */
    @JavascriptInterface
    fun packFallido(): String = onMain { ScMemoria.packFallido(activity) }

    /** «Enviar informe» of the error screen: anonymous SC-45 with the cause key and the exit code. */
    @JavascriptInterface
    fun enviarInformeCierre(causa: String, codigo: String) {
        ScAvisos.enviar(activity, ScAvisos.CIERRE, mapOf("n" to causa, "x" to codigo))
    }

    /** «Cancelar» on the preparing screen (FCL launch steps). */
    @JavascriptInterface
    fun cancelarCarga() { ScCarga.cancelar(); ScPack.cancelar() }

    /**
     * At start, on coming back to the app and every 30 min (the page calls it): a newer app on our website? On Wi-Fi it
     * downloads and installs by itself (Felix 27-10: «automático»); on mobile data the page asks first.
     */
    @JavascriptInterface
    fun buscarActualizacion() {
        io.execute {
            val r = ScAppUpdate.check(activity)
            if (r.isNotEmpty() && ScAppUpdate.sinCoste(activity)) {
                if (ScAppUpdate.puedeAuto()) ScAppUpdate.downloadAndInstall(activity)
            } else if (r.isNotEmpty()) {
                val j = JSONObject(r)
                val texto = "Ya está la versión ${j.getString("version")} de SC Launcher." +
                    j.optString("notas").takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                ScUi.js("scApp.dialogo('Nueva versión de la app', ${JSONObject.quote(texto)}, 'Más tarde', 'Actualizar', 'actualizarApp')")
            }
        }
    }

    companion object {
        private val HOSTS_DATOS = setOf("descargas.servidorcobblemon.es", "servidorcobblemon.es")
        private val HOSTS_ENLACES = setOf("servidorcobblemon.es", "discord.gg", "discord.com")
    }
}
