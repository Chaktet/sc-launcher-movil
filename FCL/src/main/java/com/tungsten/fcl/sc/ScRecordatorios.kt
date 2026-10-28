package com.tungsten.fcl.sc

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tungsten.fcl.R
import com.tungsten.fcl.activity.SplashActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * SC: phone notifications that bring players back (Felix 27-10, «avisos de eventos»): 15 minutes before each server
 * event (from estado.json, the same feed the home screen shows), and once when a region that was restarting is open
 * again. Ajustes › «Avisos de eventos» turns them off; Android 13+ asks for the notification permission once.
 * Times are never shown as clock times (network rule): «empieza en 15 minutos».
 */
object ScRecordatorios {
    private const val PREFS = "sc"
    private const val K_ACTIVO = "avisos_eventos"
    private const val K_PEDIDO = "avisos_permiso_pedido"
    private const val CANAL = "sc_eventos"
    private const val ESTADO = "https://descargas.servidorcobblemon.es/launcher/estado.json"

    fun activo(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ACTIVO, true)
    fun setActivo(c: Context, on: Boolean) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ACTIVO, on).apply()

    /** Android 13+: the notification permission, asked once (the page calls it after the first home load). */
    fun pedirPermiso(a: Activity) {
        if (Build.VERSION.SDK_INT < 33 || !activo(a)) return
        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(K_PEDIDO, false) || a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        p.edit().putBoolean(K_PEDIDO, true).apply()
        a.runOnUiThread { a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7303) }
    }

    /** eventos: the page's list [{id?, nombre, inicio, region?}]; one inexact alarm per event in the next 8 days. */
    fun programar(c: Context, eventos: String) {
        if (!activo(c)) return
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ahora = System.currentTimeMillis()
        val lista = runCatching { JSONArray(eventos) }.getOrNull() ?: return
        for (i in 0 until lista.length()) {
            val e = lista.optJSONObject(i) ?: continue
            val inicio = e.optLong("inicio"); val nombre = e.optString("nombre").take(60)
            val cuando = inicio - 15 * 60_000
            if (nombre.isEmpty() || cuando <= ahora || cuando - ahora > 8 * 86_400_000L) continue
            val intent = Intent(c, ScRecordatorio::class.java).putExtra("tipo", "evento").putExtra("nombre", nombre)
                .putExtra("region", e.optString("region").take(40))
            val pi = PendingIntent.getBroadcast(c, (nombre + inicio).hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi) }
        }
    }

    /** A region is restarting: check every 5 minutes (for up to two hours) and tell the player once it is open. */
    fun vigilarReinicio(c: Context, regiones: String, intento: Int = 0) {
        if (!activo(c) || intento > 24) return
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(c, ScRecordatorio::class.java).putExtra("tipo", "reinicio").putExtra("regiones", regiones).putExtra("intento", intento)
        val pi = PendingIntent.getBroadcast(c, 7304, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5 * 60_000, pi) }
    }

    fun notificar(c: Context, id: Int, titulo: String, texto: String) {
        if (!activo(c)) return
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CANAL, "Eventos del servidor", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(c, id, Intent(c, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, NotificationCompat.Builder(c, CANAL).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(titulo)
            .setContentText(texto).setContentIntent(abrir).setAutoCancel(true).build())
    }

    fun estadoRegiones(): Map<String, Boolean> {
        val c = URL(ESTADO + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        try {
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val r = j.optJSONArray("regiones") ?: return emptyMap()
            return (0 until r.length()).mapNotNull { r.optJSONObject(it) }.associate { it.optString("nombre") to it.optBoolean("online") }
        } finally { c.disconnect() }
    }
}

class ScRecordatorio : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.getStringExtra("tipo")) {
            "evento" -> {
                val nombre = i.getStringExtra("nombre").orEmpty()
                val region = i.getStringExtra("region").orEmpty()
                ScRecordatorios.notificar(c, nombre.hashCode(), "$nombre empieza en 15 minutos",
                    (if (region.isNotEmpty()) "En $region. " else "") + "Toca para entrar al servidor.")
            }
            "reinicio" -> {
                val pendiente = goAsync()
                Thread {
                    try {
                        val esperando = i.getStringExtra("regiones").orEmpty().split(',').filter { it.isNotBlank() }
                        val estado = runCatching { ScRecordatorios.estadoRegiones() }.getOrNull()
                        val abiertas = esperando.filter { estado?.get(it) == true }
                        if (estado != null && abiertas.isNotEmpty()) {
                            ScRecordatorios.notificar(c, 7305, "${abiertas.joinToString(" y ")} ya está abierto otra vez",
                                "El reinicio ha terminado. Toca para volver a jugar.")
                        } else {
                            ScRecordatorios.vigilarReinicio(c, esperando.joinToString(","), i.getIntExtra("intento", 0) + 1)
                        }
                    } finally { pendiente.finish() }
                }.start()
            }
        }
    }
}
