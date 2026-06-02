package com.trabajogrado.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotificacionesActivity : AppCompatActivity() {

    private lateinit var switchLecturaAuto: Switch
    private lateinit var btnConfigurarAccesibilidad: Button
    private lateinit var recyclerApps: RecyclerView
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button
    private lateinit var btnConfigurarPrefijo: Button

    companion object {
        private const val REQUEST_CONTACTS = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificaciones)

        inicializarVistas()
        configurarListeners()
        cargarConfiguracion()
        cargarListaApps()
        solicitarPermisoContactos()
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoServicio()
    }

    private fun inicializarVistas() {
        switchLecturaAuto = findViewById(R.id.switch_lectura_auto)
        btnConfigurarAccesibilidad = findViewById(R.id.btn_configurar_accesibilidad)
        recyclerApps = findViewById(R.id.recycler_apps)
        txtEstado = findViewById(R.id.txt_estado)
        btnVolver = findViewById(R.id.btn_volver)
        btnConfigurarPrefijo = findViewById(R.id.btn_configurar_prefijo)

        recyclerApps.layoutManager = LinearLayoutManager(this)
    }

    private fun configurarListeners() {
        switchLecturaAuto.setOnCheckedChangeListener { _, isChecked ->
            guardarConfiguracionLectura(isChecked)
            actualizarEstadoServicio()
        }

        btnConfigurarAccesibilidad.setOnClickListener {
            abrirConfiguracionNotificaciones()
        }

        btnVolver.setOnClickListener { finish() }

        btnConfigurarPrefijo.setOnClickListener {
            startActivity(Intent(this, ConfiguracionPrefijoActivity::class.java))
        }
    }

    private fun solicitarPermisoContactos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_CONTACTS
            )
        }
    }

    private fun guardarConfiguracionLectura(activado: Boolean) {
        getSharedPreferences("config_notificaciones", MODE_PRIVATE)
            .edit().putBoolean("lectura_automatica", activado).apply()
        Toast.makeText(
            this,
            if (activado) "Lectura automática activada" else "Lectura automática pausada",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cargarConfiguracion() {
        val lecturaAuto = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
            .getBoolean("lectura_automatica", false)
        switchLecturaAuto.isChecked = lecturaAuto
    }

    // CORRECCIÓN 3: carga todas las apps instaladas con ícono real
    private fun cargarListaApps() {
        try {
            val apps = obtenerAppsInstaladas()
            Log.d("Notificaciones", "Apps encontradas: ${apps.size}")
            recyclerApps.adapter = AppsAdapter(apps) { app, activada ->
                guardarEstadoApp(app, activada)
            }
        } catch (e: Exception) {
            Log.e("Notificaciones", "Error cargando apps: ${e.message}", e)
            Toast.makeText(this, "Error cargando apps", Toast.LENGTH_SHORT).show()
        }
    }

    // Devuelve todas las apps que tienen icono de lanzador (apps de usuario), con ícono real.
    // Usa packageName como clave — no depende de traducciones ni mapeos de nombres.
    private fun obtenerAppsInstaladas(): List<AppInfo> {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return pm.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != packageName }   // excluir esta app
            .distinctBy { it.activityInfo.packageName }              // una entrada por app
            .map { info ->
                val pkg = info.activityInfo.packageName
                val nombre = info.loadLabel(pm).toString()
                val icono = try { info.loadIcon(pm) } catch (e: Exception) { null }
                AppInfo(pkg, nombre, obtenerEstadoApp(pkg), icono)
            }
            .sortedBy { it.nombre.lowercase() }
            .toList()
    }

    private fun obtenerEstadoApp(packageName: String): Boolean {
        return getSharedPreferences("apps_seleccionadas", MODE_PRIVATE)
            .getBoolean(packageName, true)   // por defecto: habilitada
    }

    private fun guardarEstadoApp(app: AppInfo, activada: Boolean) {
        getSharedPreferences("apps_seleccionadas", MODE_PRIVATE)
            .edit().putBoolean(app.packageName, activada).apply()
        Log.d("Notificaciones", "${app.nombre} (${app.packageName}): $activada")
    }

    private fun actualizarEstadoServicio() {
        if (isServicioNotificacionesActivado()) {
            txtEstado.text = if (switchLecturaAuto.isChecked)
                "Lectura automática ACTIVADA"
            else
                "Lectura automática PAUSADA"
        } else {
            txtEstado.text = "Permiso de notificaciones no concedido"
        }
    }

    private fun isServicioNotificacionesActivado(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        return enabledListeners?.contains(packageName) == true
    }

    private fun abrirConfiguracionNotificaciones() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(
                this,
                "Busca 'EVA' y activa el acceso a notificaciones",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error abriendo configuración", Toast.LENGTH_SHORT).show()
        }
    }
}
