package com.trabajogrado.asistente

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsAdapter(
    private val apps: List<AppInfo>,
    private val onAppToggled: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtAppName: TextView = view.findViewById(R.id.txt_app_name)
        val checkboxApp: CheckBox = view.findViewById(R.id.checkbox_app)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        holder.txtAppName.text = app.nombre
        holder.checkboxApp.isChecked = app.activada

        holder.checkboxApp.setOnCheckedChangeListener { _, isChecked ->
            app.activada = isChecked
            onAppToggled(app, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.checkboxApp.isChecked = !holder.checkboxApp.isChecked
        }
    }

    override fun getItemCount() = apps.size
}