package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.ComponentName
import android.widget.Toast
import android.util.Log
import android.content.pm.PackageManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlin.Int
import kotlin.String

class NotificacionesActivity : AppCompatActivity() {
    private val TAG = "NotificacionesActivity"

    private lateinit var switchLecturaAuto: Switch
    private lateinit var btnConfigurarAccesibilidad: Button
    private lateinit var recyclerApps: RecyclerView
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificaciones)

        try {
            inicializarVistas()
            verificarPermisoNotificaciones()
            configurarListeners()
            cargarConfiguracion()
            cargarListaApps()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun inicializarVistas() {
        Log.d("Notificaciones", "🔍 Buscando vistas...")

        switchLecturaAuto = findViewById(R.id.switch_lectura_auto)
        Log.d("Notificaciones", "   switchLecturaAuto: ${switchLecturaAuto != null}")

        btnConfigurarAccesibilidad = findViewById(R.id.btn_configurar_accesibilidad)
        Log.d("Notificaciones", "   btnConfigurarAccesibilidad: ${btnConfigurarAccesibilidad != null}")

        recyclerApps = findViewById(R.id.recycler_apps)
        Log.d("Notificaciones", "   recyclerApps: ${recyclerApps != null}")

        txtEstado = findViewById(R.id.txt_estado)
        Log.d("Notificaciones", "   txtEstado: ${txtEstado != null}")

        btnVolver = findViewById(R.id.btn_volver)
        Log.d("Notificaciones", "   btnVolver: ${btnVolver != null}")

        // Configurar RecyclerView para apps
        recyclerApps.layoutManager = LinearLayoutManager(this)
        Log.d("Notificaciones", "✅ LayoutManager configurado")
    }

    private fun configurarListeners() {
        switchLecturaAuto.setOnCheckedChangeListener { _, isChecked ->
            guardarConfiguracionLectura(isChecked)
            actualizarEstadoServicio()
            notificarServicioConfiguracionCambiada()

            val mensaje = if (isChecked) "✅ Notificaciones ACTIVADAS" else "🔕 Notificaciones DESACTIVADAS"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()

            // Log para debugging
            Log.d(TAG, "📤 Configuración guardada y servicio notificado: lectura_automatica = $isChecked")
        }

        btnConfigurarAccesibilidad.setOnClickListener {
            abrirConfiguracionAccesibilidad()
        }
        btnVolver.setOnClickListener {
            finish()
        }
    }

    private fun guardarConfiguracionLectura(activado: Boolean) {
        val sharedPref = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("lectura_automatica", activado)
            apply()
        }
    }

    private fun notificarServicioConfiguracionCambiada() {
        // Notificar al servicio que la configuración cambió
        LecturaPantallaService.actualizarConfiguracionNotificaciones()
    }

    private fun cargarConfiguracion() {
        val sharedPref = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
        val lecturaAuto = sharedPref.getBoolean("lectura_automatica", false)
        switchLecturaAuto.isChecked = lecturaAuto

        actualizarEstadoServicio()
    }

    private fun cargarListaApps() {
        Log.d("Notificaciones", "🔄 INICIANDO cargarListaApps...")

        try {
            Log.d("Notificaciones", "🔍 Obteniendo apps instaladas...")
            val appsInstaladas = obtenerAppsInstaladas()
            Log.d("Notificaciones", "📱 Apps obtenidas: ${appsInstaladas.size}")

            // DEBUG: Mostrar cada app encontrada
            if (appsInstaladas.isEmpty()) {
                Log.d("Notificaciones", "❌ LISTA VACÍA - No se encontraron apps")
            } else {
                appsInstaladas.forEachIndexed { index, app ->
                    Log.d("Notificaciones", "   ${index + 1}. ${app.nombre} (activada: ${app.activada})")
                }
            }

            Log.d("Notificaciones", "🔄 Configurando adapter...")
            val adapter = AppsAdapter(appsInstaladas) { app, activada ->
                Log.d("Notificaciones", "🎚️ Switch cambiado: ${app.nombre} = $activada")
                Toast.makeText(this, "${app.nombre} ${if (activada) "activada" else "desactivada"}",
                    Toast.LENGTH_SHORT).show()
                notificarServicioConfiguracionCambiada()
                actualizarContadorApps()
            }

            Log.d("Notificaciones", "🔄 Asignando adapter al RecyclerView...")
            recyclerApps.adapter = adapter
            Log.d("Notificaciones", "✅ Adapter asignado")

            actualizarContadorApps()
            Log.d("Notificaciones", "✅ Contador actualizado")

        } catch (e: Exception) {
            Log.e("Notificaciones", "❌ ERROR en cargarListaApps: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Error cargando apps: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun verificarPermisoNotificaciones() {
        try {
            val enabledNotificationListeners = Settings.Secure.getString(
                contentResolver,
                "enabled_notification_listeners"
            )

            val service = ComponentName(this, LecturaPantallaService::class.java)
            val hasPermission = enabledNotificationListeners?.contains(service.flattenToString()) ?: false

            if (!hasPermission) {
                // Verificar si YA hemos mostrado el mensaje en esta sesión
                val prefs = getSharedPreferences("permisos_notificaciones", MODE_PRIVATE)
                val yaMostrado = prefs.getBoolean("mensaje_mostrado", false)

                if (!yaMostrado) {
                    // Mostrar mensaje solo la primera vez
                    Toast.makeText(this,
                        "Para leer notificaciones, activa el permiso en ajustes",
                        Toast.LENGTH_LONG
                    ).show()

                    // Marcar que ya mostramos el mensaje
                    prefs.edit().putBoolean("mensaje_mostrado", true).apply()

                    // Opcional: abrir ajustes automáticamente solo la primera vez
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    startActivity(intent)
                } else {
                    // Ya mostramos el mensaje antes, solo log
                    Log.d(TAG, "✅ Permiso de notificaciones ya solicitado anteriormente")
                }
            } else {
                Log.d(TAG, "✅ Permiso de notificaciones YA ACTIVADO")
                // No mostrar Toast para no molestar
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando permiso: ${e.message}")
        }
    }


    private fun obtenerAppsInstaladas(): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()

        try {
            // PRIMERO: Agregar apps principales que SIEMPRE queremos mostrar
            val appsPrincipales = listOf(
                "WhatsApp", "Instagram", "Facebook", "Telegram",
                "Twitter", "Mensajes", "Gmail", "Llamadas"
            )

            appsPrincipales.forEach { nombreApp ->
                apps.add(AppInfo(nombreApp, obtenerEstadoApp(nombreApp)))
            }

            // SEGUNDO: Buscar apps instaladas específicas por paquete
            val appsEspecificas = mapOf(
                "com.whatsapp" to "WhatsApp",
                "com.instagram.android" to "Instagram",
                "com.facebook.katana" to "Facebook",
                "org.telegram.messenger" to "Telegram",
                "com.twitter.android" to "Twitter",
                "com.android.messaging" to "Mensajes",
                "com.google.android.gm" to "Gmail",
                "com.android.dialer" to "Llamadas",
                "com.discord" to "Discord",
                "com.skype.raider" to "Skype",
                "com.viber.voip" to "Viber",
                "com.snapchat.android" to "Snapchat",
                "com.microsoft.teams" to "Microsoft Teams",
                "com.signal" to "Signal"
            )

            val packageManager = packageManager
            appsEspecificas.forEach { (packageName, appName) ->
                if (estaAppInstalada(packageName)) {
                    // Evitar duplicados
                    if (!apps.any { it.nombre == appName }) {
                        apps.add(AppInfo(appName, obtenerEstadoApp(appName)))
                    }
                }
            }

            // TERCERO: Buscar más apps instaladas (opcional)
            buscarOtrasAppsInstaladas(apps)

            // Ordenar alfabéticamente
            apps.sortBy { it.nombre }

            Log.d("Notificaciones", "Apps encontradas: ${apps.size}")

        } catch (e: Exception) {
            Log.e("Notificaciones", "Error obteniendo apps instaladas: ${e.message}")
        }

        return apps
    }

    private fun estaAppInstalada(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun buscarOtrasAppsInstaladas(appsList: MutableList<AppInfo>) {
        try {
            val packageManager = packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolvedInfos = packageManager.queryIntentActivities(intent, 0)

            // Apps del sistema que NO queremos mostrar
            val appsExcluidas = listOf(
                "settings", "camera", "calculator", "calendar", "gallery",
                "email", "maps", "chrome", "photos", "clock", "contacts"
            )

            for (info in resolvedInfos) {
                val packageName = info.activityInfo.packageName
                val appName = info.loadLabel(packageManager).toString()

                // Solo agregar si no es una app excluida y no está ya en la lista
                val debeExcluir = appsExcluidas.any {
                    packageName.contains(it, ignoreCase = true) ||
                            appName.contains(it, ignoreCase = true)
                }

                if (!debeExcluir && !appsList.any { it.nombre.equals(appName, ignoreCase = true) }) {
                    // Limitar a apps populares (evitar apps del sistema raras)
                    if (esAppPopular(packageName) || appName.length in 2..30) {
                        appsList.add(AppInfo(appName, obtenerEstadoApp(appName)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Notificaciones", "Error buscando otras apps: ${e.message}")
        }
    }

    private fun esAppPopular(packageName: String): Boolean {
        val appsPopulares = listOf(
            "whatsapp", "instagram", "facebook", "messenger", "telegram",
            "twitter", "gmail", "outlook", "discord", "skype", "viber",
            "snapchat", "tiktok", "netflix", "spotify", "youtube"
        )
        return appsPopulares.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun actualizarContadorApps() {
        val totalApps = (recyclerApps.adapter?.itemCount ?: 0)
        val appsActivas = obtenerCantidadAppsActivas()

        val textoContador = "Aplicaciones configuradas: $appsActivas/$totalApps activas"

        txtEstado.text = textoContador // ← CAMBIA: usa txtEstado en lugar de findViewById

        Log.d("Notificaciones", "Contador actualizado: $appsActivas/$totalApps")
    }

    private fun obtenerCantidadAppsActivas(): Int {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        val allPrefs = sharedPref.all

        return allPrefs.count { it.value == true }
    }

    private fun esAppDelSistema(packageName: String): Boolean {
        val appsSistema = listOf(
            "com.android.settings",
            "com.google.android.apps.photos",
            "com.android.camera",
            "com.android.calculator",
            "com.google.android.calendar",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.gallery",
            "com.android.email"
        )
        return appsSistema.any { packageName.contains(it) }
    }

    private fun obtenerEstadoApp(nombreApp: String): Boolean {
        val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
        return sharedPref.getBoolean(nombreApp, true) // Por defecto activadas
    }

    private fun actualizarEstadoServicio() {
        if (isServicioAccesibilidadActivado()) {
            txtEstado.text = if (switchLecturaAuto.isChecked) {
                "✅ Lectura automática ACTIVADA"
            } else {
                "⏸️ Lectura automática PAUSADA"
            }
        } else {
            txtEstado.text = "❌ Servicio de accesibilidad no activado"
        }
    }

    private fun isServicioAccesibilidadActivado(): Boolean {
        try {
            val service = ComponentName(this, LecturaPantallaService::class.java)
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(service.flattenToString()) ?: false
        } catch (e: Exception) {
            Log.e("Notificaciones", "Error verificando servicio: ${e.message}")
            return false
        }
    }

    private fun abrirConfiguracionAccesibilidad() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error abriendo configuración de accesibilidad", Toast.LENGTH_SHORT).show()
        }
    }
}