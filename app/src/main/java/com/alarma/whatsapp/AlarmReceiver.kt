package com.alarma.whatsapp

import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Se ejecuta cuando AlarmManager dispara la alarma.
 * Abre CallTriggerActivity por dos caminos a la vez:
 *  - startActivity directo (funciona si hay permiso "Mostrar sobre otras apps")
 *  - notificación de pantalla completa (funciona con el celular bloqueado)
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val alarma = AlarmScheduler.cargarAlarmas(context).find { it.id == alarmId } ?: return
        if (!alarma.activa) return

        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmaWhatsApp:CpuWakeLock")
        wakeLock.acquire(15_000)

        lanzar(context, alarma)

        // Se repite todos los días
        AlarmScheduler.programarAlarma(context, alarma)
    }

    private fun opcionesSegundoPlano(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return try {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } catch (_: Exception) {
            null
        }
    }

    private fun lanzar(context: Context, alarma: AlarmaModel) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "canal_alarma_reloj_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Llamadas programadas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Abre la llamada a la hora programada"
                    setBypassDnd(true)
                    enableVibration(true)
                }
            )
        }

        val trigger = Intent(context, CallTriggerActivity::class.java).apply {
            putExtra("ALARM_ID", alarma.id)
            putExtra("NUMERO_DESTINO", alarma.numero)
            putExtra("NOMBRE", alarma.nombreContacto)
            putExtra("TIPO_LLAMADA", alarma.tipoLlamada)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val opts = opcionesSegundoPlano()
        val fullScreenPi = PendingIntent.getActivity(
            context, alarma.id, trigger,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, opts
        )

        val quien = alarma.nombreContacto.ifEmpty { alarma.numero }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (alarma.tipoLlamada == "PHONE") "Llamando a $quien" else "Llamando por WhatsApp a $quien")
            .setContentText("Toca si la llamada no se abre sola")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(alarma.id, notification)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: dependemos del inicio directo
        }

        try {
            if (opts != null) context.startActivity(trigger, opts) else context.startActivity(trigger)
        } catch (e: Exception) {
            // Solo se registra si además no hay forma de que la notificación lo abra
            if (!Settings.canDrawOverlays(context)) {
                HistorialStore.agregar(
                    context, RegistroLlamada(
                        System.currentTimeMillis(), alarma.nombreContacto, alarma.numero, alarma.tipoLlamada, false,
                        "Android bloqueó abrir la app en segundo plano (${e.javaClass.simpleName}). " +
                                "Activa «Mostrar sobre otras apps»."
                    )
                )
            }
        }
    }
}
