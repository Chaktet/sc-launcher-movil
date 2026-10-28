package com.tungsten.fcl.sc

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.tungsten.fcl.R

/**
 * SC: while the modpack downloads or installs (Felix 27-10, «pantalla encendida al instalar»), the screen stays on and
 * this foreground service keeps the app alive if the player switches to another app, so Android does not kill the
 * download halfway (the cause of most cut installs). It only shows an ongoing notification; the work is ScPack's.
 */
class ScServicioDescarga : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CANAL, "Preparando el juego", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Preparando el juego")
            .setContentText("Descargando el modpack del servidor. Puedes cambiar de app, sigue en segundo plano.")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CANAL = "sc_preparando"
        private const val ID = 7302

        /** Start of the pack work: screen on + service. Never fails the work if Android refuses the service. */
        fun empezar(activity: Activity) {
            activity.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            runCatching {
                val i = Intent(activity, ScServicioDescarga::class.java)
                if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(i) else activity.startService(i)
            }
        }

        fun terminar(activity: Activity) {
            activity.runOnUiThread { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            runCatching { activity.stopService(Intent(activity, ScServicioDescarga::class.java)) }
        }
    }
}
