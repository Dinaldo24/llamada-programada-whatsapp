package com.alarma.whatsapp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Lista central de todo lo que la app necesita para llamar sola.
 * La usan la pantalla de permisos y el aviso de la pantalla principal.
 */
object Permisos {

    enum class Tipo { RUNTIME, AJUSTE }

    data class Item(
        val id: String,
        val titulo: String,
        val descripcion: String,
        val icono: Int,
        val tipo: Tipo,
        /** Permisos de ejecución a pedir (solo para RUNTIME). */
        val runtime: Array<String> = emptyArray(),
        /** Pantalla de Ajustes a abrir (solo para AJUSTE). */
        val ajuste: ((Context) -> Intent)? = null,
        /** null = el sistema no permite comprobarlo (hay que revisarlo a mano). */
        val concedido: (Context) -> Boolean?
    )

    private fun granted(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    private fun appUri(c: Context) = Uri.parse("package:${c.packageName}")

    val esXiaomi: Boolean
        get() = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    fun todos(): List<Item> {
        val lista = mutableListOf<Item>()

        lista += Item(
            "contactos", "Contactos",
            "Para elegir a quién llamar y encontrar su llamada de WhatsApp.",
            R.drawable.ic_person, Tipo.RUNTIME,
            runtime = arrayOf(Manifest.permission.READ_CONTACTS),
            concedido = { granted(it, Manifest.permission.READ_CONTACTS) }
        )
        lista += Item(
            "telefono", "Teléfono",
            "Para hacer llamadas por línea normal sin que tengas que tocar nada.",
            R.drawable.ic_phone, Tipo.RUNTIME,
            runtime = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE),
            concedido = { granted(it, Manifest.permission.CALL_PHONE) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lista += Item(
                "notificaciones", "Notificaciones",
                "Para avisarte y despertar la pantalla cuando llega la hora.",
                R.drawable.ic_bell, Tipo.RUNTIME,
                runtime = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                concedido = { granted(it, Manifest.permission.POST_NOTIFICATIONS) }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lista += Item(
                "alarmas", "Alarmas y recordatorios",
                "Para que la llamada salga a la hora exacta.",
                R.drawable.ic_alarm, Tipo.AJUSTE,
                ajuste = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri(it)) },
                concedido = { (it.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() }
            )
        }
        lista += Item(
            "overlay", "Mostrar sobre otras apps",
            "Clave: sin esto Android bloquea abrir WhatsApp cuando la app está cerrada.",
            R.drawable.ic_layers, Tipo.AJUSTE,
            ajuste = { Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri(it)) },
            concedido = { Settings.canDrawOverlays(it) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            lista += Item(
                "fullscreen", "Pantalla completa",
                "Permite encender la pantalla con el celular bloqueado (Android 14+).",
                R.drawable.ic_fullscreen, Tipo.AJUSTE,
                ajuste = { Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appUri(it)) },
                concedido = { (it.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent() }
            )
        }
        lista += Item(
            "bateria", "Batería sin restricciones",
            "Evita que el sistema duerma la app y cancele la llamada.",
            R.drawable.ic_battery, Tipo.AJUSTE,
            ajuste = { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri(it)) },
            concedido = { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) }
        )
        if (esXiaomi) {
            lista += Item(
                "xiaomi", "Permisos extra de Xiaomi",
                "Activa «Mostrar ventanas emergentes en segundo plano» y «Mostrar en pantalla de bloqueo».",
                R.drawable.ic_settings, Tipo.AJUSTE,
                ajuste = { ctx ->
                    Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                        setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        putExtra("extra_pkgname", ctx.packageName)
                    }
                },
                concedido = { null }
            )
        }
        return lista
    }

    /** Elementos que se pueden comprobar y faltan. */
    fun faltantes(context: Context): List<Item> = todos().filter { it.concedido(context) == false }

    fun intentAjustesApp(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri(context))
}
