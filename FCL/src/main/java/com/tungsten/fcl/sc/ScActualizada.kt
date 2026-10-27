package com.tungsten.fcl.sc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tungsten.fcl.R
import com.tungsten.fcl.activity.SplashActivity

/**
 * SC: after the app updated itself (ScAppUpdate, MY_PACKAGE_REPLACED) Android has closed it. It is opened again; where
 * Android does not let a background app open a screen, a notification «toca para abrir» does it with one tap.
 */
class ScActualizada : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val abrir = Intent(c, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(abrir) }
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CANAL, "Actualizaciones de la app", NotificationManager.IMPORTANCE_DEFAULT))
        val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
        val pi = PendingIntent.getActivity(c, 0, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            nm.notify(ID, NotificationCompat.Builder(c, CANAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SC Launcher actualizado a la $version")
                .setContentText("Toca para volver a abrirlo.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build())
        }
    }

    private companion object {
        const val CANAL = "sc_actualizaciones"
        const val ID = 7301
    }
}
