package com.alarma.whatsapp

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity invisible que enciende la pantalla, se muestra sobre el bloqueo y lanza la llamada.
 *
 * Antes la llamada se lanzaba y la activity se cerraba al instante, con el celular aún bloqueado:
 * WhatsApp quedaba detrás de la pantalla de bloqueo y Android lo bloqueaba. Ahora primero se pide
 * quitar el bloqueo y recién entonces se inicia la llamada, quedándonos en primer plano un momento.
 */
class CallTriggerActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lanzada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        procesar(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handler.removeCallbacksAndMessages(null)
        lanzada = false
        procesar(intent)
    }

    private fun procesar(intent: Intent) {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = lanzar(intent, "")
                override fun onDismissCancelled() = lanzar(intent, " (con el celular aún bloqueado)")
                override fun onDismissError() = lanzar(intent, " (con el celular aún bloqueado)")
            })
            // Si hay PIN/patrón y nadie lo desbloquea, intentamos igual tras unos segundos
            handler.postDelayed({ lanzar(intent, " (con el celular aún bloqueado)") }, 6_000)
        } else {
            lanzar(intent, "")
        }
    }

    private fun lanzar(intent: Intent, nota: String) {
        if (lanzada || isFinishing) return
        lanzada = true

        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val numero = intent.getStringExtra("NUMERO_DESTINO").orEmpty()
        val nombre = intent.getStringExtra("NOMBRE").orEmpty()
        val tipo = intent.getStringExtra("TIPO_LLAMADA") ?: "PHONE"

        if (alarmId != -1) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(alarmId)
        }

        if (numero.isNotEmpty()) {
            val r = if (tipo == "PHONE") {
                WhatsAppCallHelper.realizarLlamadaTelefonica(this, numero)
            } else {
                WhatsAppCallHelper.realizarLlamadaWhatsApp(this, numero)
            }
            HistorialStore.agregar(
                this, RegistroLlamada(System.currentTimeMillis(), nombre, numero, tipo, r.exito, r.detalle + nota)
            )
        }

        // Quedarse un momento en primer plano para que Android permita que WhatsApp tome la pantalla
        handler.postDelayed({ finish() }, 2_500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
