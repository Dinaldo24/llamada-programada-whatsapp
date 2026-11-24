package com.alarma.whatsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Vuelve a registrar las alarmas activas cuando el celular se reinicia
 * o cuando se instala una versión nueva de la app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            AlarmScheduler.reprogramarTodasLasActivas(context)
        }
    }
}
