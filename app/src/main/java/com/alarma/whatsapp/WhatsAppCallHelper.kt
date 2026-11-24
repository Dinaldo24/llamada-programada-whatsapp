package com.alarma.whatsapp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Resultado de un intento de llamada, para mostrarlo y guardarlo en el historial. */
data class ResultadoLlamada(val exito: Boolean, val detalle: String)

object WhatsAppCallHelper {

    private val PAQUETES_WA = listOf(
        "com.whatsapp" to "vnd.android.cursor.item/vnd.com.whatsapp.voip.call",
        "com.whatsapp.w4b" to "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
    )

    /** Llamada por línea normal (SIM). */
    fun realizarLlamadaTelefonica(context: Context, numero: String): ResultadoLlamada {
        val limpio = numero.replace(Regex("[^0-9+]"), "")
        if (limpio.isEmpty()) return ResultadoLlamada(false, "Número vacío")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            abrirMarcador(context, limpio)
            return ResultadoLlamada(false, "Falta el permiso de Teléfono: se abrió el marcador sin llamar.")
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$limpio"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ResultadoLlamada(true, "Llamada por línea iniciada.")
        } catch (e: SecurityException) {
            abrirMarcador(context, limpio)
            ResultadoLlamada(false, "Android bloqueó la llamada: ${e.message}")
        } catch (e: Exception) {
            ResultadoLlamada(false, "Error: ${e.javaClass.simpleName} ${e.message ?: ""}")
        }
    }

    private fun abrirMarcador(context: Context, numero: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    private fun instalado(context: Context, pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Inicia una llamada de voz de WhatsApp.
     * 1) Busca en la agenda la fila "Llamada de voz" que WhatsApp crea por cada contacto y la abre:
     *    eso inicia la llamada directamente.
     * 2) Si no existe, abre el chat (ahí ya no se puede llamar sin tocar la pantalla).
     */
    fun realizarLlamadaWhatsApp(context: Context, numeroDestino: String): ResultadoLlamada {
        val digitos = numeroDestino.replace(Regex("[^0-9]"), "")
        if (digitos.length < 7) return ResultadoLlamada(false, "Número inválido: $numeroDestino")

        val disponibles = PAQUETES_WA.filter { instalado(context, it.first) }
        if (disponibles.isEmpty()) return ResultadoLlamada(false, "WhatsApp no está instalado.")

        val tieneContactos = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

        var ultimoError: String? = null
        if (tieneContactos) {
            for ((pkg, mime) in disponibles) {
                val dataId = buscarDataIdVoip(context, digitos, mime) ?: continue
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("content://com.android.contacts/data/$dataId"), mime)
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return ResultadoLlamada(true, "Llamada de WhatsApp iniciada.")
                } catch (e: SecurityException) {
                    ultimoError = "WhatsApp rechazó el inicio (permiso): ${e.message}"
                } catch (e: ActivityNotFoundException) {
                    ultimoError = "Esta versión de WhatsApp no acepta llamadas desde otras apps."
                } catch (e: Exception) {
                    ultimoError = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
        }

        // Respaldo: abrir el chat
        val chat = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digitos")).apply {
            setPackage(disponibles.first().first)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val motivo = when {
            !tieneContactos -> "Falta el permiso de Contactos"
            ultimoError != null -> ultimoError
            else -> "El número no está guardado en tus contactos o WhatsApp aún no lo sincronizó"
        }
        return try {
            context.startActivity(chat)
            ResultadoLlamada(false, "$motivo. Se abrió el chat, pero no se pudo llamar automáticamente.")
        } catch (e: Exception) {
            ResultadoLlamada(false, "$motivo. Tampoco se pudo abrir el chat: ${e.message}")
        }
    }

    /** Compara por los últimos 9 dígitos para que «986…» y «+51 986…» coincidan. */
    private fun coincide(a: String, b: String): Boolean {
        if (a.length < 7 || b.length < 7) return false
        val n = minOf(9, a.length, b.length)
        return a.takeLast(n) == b.takeLast(n)
    }

    private fun buscarDataIdVoip(context: Context, digitos: String, mime: String): Long? {
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1, ContactsContract.Data.DATA3),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(mime),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val d1 = (c.getString(1) ?: "").replace(Regex("[^0-9]"), "")
                    val d3 = (c.getString(2) ?: "").replace(Regex("[^0-9]"), "")
                    if (coincide(d1, digitos) || coincide(d3, digitos)) return c.getLong(0)
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
