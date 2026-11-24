package com.alarma.whatsapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var rvAlarms: RecyclerView
    private lateinit var rvHistory: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var tabs: TabLayout
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var cardPermissionWarning: View

    private lateinit var alarmsAdapter: AlarmsAdapter
    private val alarmas = mutableListOf<AlarmaModel>()
    private val historial = mutableListOf<RegistroLlamada>()

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            actualizarProxima()
            alarmsAdapter.notifyItemRangeChanged(0, alarmas.size)
            handler.postDelayed(this, 30_000)
        }
    }

    private var onContactPicked: ((numero: String, nombre: String) -> Unit)? = null

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ), null, null, null
        )?.use {
            if (it.moveToFirst()) onContactPicked?.invoke(it.getString(0).orEmpty(), it.getString(1).orEmpty())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvAlarms = findViewById(R.id.rvAlarms)
        rvHistory = findViewById(R.id.rvHistory)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tabs = findViewById(R.id.tabs)
        fab = findViewById(R.id.fabAddAlarm)
        cardPermissionWarning = findViewById(R.id.cardPermissionWarning)

        val abrirPermisos = View.OnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        cardPermissionWarning.setOnClickListener(abrirPermisos)
        findViewById<ImageButton>(R.id.btnPermisos).setOnClickListener(abrirPermisos)

        configurarListas()
        configurarTabs()
        fab.setOnClickListener { mostrarHojaNuevaLlamada() }

        // Primera vez: mostrar la pantalla de permisos
        if (!PermissionsActivity.yaVisto(this)) {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        recargar()
        val faltan = Permisos.faltantes(this).size
        cardPermissionWarning.visibility = if (faltan > 0) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvPermissionTitle).text =
            if (faltan == 1) "Falta 1 permiso" else "Faltan $faltan permisos"
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ---------------------------------------------------------------- listas

    private fun configurarListas() {
        alarmsAdapter = AlarmsAdapter(
            alarms = alarmas,
            onToggle = { a, checked ->
                a.activa = checked
                AlarmScheduler.guardarLista(this, alarmas)
                if (checked) {
                    aviso("Activada: sonará ${AlarmScheduler.programarAlarma(this, a)}")
                } else {
                    AlarmScheduler.cancelarAlarma(this, a.id)
                    aviso("Llamada desactivada")
                }
                refrescarAlarmas()
            },
            onEditTime = { a ->
                elegirHora(a.hora, a.minuto) { h, m ->
                    a.hora = h; a.minuto = m; a.activa = true
                    AlarmScheduler.guardarLista(this, alarmas)
                    aviso("Hora cambiada: sonará ${AlarmScheduler.programarAlarma(this, a)}")
                    recargar()
                }
            },
            onDelete = { a ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("¿Eliminar esta llamada?")
                    .setMessage("${a.nombreContacto.ifBlank { a.numero }} · ${formatearHora(a.hora, a.minuto)}")
                    .setPositiveButton("Eliminar") { _, _ ->
                        AlarmScheduler.cancelarAlarma(this, a.id)
                        alarmas.remove(a)
                        AlarmScheduler.guardarLista(this, alarmas)
                        refrescarAlarmas()
                        aviso("Llamada eliminada")
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            },
            onTest = { a ->
                val r = if (a.tipoLlamada == "PHONE") {
                    WhatsAppCallHelper.realizarLlamadaTelefonica(this, a.numero)
                } else {
                    WhatsAppCallHelper.realizarLlamadaWhatsApp(this, a.numero)
                }
                HistorialStore.agregar(
                    this, RegistroLlamada(
                        System.currentTimeMillis(), a.nombreContacto, a.numero, a.tipoLlamada,
                        r.exito, "Prueba manual: ${r.detalle}"
                    )
                )
                if (!r.exito) Toast.makeText(this, r.detalle, Toast.LENGTH_LONG).show()
            }
        )
        rvAlarms.layoutManager = LinearLayoutManager(this)
        rvAlarms.adapter = alarmsAdapter
        rvAlarms.itemAnimator?.changeDuration = 0

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = HistoryAdapter(historial)
    }

    private fun configurarTabs() {
        tabs.addTab(tabs.newTab().setText("Programadas").setIcon(R.drawable.ic_alarm))
        tabs.addTab(tabs.newTab().setText("Historial").setIcon(R.drawable.ic_history))
        tabs.tabIconTint = tabs.tabTextColors
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = mostrarTab()
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == 1 && historial.isNotEmpty()) confirmarBorrarHistorial()
            }
        })
    }

    private fun recargar() {
        alarmas.clear()
        alarmas.addAll(AlarmScheduler.cargarAlarmas(this))
        historial.clear()
        historial.addAll(HistorialStore.cargar(this))
        tabs.getTabAt(1)?.text = if (historial.isEmpty()) "Historial" else "Historial (${historial.size})"
        refrescarAlarmas()
        rvHistory.adapter?.notifyDataSetChanged()
        mostrarTab()
    }

    private fun refrescarAlarmas() {
        alarmas.sortWith(compareBy({ it.hora }, { it.minuto }))
        alarmsAdapter.notifyDataSetChanged()
        val activas = alarmas.count { it.activa }
        findViewById<TextView>(R.id.tvSubtitle).text = when {
            alarmas.isEmpty() -> "Se llaman solas a la hora que elijas"
            else -> "$activas activa${if (activas == 1) "" else "s"} de ${alarmas.size}"
        }
        actualizarProxima()
        mostrarTab()
    }

    private fun mostrarTab() {
        val enHistorial = tabs.selectedTabPosition == 1
        val vacia = if (enHistorial) historial.isEmpty() else alarmas.isEmpty()
        rvAlarms.visibility = if (!enHistorial && !vacia) View.VISIBLE else View.GONE
        rvHistory.visibility = if (enHistorial && !vacia) View.VISIBLE else View.GONE
        layoutEmpty.visibility = if (vacia) View.VISIBLE else View.GONE
        if (enHistorial) fab.shrink() else fab.extend()

        if (vacia) {
            findViewById<ImageView>(R.id.ivEmpty).setImageResource(if (enHistorial) R.drawable.ic_history else R.drawable.ic_alarm)
            findViewById<TextView>(R.id.tvEmptyTitle).text = if (enHistorial) "Sin llamadas todavía" else "Aún no hay llamadas"
            findViewById<TextView>(R.id.tvEmptyBody).text = if (enHistorial)
                "Aquí verás cada llamada que la app hizo y, si falló, el motivo exacto."
            else "Toca «Nueva llamada», elige un contacto y una hora."
        }
    }

    private fun actualizarProxima() {
        val ahora = System.currentTimeMillis()
        val proxima = alarmas.filter { it.activa }.minByOrNull { AlarmScheduler.proximaEjecucion(it, ahora) }
        val tvTime = findViewById<TextView>(R.id.tvNextTime)
        val tvAmPm = findViewById<TextView>(R.id.tvNextAmPm)
        val tvWho = findViewById<TextView>(R.id.tvNextWho)
        val tvCount = findViewById<TextView>(R.id.tvNextCountdown)
        if (proxima == null) {
            tvTime.text = "--:--"
            tvAmPm.text = ""
            tvWho.text = "No hay llamadas activas"
            tvCount.text = "Toca «Nueva llamada» para programar una"
            return
        }
        tvTime.text = String.format(Locale.getDefault(), "%d:%02d", AlarmsAdapter.hora12(proxima.hora), proxima.minuto)
        tvAmPm.text = if (proxima.hora >= 12) "p. m." else "a. m."
        val tipo = if (proxima.tipoLlamada == "PHONE") "línea normal" else "WhatsApp"
        tvWho.text = "${proxima.nombreContacto.ifBlank { proxima.numero }} · $tipo"
        tvCount.text = "Sonará " + AlarmScheduler.textoTiempoRestante(AlarmScheduler.proximaEjecucion(proxima, ahora) - ahora)
    }

    private fun confirmarBorrarHistorial() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Borrar historial?")
            .setPositiveButton("Borrar") { _, _ -> HistorialStore.limpiar(this); recargar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------------- nueva llamada

    private fun mostrarHojaNuevaLlamada() {
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(R.layout.sheet_add_alarm)
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true

        val btnTime = sheet.findViewById<TextView>(R.id.btnSheetTime)!!
        val til = sheet.findViewById<TextInputLayout>(R.id.tilSheetPhone)!!
        val etPhone = sheet.findViewById<TextInputEditText>(R.id.etSheetPhone)!!
        val tvContact = sheet.findViewById<TextView>(R.id.tvSheetContactName)!!
        val toggle = sheet.findViewById<MaterialButtonToggleGroup>(R.id.toggleSheetType)!!
        val tvHint = sheet.findViewById<TextView>(R.id.tvSheetHint)!!

        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 5) }
        var hora = cal.get(java.util.Calendar.HOUR_OF_DAY)
        var minuto = cal.get(java.util.Calendar.MINUTE)
        var nombre = ""
        btnTime.text = formatearHora(hora, minuto)

        btnTime.setOnClickListener {
            elegirHora(hora, minuto) { h, m -> hora = h; minuto = m; btnTime.text = formatearHora(h, m) }
        }

        var numeroDelContacto = ""
        onContactPicked = { numero, n ->
            numeroDelContacto = numero
            nombre = n
            etPhone.setText(numero)
            tvContact.text = "✓ $n"
            tvContact.visibility = View.VISIBLE
            til.error = null
        }
        sheet.setOnDismissListener { onContactPicked = null }

        // Si el usuario edita el número a mano, deja de estar ligado al contacto
        etPhone.doAfterTextChanged { txt ->
            if (nombre.isNotEmpty() && txt?.toString() != numeroDelContacto) {
                nombre = ""
                tvContact.visibility = View.GONE
            }
        }

        sheet.findViewById<MaterialButton>(R.id.btnSheetPickContact)!!.setOnClickListener {
            // ACTION_PICK concede acceso temporal al contacto elegido: no necesita permiso
            pickContactLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }

        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            tvHint.text = if (id == R.id.btnTypeWhatsApp)
                "Para WhatsApp el número debe estar guardado en tus contactos."
            else "Se llamará desde tu chip, con saldo/minutos normales."
        }

        sheet.findViewById<MaterialButton>(R.id.btnSheetSave)!!.setOnClickListener {
            val numero = etPhone.text?.toString()?.trim().orEmpty()
            if (numero.replace(Regex("[^0-9]"), "").length < 7) {
                til.error = "Elige un contacto o escribe un número válido"
                return@setOnClickListener
            }
            val nueva = AlarmaModel(
                id = AlarmScheduler.generarNuevoId(this),
                hora = hora, minuto = minuto,
                numero = numero, nombreContacto = nombre,
                tipoLlamada = if (toggle.checkedButtonId == R.id.btnTypePhone) "PHONE" else "WHATSAPP",
                activa = true
            )
            alarmas.add(nueva)
            AlarmScheduler.guardarLista(this, alarmas)
            val tiempo = AlarmScheduler.programarAlarma(this, nueva)
            sheet.dismiss()
            tabs.getTabAt(0)?.select()
            refrescarAlarmas()
            aviso("Llamada programada: sonará $tiempo")
        }

        sheet.show()
    }

    // ---------------------------------------------------------------- utilidades

    private fun elegirHora(h: Int, m: Int, onOk: (Int, Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(h).setMinute(m)
            .setTitleText("Hora de la llamada")
            .build()
        picker.addOnPositiveButtonClickListener { onOk(picker.hour, picker.minute) }
        picker.show(supportFragmentManager, "hora")
    }

    private fun aviso(msg: String) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_LONG)
            .setAnchorView(fab)
            .show()
    }

    private fun formatearHora(hora: Int, minuto: Int) = String.format(
        Locale.getDefault(), "%d:%02d %s", AlarmsAdapter.hora12(hora), minuto, if (hora >= 12) "p. m." else "a. m."
    )
}
