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
import com.tungsten.fclcore.auth.microsoft.MicrosoftAccount
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
            .put("total", MemoryUtils.getTotalDeviceMemory(activity)))
        val renderers = JSONArray()
        RendererManager.rendererList.forEach { renderers.put(JSONObject().put("id", it.id).put("nombre", it.name)) }
        o.put("renderers", renderers)
        o.put("renderer", s.renderer)
        o.put("idioma", LocaleUtils.getLanguage(activity))
        o.put("pack", ScPack.installedVersion(activity) ?: "")
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
        // Phase 4 (PLAN-APP-ANDROID.md): in-app Microsoft window with the desktop launcher's redirect.
        ScUi.js("scApp.dialogo('Cuenta Microsoft', 'En esta versión de prueba todavía no se puede entrar con Microsoft. Entra con No Premium.', '', 'Entendido', '')")
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
        if (clave !in setOf("debug_manifest_url", "debug_no_launch")) return
        val e = activity.getSharedPreferences("sc", android.content.Context.MODE_PRIVATE).edit()
        if (clave == "debug_no_launch") e.putBoolean(clave, valor == "1") else e.putString(clave, valor)
        e.apply()
    }

    @JavascriptInterface
    fun accion(nombre: String) {
        // Reserved for dialog actions coming from Java (phase 3/5: update app, retry).
    }

    companion object {
        private val HOSTS_DATOS = setOf("descargas.servidorcobblemon.es", "servidorcobblemon.es")
        private val HOSTS_ENLACES = setOf("servidorcobblemon.es", "discord.gg", "discord.com")
    }
}
