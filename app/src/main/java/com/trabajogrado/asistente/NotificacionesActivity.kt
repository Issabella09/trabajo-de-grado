package com.trabajogrado.asistente

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.pm.PackageManager

class NotificacionesActivity : AppCompatActivity() {

    private lateinit var switchLecturaAuto: Switch
    private lateinit var btnConfigurarAccesibilidad: Button
    private lateinit var recyclerApps: RecyclerView
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button
    private lateinit var btnSeleccionarApps: Button
    private lateinit var btnConfigurarPrefijo: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificaciones)

        inicializarVistas()
        configurarListeners()
        cargarConfiguracion()
        cargarListaApps()
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
        btnSeleccionarApps = findViewById(R.id.btn_seleccionar_apps)
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

        btnVolver.setOnClickListener {
            finish()
        }

        btnSeleccionarApps.setOnClickListener {
            val intent = Intent(this, SeleccionAppsActivity::class.java)
            startActivity(intent)
        }

        btnConfigurarPrefijo.setOnClickListener {
            val intent = Intent(this, ConfiguracionPrefijoActivity::class.java)
            startActivity(intent)
        }
    }

    private fun guardarConfiguracionLectura(activado: Boolean) {
        val sharedPref = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("lectura_automatica", activado)
            apply()
        }

        Toast.makeText(
            this,
            if (activado) "✅ Lectura automática activada" else "⏸️ Lectura automática pausada",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun cargarConfiguracion() {
        val sharedPref = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
        val lecturaAuto = sharedPref.getBoolean("lectura_automatica", false)
        switchLecturaAuto.isChecked = lecturaAuto
    }

    private fun cargarListaApps() {
        try {
            val appsInstaladas = obtenerAppsInstaladas()
            Log.d("Notificaciones", "📱 Apps encontradas: ${appsInstaladas.size}")

            val adapter = AppsAdapter(appsInstaladas) { app, activada ->
                guardarEstadoApp(app.nombre, activada)
                actualizarContador()
            }

            recyclerApps.adapter = adapter
            actualizarContador()

        } catch (e: Exception) {
            Log.e("Notificaciones", "❌ Error cargando apps: ${e.message}", e)
            Toast.makeText(this, "Error cargando apps", Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerAppsInstaladas(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val packageManager = packageManager

        // Mapeo de paquetes a nombres amigables
        val appsConocidas = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.facebook.orca" to "Messenger",
            "org.telegram.messenger" to "Telegram",
            "com.twitter.android" to "Twitter",
            "com.google.android.gm" to "Gmail",
            "com.android.messaging" to "Mensajes",
            "com.android.mms" to "Mensajes",
            "com.google.android.apps.messaging" to "Mensajes",
            "com.discord" to "Discord",
            "com.skype.raider" to "Skype",
            "com.viber.voip" to "Viber",
            "com.snapchat.android" to "Snapchat",
            "com.microsoft.teams" to "Microsoft Teams",
            "org.thoughtcrime.securesms" to "Signal",
            "com.tencent.mm" to "WeChat",
            "jp.naver.line.android" to "LINE",
            "com.linkedin.android" to "LinkedIn",
            "com.slack" to "Slack",
            "com.google.android.apps.inbox" to "Inbox",
            "com.microsoft.office.outlook" to "Outlook",
            "com.yahoo.mobile.client.android.mail" to "Yahoo Mail",
            "com.tiktokv.lite" to "TikTok",
            "com.zhiliaoapp.musically" to "TikTok"
        )

        // Buscar solo apps conocidas que estén instaladas
        appsConocidas.forEach { (packageName, appName) ->
            if (estaAppInstalada(packageName)) {
                apps.add(AppInfo(appName, obtenerEstadoApp(appName)))
                Log.d("Notificaciones", "✅ Encontrada: $appName")
            }
        }

        // Buscar otras apps de mensajería instaladas
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        try {
            val allApps = packageManager.queryIntentActivities(intent, 0)

            for (info in allApps) {
                val packageName = info.activityInfo.packageName
                val appName = info.loadLabel(packageManager).toString()

                // Solo agregar si parece ser una app de comunicación
                if (esAppDeComunicacion(packageName, appName) &&
                    !apps.any { it.nombre.equals(appName, ignoreCase = true) }) {
                    apps.add(AppInfo(appName, obtenerEstadoApp(appName)))
                    Log.d("Notificaciones", "✅ Encontrada (extra): $appName")
                }
            }
        } catch (e: Exception) {
            Log.e("Notificaciones", "Error buscando apps adicionales: ${e.message}")
        }

        // Ordenar alfabéticamente
        return apps.sortedBy { it.nombre }
    }

    private fun esAppDeComunicacion(packageName: String, appName: String): Boolean {
        val palabrasClave = listOf(
            "chat", "messenger", "message", "mail", "sms", "whats",
            "telegram", "signal", "discord", "slack", "teams"
        )

        return palabrasClave.any {
            packageName.contains(it, ignoreCase = true) ||
                    appName.contains(it, ignoreCase = true)
        }
    }

    private fun estaAppInstalada(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun obtenerEstadoApp(nombreApp: String): Boolean {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        return sharedPref.getBoolean(nombreApp, true)
    }

    private fun guardarEstadoApp(nombreApp: String, activada: Boolean) {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(nombreApp, activada)
            apply()
        }
        Log.d("Notificaciones", "💾 $nombreApp: $activada")
    }

    private fun actualizarContador() {
        val totalApps = recyclerApps.adapter?.itemCount ?: 0
        val appsActivas = obtenerCantidadAppsActivas()

        Log.d("Notificaciones", "📊 Apps activas: $appsActivas/$totalApps")
    }

    private fun obtenerCantidadAppsActivas(): Int {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        return sharedPref.all.count { it.value == true }
    }

    private fun actualizarEstadoServicio() {
        if (isServicioNotificacionesActivado()) {
            txtEstado.text = if (switchLecturaAuto.isChecked) {
                "✅ Lectura automática ACTIVADA"
            } else {
                "⏸️ Lectura automática PAUSADA"
            }
        } else {
            txtEstado.text = "❌ Permiso de notificaciones no concedido"
        }
    }

    private fun isServicioNotificacionesActivado(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = packageName
        return enabledListeners?.contains(packageName) == true
    }

    private fun abrirConfiguracionNotificaciones() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Busca 'EVA' y activa el acceso a notificaciones",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error abriendo configuración",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}