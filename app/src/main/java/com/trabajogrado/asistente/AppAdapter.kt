package com.trabajogrado.asistente

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppsAdapter(

    private var appsList: List<AppInfo>,
    private val onAppToggle: ((AppInfo, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtNombreApp: TextView = itemView.findViewById(R.id.txt_nombre_app)
        val switchApp: Switch = itemView.findViewById(R.id.switch_app)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appsList[position]

        holder.txtNombreApp.text = app.nombre
        holder.switchApp.isChecked = app.activada

        holder.switchApp.setOnCheckedChangeListener(null)

        holder.switchApp.setOnCheckedChangeListener { _, isChecked ->
            app.activada = isChecked
            guardarPreferenciaApp(holder.itemView.context, app.nombre, isChecked)
            onAppToggle?.invoke(app, isChecked)
        }
    }

    override fun getItemCount(): Int = appsList.size

    private fun guardarPreferenciaApp(context: Context, nombreApp: String, activada: Boolean) {
        val sharedPref = context.getSharedPreferences("apps_permitidas", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(nombreApp, activada)
            apply()
        }
    }

    fun actualizarLista(nuevaLista: List<AppInfo>) {
        appsList = nuevaLista
        notifyDataSetChanged()
    }
}

data class AppInfo(
    val nombre: String,
    var activada: Boolean
)