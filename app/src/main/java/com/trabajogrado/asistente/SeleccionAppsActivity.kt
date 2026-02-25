package com.trabajogrado.asistente

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SeleccionAppsActivity : AppCompatActivity() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerApps: RecyclerView
    private lateinit var btnGuardar: Button
    private lateinit var btnSeleccionarTodas: Button
    private lateinit var btnDeseleccionarTodas: Button

    private var todasLasApps = listOf<AppInfoDetallada>()
    private var appsAdapter: AppsDetalladasAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seleccion_apps)

        inicializarVistas()
        configurarListeners()
        cargarTodasLasApps()
    }

    private fun inicializarVistas() {
        searchView = findViewById(R.id.searchView)
        recyclerApps = findViewById(R.id.recycler_apps_seleccion)
        btnGuardar = findViewById(R.id.btn_guardar_apps)
        btnSeleccionarTodas = findViewById(R.id.btn_seleccionar_todas)
        btnDeseleccionarTodas = findViewById(R.id.btn_deseleccionar_todas)

        recyclerApps.layoutManager = LinearLayoutManager(this)
    }

    private fun configurarListeners() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filtrarApps(newText ?: "")
                return true
            }
        })

        btnSeleccionarTodas.setOnClickListener {
            seleccionarTodas(true)
        }

        btnDeseleccionarTodas.setOnClickListener {
            seleccionarTodas(false)
        }

        btnGuardar.setOnClickListener {
            guardarSeleccion()
            finish()
        }
    }

    private fun cargarTodasLasApps() {
        Thread {
            val apps = obtenerTodasLasAppsInstaladas()
            runOnUiThread {
                todasLasApps = apps
                mostrarApps(apps)
            }
        }.start()
    }

    private fun obtenerTodasLasAppsInstaladas(): List<AppInfoDetallada> {
        val apps = mutableListOf<AppInfoDetallada>()
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in installedApps) {
            try {
                // Solo apps que puedan mostrar notificaciones
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    esAppImportante(appInfo.packageName)) {

                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    val packageName = appInfo.packageName
                    val activada = obtenerEstadoApp(appName)

                    apps.add(AppInfoDetallada(appName, packageName, icon, activada))
                }
            } catch (e: Exception) {
                Log.e("SeleccionApps", "Error procesando app: ${e.message}")
            }
        }

        return apps.sortedBy { it.nombre.lowercase() }
    }

    private fun esAppImportante(packageName: String): Boolean {
        val appsImportantes = listOf(
            "com.whatsapp", "com.instagram.android", "com.facebook.katana",
            "com.google.android.gm", "com.android.messaging", "com.google.android.apps.messaging"
        )
        return appsImportantes.any { packageName.contains(it) }
    }

    private fun obtenerEstadoApp(nombreApp: String): Boolean {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        return sharedPref.getBoolean(nombreApp, true)
    }

    private fun mostrarApps(apps: List<AppInfoDetallada>) {
        appsAdapter = AppsDetalladasAdapter(apps.toMutableList())
        recyclerApps.adapter = appsAdapter
    }

    private fun filtrarApps(query: String) {
        val appsFiltradas = if (query.isEmpty()) {
            todasLasApps
        } else {
            todasLasApps.filter {
                it.nombre.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        mostrarApps(appsFiltradas)
    }

    private fun seleccionarTodas(seleccionar: Boolean) {
        appsAdapter?.seleccionarTodas(seleccionar)
    }

    private fun guardarSeleccion() {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        val editor = sharedPref.edit()

        appsAdapter?.obtenerApps()?.forEach { app ->
            editor.putBoolean(app.nombre, app.activada)
        }

        editor.apply()

        setResult(RESULT_OK)
    }
}