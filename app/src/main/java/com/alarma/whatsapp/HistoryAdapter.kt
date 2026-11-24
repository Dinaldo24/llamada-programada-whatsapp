package com.alarma.whatsapp

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(private val items: List<RegistroLlamada>) :
    RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.ivStatus)
        val title: TextView = v.findViewById(R.id.tvHistTitle)
        val whenTv: TextView = v.findViewById(R.id.tvHistWhen)
        val status: TextView = v.findViewById(R.id.tvHistStatus)
        val detail: TextView = v.findViewById(R.id.tvHistDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        val ctx = h.itemView.context
        val quien = r.nombre.ifBlank { r.numero }
        h.title.text = "$quien · ${if (r.tipo == "PHONE") "Línea" else "WhatsApp"}"
        h.whenTv.text = fmt.format(Date(r.timestamp))
        h.detail.text = r.detalle

        val fg = ContextCompat.getColor(ctx, if (r.exito) R.color.ok_fg else R.color.err_fg)
        val bg = ContextCompat.getColor(ctx, if (r.exito) R.color.ok_bg else R.color.err_bg)
        h.status.text = if (r.exito) "Llamada iniciada" else "No se pudo llamar"
        h.status.setTextColor(fg)
        h.icon.setImageResource(if (r.exito) R.drawable.ic_check_circle else R.drawable.ic_warning)
        h.icon.imageTintList = ColorStateList.valueOf(fg)
        h.icon.backgroundTintList = ColorStateList.valueOf(bg)
    }

    override fun getItemCount() = items.size
}
