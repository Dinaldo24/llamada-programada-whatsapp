package com.alarma.whatsapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Una ejecución de llamada (programada o de prueba) con su resultado. */
data class RegistroLlamada(
    val timestamp: Long,
    val nombre: String,
    val numero: String,
    val tipo: String,       // "PHONE" o "WHATSAPP"
    val exito: Boolean,
    val detalle: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", timestamp); put("n", nombre); put("num", numero)
        put("tipo", tipo); put("ok", exito); put("d", detalle)
    }

    companion object {
        fun fromJson(o: JSONObject) = RegistroLlamada(
            o.optLong("t"), o.optString("n"), o.optString("num"),
            o.optString("tipo", "PHONE"), o.optBoolean("ok"), o.optString("d")
        )
    }
}

/** Historial persistido en SharedPreferences (máx. 100 registros, el más reciente primero). */
object HistorialStore {
    private const val KEY = "historial_json"
    private const val MAX = 100

    fun cargar(context: Context): List<RegistroLlamada> {
        val json = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { RegistroLlamada.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun agregar(context: Context, registro: RegistroLlamada) {
        val lista = mutableListOf(registro)
        lista.addAll(cargar(context).take(MAX - 1))
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun limpiar(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(AlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE)
}
