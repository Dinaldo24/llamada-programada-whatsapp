package com.alarma.whatsapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.util.Calendar

object AlarmScheduler {

    const val PREFS_NAME = "AlarmaWhatsAppPrefs"
    const val KEY_ALARMS_LIST = "alarms_list_json"
    const val KEY_NEXT_ID = "next_alarm_id"

    fun generarNuevoId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentId = prefs.getInt(KEY_NEXT_ID, 2000)
        prefs.edit().putInt(KEY_NEXT_ID, currentId + 1).apply()
        return currentId
    }

    fun cargarAlarmas(context: Context): MutableList<AlarmaModel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ALARMS_LIST, null)
        val lista = mutableListOf<AlarmaModel>()
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    lista.add(AlarmaModel.fromJson(array.getJSONObject(i)))
                }
            } catch (_: Exception) {
            }
        }
        lista.sortWith(compareBy({ it.hora }, { it.minuto }))
        return lista
    }

    fun guardarLista(context: Context, lista: List<AlarmaModel>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in lista) array.put(item.toJson())
        prefs.edit().putString(KEY_ALARMS_LIST, array.toString()).apply()
    }

    /** Momento (epoch ms) de la próxima ejecución: hoy a esa hora o mañana si ya pasó. */
    fun proximaEjecucion(alarm: AlarmaModel, ahora: Long = System.currentTimeMillis()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = ahora
            set(Calendar.HOUR_OF_DAY, alarm.hora)
            set(Calendar.MINUTE, alarm.minuto)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= ahora) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    fun textoTiempoRestante(millis: Long): String {
        val totalMin = ((millis + 59_999) / 60_000).toInt().coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            totalMin <= 0 -> "ahora"
            h == 0 -> "en $m min"
            m == 0 -> "en $h h"
            else -> "en $h h $m min"
        }
    }

    private fun alarmIntent(context: Context) = Intent(context, AlarmReceiver::class.java)

    private val piFlags =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /**
     * Programa la alarma con setAlarmClock (misma prioridad que la app de Reloj).
     * Devuelve un texto legible ("en 3 h 20 min") o un mensaje de error.
     */
    fun programarAlarma(context: Context, alarm: AlarmaModel): String {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return "no se pudo programar: falta el permiso «Alarmas y recordatorios»"
        }

        // Solo se pasa el ID: los datos se leen al disparar (así una edición nunca queda desactualizada)
        val intent = alarmIntent(context).putExtra("ALARM_ID", alarm.id)
        val pendingIntent = PendingIntent.getBroadcast(context, alarm.id, intent, piFlags)

        val showPendingIntent = PendingIntent.getActivity(
            context, alarm.id, Intent(context, MainActivity::class.java), piFlags
        )

        val ahora = System.currentTimeMillis()
        val target = proximaEjecucion(alarm, ahora)

        return try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(target, showPendingIntent), pendingIntent)
            textoTiempoRestante(target - ahora)
        } catch (e: SecurityException) {
            "no se pudo programar: ${e.message}"
        }
    }

    fun cancelarAlarma(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, alarmId, alarmIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun reprogramarTodasLasActivas(context: Context) {
        for (alarm in cargarAlarmas(context)) {
            if (alarm.activa) programarAlarma(context, alarm)
        }
    }
}
