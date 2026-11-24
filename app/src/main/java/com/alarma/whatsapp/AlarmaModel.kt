package com.alarma.whatsapp

import org.json.JSONObject

/**
 * Modelo de datos para una alarma de llamada.
 * Se serializa a JSON para persistirse en SharedPreferences.
 */
data class AlarmaModel(
    val id: Int,
    var hora: Int,        // 0-23 (formato 24h)
    var minuto: Int,      // 0-59
    var numero: String,
    var nombreContacto: String,
    var tipoLlamada: String,  // "PHONE" o "WHATSAPP"
    var activa: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("hora", hora)
            put("minuto", minuto)
            put("numero", numero)
            put("nombreContacto", nombreContacto)
            put("tipoLlamada", tipoLlamada)
            put("activa", activa)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AlarmaModel {
            return AlarmaModel(
                id = json.getInt("id"),
                hora = json.getInt("hora"),
                minuto = json.getInt("minuto"),
                numero = json.getString("numero"),
                nombreContacto = json.optString("nombreContacto", ""),
                tipoLlamada = json.optString("tipoLlamada", "PHONE"),
                activa = json.optBoolean("activa", true)
            )
        }
    }
}
