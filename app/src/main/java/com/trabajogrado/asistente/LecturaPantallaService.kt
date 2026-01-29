package com.trabajogrado.asistente

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Locale
import android.content.Intent
import android.app.Service

class LecturaPantallaService : AccessibilityService(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech
    private val TAG = "LecturaPantallaService"
    private var ultimaAppAnunciada: String? = null
    private var lecturaActiva: Boolean = false
    private var isLecturaNotificacionesActiva = false
    private val notificacionesLeidas = mutableSetOf<String>()
    private var ultimoLogDesactivado: Long = 0

    // Métodos para controlar el servicio desde la app
    fun activarLectura() {
        Log.d(TAG, "🎯 ACTIVANDO lectura de pantalla por comando del usuario")
        Companion.lecturaActiva = true  // ← Usar la del companion
        hablar("Lectura de pantalla activada")
    }

    fun desactivarLectura() {
        Log.d(TAG, "🎯 DESACTIVANDO lectura de pantalla por comando del usuario")
        Companion.lecturaActiva = false

        // Detener TODAS las lecturas inmediatamente
        textToSpeech.stop()
        textToSpeech.shutdown()

        // Re-inicializar TextToSpeech (pero silenciado)
        textToSpeech = TextToSpeech(this, this)

        Log.d(TAG, "🔕 Servicio COMPLETAMENTE silenciado")
    }

    fun actualizarConfiguracionNotificaciones() {
        Log.d(TAG, "🔄 SOLICITUD DE ACTUALIZACIÓN DE CONFIGURACIÓN")

        // Recargar configuración INMEDIATAMENTE
        cargarConfiguracionNotificaciones()

        // Detener cualquier lectura en curso si se desactivó
        if (!isLecturaNotificacionesActiva && textToSpeech.isSpeaking) {
            Log.d(TAG, "⏹️ Deteniendo lectura en curso porque se desactivaron notificaciones")
            textToSpeech.stop()
        }

        Log.d(TAG, "✅ Configuración actualizada. Notificaciones activas: $isLecturaNotificacionesActiva")
    }

    fun estaActivo(): Boolean {
        return Companion.lecturaActiva  // ← Usar la del companion
    }

    // Metodo para recibir comandos desde fuera del servicio
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Servicio desvinculado")
        return super.onUnbind(intent)
    }

    // Metodo estático para obtener la instancia del servicio (simplificado)
    companion object {

        private var nivelLectura: Int = 1 // Por defecto nivel 1
        private var lecturaActiva: Boolean = false
        private var instance: LecturaPantallaService? = null

        fun activarLecturaDesdeExterno(nivel: Int = 1) {
            nivelLectura = nivel
            lecturaActiva = true
            instance?.activarLectura() // Llama a tu método existente
            Log.d("LecturaService", "📢 Lectura activada - Nivel: $nivel")
        }

        fun actualizarNivelLectura(nuevoNivel: Int) {
            nivelLectura = nuevoNivel
            Log.d("LecturaService", "🔄 Nivel actualizado: $nuevoNivel")
        }

        fun obtenerNivelActual(): Int {
            return nivelLectura
        }

        fun getInstance(): LecturaPantallaService? {
            return instance
        }

        fun activarLecturaDesdeExterno() {
            instance?.activarLectura()
        }

        fun desactivarLecturaDesdeExterno() {
            instance?.desactivarLectura()
        }

        fun actualizarConfiguracionNotificaciones() {
            instance?.actualizarConfiguracionNotificaciones()
            Log.d("LecturaService", "🔄 Solicitada actualización de configuración de notificaciones")
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "✅ Servicio de lectura de pantalla CONECTADO")
        instance = this  // ← GUARDAR INSTANCIA

        // Configurar el servicio de accesibilidad
        configurarServicio()

        // Inicializar TextToSpeech pero NO hablar automáticamente
        textToSpeech = TextToSpeech(this, this)

        cargarConfiguracionNotificaciones()

        // IMPORTANTE: No hablar ni activar funcionalidad aquí
        // El usuario controlará cuándo activar desde la app
        Log.d(TAG, "⚠️ Servicio configurado pero NO activado - Esperando comando del usuario")
    }

    private fun configurarServicio() {
        val info = AccessibilityServiceInfo().apply {
            // Configurar qué eventos queremos escuchar
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

            // Tipo de feedback (voz)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN

            // Tiempo de espera para enviar eventos
            notificationTimeout = 100

            // Podemos interceptar gestos y leer contenido
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
            // Permitir leer contenido de la pantalla
            this.serviceInfo = info
            Log.d(TAG, "Servicio configurado para leer todas las aplicaciones")

    }

    private fun cargarConfiguracionNotificaciones() {
        try {
            val sharedPref = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
            isLecturaNotificacionesActiva = sharedPref.getBoolean("lectura_automatica", false)
            Log.d(TAG, "📢 Configuración notificaciones cargada: $isLecturaNotificacionesActiva")

            // DEBUG EXTRA: Mostrar todas las preferencias
            val allPrefs = sharedPref.all
            Log.d(TAG, "📋 Todas las preferencias de notificaciones: $allPrefs")

        } catch (e: Exception) {
            Log.e(TAG, "Error cargando configuración notificaciones: ${e.message}")
            isLecturaNotificacionesActiva = false // Por defecto desactivado
        }
    }

    private fun procesarNotificacion(event: AccessibilityEvent) {
        Log.d(TAG, "🔔 EVENTO DE NOTIFICACIÓN DETECTADO")

        // 1. Verificar si el servicio global está activado
        if (!Companion.lecturaActiva) {
            Log.d(TAG, "🔇 Servicio global DESACTIVADO - Ignorando notificación")
            return
        }

        // 2. Verificar si las notificaciones están activadas (NUEVO CHECK CRÍTICO)
        if (!isLecturaNotificacionesActiva) {
            Log.d(TAG, "🔕 Switch de notificaciones DESACTIVADO - Ignorando notificación")
            return // ← ESTA ES LA LÍNEA CLAVE
        }

        try {
            val textoNotificacion = obtenerTextoNotificacion(event)
            Log.d(TAG, "📝 Texto de notificación: '$textoNotificacion'")

            if (textoNotificacion.isNotBlank()) {
                Log.d(TAG, "✅ Notificación tiene texto")

                if (esNotificacionNueva(textoNotificacion)) {
                    Log.d(TAG, "🆕 Notificación NUEVA (no repetida)")

                    // Verificar si es de una app permitida
                    val nombreApp = obtenerNombreApp(event.packageName.toString())
                    Log.d(TAG, "📱 App de origen: ${event.packageName} -> $nombreApp")

                    if (esAppPermitida(nombreApp)) {
                        Log.d(TAG, "✅ App PERMITIDA: $nombreApp")

                        // Leer la notificación
                        val mensaje = "Notificación de $nombreApp: $textoNotificacion"
                        Log.d(TAG, "🎤 Leyendo: $mensaje")
                        leerEnVozAlta(mensaje)

                        // Marcar como leída
                        notificacionesLeidas.add(textoNotificacion.hashCode().toString())
                        Log.d(TAG, "📌 Notificación marcada como leída")

                    } else {
                        Log.d(TAG, "❌ App NO permitida: $nombreApp")
                    }
                } else {
                    Log.d(TAG, "🔁 Notificación REPETIDA, ignorando")
                }
            } else {
                Log.d(TAG, "❌ Notificación SIN TEXTO")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR procesando notificación: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun obtenerTextoNotificacion(event: AccessibilityEvent): String {
        return if (event.text.isNotEmpty()) {
            event.text.joinToString(" ")
        } else {
            "Notificación sin texto"
        }
    }

    private fun esNotificacionNueva(texto: String): Boolean {
        val hash = texto.hashCode().toString()
        return !notificacionesLeidas.contains(hash)
    }

    private fun obtenerNombreApp(packageName: String): String {
        val mapaPaquetes = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.android.messaging" to "Mensajes",
            "com.google.android.gm" to "Gmail",
            "com.facebook.katana" to "Facebook",
            "com.instagram.android" to "Instagram",
            "org.telegram.messenger" to "Telegram",
            "com.twitter.android" to "Twitter",
            "com.discord" to "Discord",
            "com.skype.raider" to "Skype",
            "com.viber.voip" to "Viber",
            "com.snapchat.android" to "Snapchat",
            "com.microsoft.teams" to "Microsoft Teams",
            "com.signal" to "Signal",
            "com.google.android.talk" to "Google Meet",
            "com.android.email" to "Email",
            "com.samsung.android.messaging" to "Mensajes Samsung"
        )

        return mapaPaquetes[packageName] ?: "Aplicación"
    }

    private fun esAppPermitida(nombreApp: String): Boolean {
        try {
            val sharedPref = getSharedPreferences("apps_permitidas", MODE_PRIVATE)
            return sharedPref.getBoolean(nombreApp, true) // Por defecto todas permitidas
        } catch (e: Exception) {
            return true
        }
    }

    private fun leerEnVozAlta(texto: String) {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // configurar español latino
            val result = textToSpeech.setLanguage(Locale("es", "COL"))

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ El idioma español no está soportado")
            } else {
                // NO hablar automáticamente - solo configurar
                textToSpeech.setPitch(0.95f)
                textToSpeech.setSpeechRate(1.0f)

                Log.d(TAG, "🎤 TextToSpeech listo pero SILENCIADO - Esperando activación")
                // NO llamar a hablar() aquí
            }
        } else {
            Log.e(TAG, "❌ Error al inicializar TextToSpeech")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (!Companion.lecturaActiva || !isLecturaNotificacionesActiva) {
            // Log solo la primera vez para no spammear
            if (System.currentTimeMillis() - ultimoLogDesactivado > 5000) {
                Log.d(TAG, "🔇 Servicio DESACTIVADO - Ignorando evento")
                ultimoLogDesactivado = System.currentTimeMillis()
            }
            return
        }

        event?.let {
            Log.d(TAG, "📱 Evento detectado: ${event.eventType} - App: ${event.packageName} - Nivel: ${Companion.nivelLectura}")

            when (Companion.nivelLectura) {
                1 -> procesarEventoNivel1(event)
                2 -> procesarEventoNivel2(event)
                else -> procesarEventoNivel1(event)
            }
        }
    }

    private fun procesarEventoNivel1(event: AccessibilityEvent) {
        // SOLO procesar si es una app de mensajería
        if (esAppDeMensajeria(event.packageName?.toString())) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    anunciarPantallaActual(event.packageName?.toString())
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    leerElementoTocado(event)
                }
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    leerElementoConFoco(event)
                }
                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    leerElementoSeleccionado(event)
                }
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    leerTextoEscrito(event)
                }
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    // Leer notificaciones de mensajería
                    leerNotificacionMensaje(event)
                }
            }
        } else {
            Log.d(TAG, "🔇 Nivel 1: Ignorando evento en app no mensajería")
        }
    }

    private fun procesarEventoNivel2(event: AccessibilityEvent) {
        // LEER TODO en cualquier app - SIN FILTROS
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                anunciarPantallaActual(event.packageName?.toString())
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                leerElementoTocado(event)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                leerElementoConFoco(event)
            }
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                leerElementoSeleccionado(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                leerTextoEscrito(event)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                leerNotificacionMensaje(event)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                leerElementoDesplazado(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                leerElementoLargoTocado(event)
            }
            // Agregar más tipos de eventos para nivel 2
            else -> {
                // Para cualquier otro tipo de evento, intentar leerlo
                leerEventoGenerico(event)
            }
        }
    }

    private fun esAppDeMensajeria(packageName: String?): Boolean {
        return when (packageName) {
            "com.whatsapp", // WhatsApp
            "com.whatsapp.w4b", // WhatsApp Business
            "org.telegram.messenger", // Telegram
            "org.telegram.messenger.web", // Telegram Web
            "com.instagram.android", // Instagram
            "com.facebook.katana", // Facebook
            "com.facebook.orca", // Facebook Messenger
            "com.facebook.mlite", // Facebook Lite
            "com.discord", // Discord
            "com.skype.raider", // Skype
            "com.snapchat.android", // Snapchat
            "com.google.android.talk", // Google Hangouts/Meet
            "com.microsoft.teams", // Microsoft Teams
            "com.signal", // Signal
            "org.thoughtcrime.securesms", // Signal (alternativo)
            "com.viber.voip" -> true // Viber
            else -> false
        }
    }

    private fun leerNotificacionMensaje(event: AccessibilityEvent) {
        procesarNotificacion(event)
    }

    // AGREGAR ESTOS NUEVOS MÉTODOS para nivel 2:

    private fun leerElementoDesplazado(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "🔄 Elemento desplazado: $texto")
            if (Companion.lecturaActiva) {
                hablar("Desplazando: $texto")
            }
        }
    }

    private fun leerElementoLargoTocado(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "👆🔒 Elemento largo tocado: $texto")
            if (Companion.lecturaActiva) {
                hablar("Mantener presionado: $texto")
            }
        }
    }

    private fun leerEventoGenerico(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "📝 Evento genérico [${event.eventType}]: $texto")
            if (Companion.lecturaActiva) {
                // Solo leer si es un texto significativo (no muy largo)
                if (texto.length < 100) {
                    hablar(texto)
                }
            }
        }
    }

    private fun anunciarPantallaActual(packageName: String?) {
        val nombreApp = when (packageName) {
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher" -> "Pantalla de inicio"

            "com.whatsapp" -> "WhatsApp"
            "com.instagram.android" -> "Instagram"
            "com.facebook.katana" -> "Facebook"
            "org.telegram.messenger" -> "Telegram"
            "com.twitter.android" -> "Twitter"
            "com.android.chrome" -> "Chrome"
            "com.google.android.gm" -> "Gmail"
            "com.android.settings" -> "Ajustes"
            "com.android.dialer" -> "Teléfono"
            "com.google.android.contacts" -> "Contactos"
            "com.android.messaging" -> "Mensajes"
            "com.spotify.music" -> "Spotify"
            "com.netflix.mediaclient" -> "Netflix"
            "com.amazon.mShop.android.shopping" -> "Amazon"

            else -> null
        }

        // Solo anunciar si es una app diferente a la última anunciada
        if (nombreApp != null && nombreApp != ultimaAppAnunciada) {
            Log.d(TAG, "🔄 App detectada: $nombreApp (SILENCIO)")
            if (Companion.lecturaActiva) {  // ← CAMBIADO: Usar Companion.lecturaActiva
                hablar(nombreApp)
            }
            ultimaAppAnunciada = nombreApp
        }
    }

    private fun leerElementoTocado(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "👆 Elemento tocado (SILENCIO): $texto")
            if (Companion.lecturaActiva) {  // ← CAMBIADO
                hablar(texto)
            }
        }

    }

    private fun leerElementoConFoco(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        val tipoElemento = obtenerTipoElemento(event.className?.toString())

        if (texto.isNotBlank()) {
            val mensaje = when {
                tipoElemento != null -> "$texto, $tipoElemento"
                else -> texto
            }
            Log.d(TAG, "🎯 Elemento con foco (SILENCIO): $mensaje")
            if (Companion.lecturaActiva) {
                hablar(mensaje)
            }
        } else if (tipoElemento != null) {
            Log.d(TAG, "🎯 Elemento con foco (SILENCIO): $tipoElemento")
            if (Companion.lecturaActiva) {  // ← ELIMINA el "if" duplicado
                hablar(tipoElemento)
            }
        }
    }

    private fun leerElementoSeleccionado(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "✅ Elemento seleccionado (SILENCIO): $texto")
            if (Companion.lecturaActiva) {  // ← CAMBIADO
                hablar("Seleccionado: $texto")
            }
        }
    }

    private fun leerTextoEscrito(event: AccessibilityEvent) {
        val texto = event.text?.joinToString("") { it.toString() } ?: ""
        // Solo leer texto si es un campo de entrada y tiene contenido
        if (texto.isNotBlank() && event.className?.toString()?.contains("EditText") == true) {
            if (texto.length > 2 && texto.length < 50) { // No leer textos muy largos
                Log.d(TAG, "📝 Texto escrito: $texto")
                //hablar(texto) // Opcional: comentado para no ser intrusivo
            }
        }
    }

    private fun obtenerTextoDelEvento(event: AccessibilityEvent): String {
        val texto = event.text?.joinToString(" ") { it.toString() } ?: ""
        val descripcion = event.contentDescription?.toString() ?: ""

        // Preferir la descripción si está disponible, sino el texto
        return when {
            descripcion.isNotBlank() -> descripcion
            texto.isNotBlank() -> texto
            else -> ""
        }
    }

    private fun obtenerTipoElemento(className: String?): String? {
        return when {
            className == null -> null
            className.contains("Button", ignoreCase = true) -> "Botón"
            className.contains("EditText", ignoreCase = true) -> "Campo de texto"
            className.contains("TextView", ignoreCase = true) -> "Texto"
            className.contains("Image", ignoreCase = true) -> "Imagen"
            className.contains("CheckBox", ignoreCase = true) -> "Casilla"
            className.contains("RadioButton", ignoreCase = true) -> "Opción"
            className.contains("Switch", ignoreCase = true) -> "Interruptor"
            className.contains("SeekBar", ignoreCase = true) -> "Barra"
            className.contains("Spinner", ignoreCase = true) -> "Lista"
            else -> null
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "⏸️ Servicio interrumpido")
        textToSpeech.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos
        textToSpeech.stop()
        textToSpeech.shutdown()
        Log.d(TAG, "🔚 Servicio destruido")
    }

    private fun hablar(texto: String) {
        if (texto.isNotBlank()) {
            textToSpeech.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

}