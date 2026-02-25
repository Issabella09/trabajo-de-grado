package com.trabajogrado.asistente

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsDetalladasAdapter(
    private val apps: MutableList<AppInfoDetallada>
) : RecyclerView.Adapter<AppsDetalladasAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.img_icon_detallada)
        val txtNombre: TextView = view.findViewById(R.id.txt_nombre_detallada)
        val txtPackage: TextView = view.findViewById(R.id.txt_package_detallada)
        val checkbox: CheckBox = view.findViewById(R.id.checkbox_detallada)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_detallada, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        holder.imgIcon.setImageDrawable(app.icon)
        holder.txtNombre.text = app.nombre
        holder.txtPackage.text = app.packageName
        holder.checkbox.isChecked = app.activada

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            app.activada = isChecked
        }

        holder.itemView.setOnClickListener {
            holder.checkbox.isChecked = !holder.checkbox.isChecked
        }
    }

    override fun getItemCount() = apps.size

    fun seleccionarTodas(seleccionar: Boolean) {
        apps.forEach { it.activada = seleccionar }
        notifyDataSetChanged()
    }

    fun obtenerApps() = apps
}