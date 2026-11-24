package com.alarma.whatsapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class AlarmsAdapter(
    private val alarms: List<AlarmaModel>,
    private val onToggle: (AlarmaModel, Boolean) -> Unit,
    private val onEditTime: (AlarmaModel) -> Unit,
    private val onDelete: (AlarmaModel) -> Unit,
    private val onTest: (AlarmaModel) -> Unit
) : RecyclerView.Adapter<AlarmsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvTime: TextView = v.findViewById(R.id.tvAlarmTime)
        val tvAmPm: TextView = v.findViewById(R.id.tvAlarmAmPm)
        val tvAvatar: TextView = v.findViewById(R.id.tvAvatar)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvNumber: TextView = v.findViewById(R.id.tvNumber)
        val tvType: TextView = v.findViewById(R.id.tvType)
        val tvCountdown: TextView = v.findViewById(R.id.tvCountdown)
        val switch: MaterialSwitch = v.findViewById(R.id.switchAlarm)
        val timeClick: View = v.findViewById(R.id.layoutTimeClick)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDeleteAlarm)
        val btnTest: ImageButton = v.findViewById(R.id.btnTestSingleAlarm)
        val dimmable: List<View> = listOf(tvTime, tvAmPm, tvAvatar, tvName, tvNumber, tvType, tvCountdown)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val a = alarms[position]

        h.tvTime.text = String.format(Locale.getDefault(), "%d:%02d", hora12(a.hora), a.minuto)
        h.tvAmPm.text = if (a.hora >= 12) "p. m." else "a. m."

        val tieneNombre = a.nombreContacto.isNotBlank()
        h.tvName.text = if (tieneNombre) a.nombreContacto else a.numero
        h.tvNumber.text = a.numero
        h.tvNumber.visibility = if (tieneNombre) View.VISIBLE else View.GONE
        h.tvAvatar.text = if (tieneNombre) a.nombreContacto.trim().take(1).uppercase() else "#"

        val esWa = a.tipoLlamada != "PHONE"
        h.tvType.text = if (esWa) "WhatsApp" else "Línea"
        ContextCompat.getDrawable(h.itemView.context, if (esWa) R.drawable.ic_chat else R.drawable.ic_phone)
            ?.mutate()?.let {
                val size = (14 * h.itemView.resources.displayMetrics.density).toInt()
                it.setBounds(0, 0, size, size)
                it.setTint(h.tvType.currentTextColor)
                h.tvType.setCompoundDrawablesRelative(it, null, null, null)
            }

        h.tvCountdown.text = if (a.activa) {
            "Todos los días · " + AlarmScheduler.textoTiempoRestante(
                AlarmScheduler.proximaEjecucion(a) - System.currentTimeMillis()
            )
        } else "Desactivada"

        h.switch.setOnCheckedChangeListener(null)
        h.switch.isChecked = a.activa
        aplicarAlpha(h, a.activa)
        h.switch.setOnCheckedChangeListener { _, checked -> onToggle(a, checked) }

        h.timeClick.setOnClickListener { onEditTime(a) }
        h.btnDelete.setOnClickListener { onDelete(a) }
        h.btnTest.setOnClickListener { onTest(a) }
    }

    private fun aplicarAlpha(h: VH, activa: Boolean) {
        val alpha = if (activa) 1f else 0.45f
        h.dimmable.forEach { it.alpha = alpha }
    }

    override fun getItemCount() = alarms.size

    companion object {
        fun hora12(h: Int) = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
    }
}
