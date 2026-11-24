package com.alarma.whatsapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Pantalla de permisos: se muestra al abrir la app por primera vez y
 * desde el aviso/escudo de la pantalla principal.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    private val runtimeLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Si el usuario lo negó "para siempre", Android ya no muestra el diálogo: mandamos a Ajustes
        val negadoDefinitivo = result.any { (perm, ok) -> !ok && !shouldShowRequestPermissionRationale(perm) }
        if (negadoDefinitivo) {
            Toast.makeText(this, "Actívalo en Permisos de la app", Toast.LENGTH_LONG).show()
            startActivity(Permisos.intentAjustesApp(this))
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        container = findViewById(R.id.containerPermisos)

        findViewById<MaterialButton>(R.id.btnPermContinuar).setOnClickListener {
            marcarVisto(this)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (item in Permisos.todos()) {
            val v = inflater.inflate(R.layout.item_permission, container, false)
            v.findViewById<ImageView>(R.id.ivPermIcon).setImageResource(item.icono)
            v.findViewById<TextView>(R.id.tvPermTitle).text = item.titulo
            v.findViewById<TextView>(R.id.tvPermDesc).text = item.descripcion

            val ok = v.findViewById<ImageView>(R.id.ivPermOk)
            val btn = v.findViewById<MaterialButton>(R.id.btnPermGrant)
            val estado = item.concedido(this)

            if (estado == true) {
                ok.visibility = View.VISIBLE
                btn.visibility = View.GONE
            } else {
                ok.visibility = View.GONE
                btn.visibility = View.VISIBLE
                btn.text = if (estado == null) "Abrir" else "Activar"
                btn.setOnClickListener { pedir(item) }
            }
            container.addView(v)
        }
    }

    private fun pedir(item: Permisos.Item) {
        when (item.tipo) {
            Permisos.Tipo.RUNTIME -> runtimeLauncher.launch(item.runtime)
            Permisos.Tipo.AJUSTE -> {
                val intent = item.ajuste?.invoke(this) ?: return
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    // Algunas marcas no tienen esa pantalla: abrimos la info de la app
                    startActivity(Permisos.intentAjustesApp(this))
                }
            }
        }
    }

    companion object {
        private const val KEY_VISTO = "onboarding_permisos_visto"

        fun yaVisto(c: Context) =
            c.getSharedPreferences(AlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_VISTO, false)

        fun marcarVisto(c: Context) =
            c.getSharedPreferences(AlarmScheduler.PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_VISTO, true).apply()
    }
}
