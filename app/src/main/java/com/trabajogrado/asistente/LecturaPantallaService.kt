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

    // Métodos para controlar el servicio desde la app
    fun activarLectura() {
        Log.d(TAG, "🎯 ACTIVANDO lectura de pantalla por comando del usuario")
        // Aquí podrías activar flags o variables de control
        lecturaActiva = true
        hablar("Lectura de pantalla activada")
    }

    fun desactivarLectura() {
        Log.d(TAG, "🎯 DESACTIVANDO lectura de pantalla por comando del usuario")
        lecturaActiva = false
        textToSpeech.stop()
        hablar("Lectura de pantalla desactivada")
    }

    fun estaActivo(): Boolean {
        return lecturaActiva
    }

    // Metodo para recibir comandos desde fuera del servicio
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Servicio desvinculado")
        return super.onUnbind(intent)
    }

    // Metodo estático para obtener la instancia del servicio (simplificado)
    companion object {
        private var instance: LecturaPantallaService? = null

        fun getInstance(): LecturaPantallaService? {
            return instance
        }

        fun activarLecturaDesdeExterno() {
            instance?.activarLectura()
        }

        fun desactivarLecturaDesdeExterno() {
            instance?.desactivarLectura()
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "✅ Servicio de lectura de pantalla CONECTADO")
        instance = this  // ← GUARDAR INSTANCIA

        // Configurar el servicio de accesibilidad
        configurarServicio()

        // Inicializar TextToSpeech pero NO hablar automáticamente
        textToSpeech = TextToSpeech(this, this)

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
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED

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
        event?.let {
            Log.d(TAG, "📱 Evento detectado: ${event.eventType} - App: ${event.packageName}")

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Anunciar en qué aplicación o pantalla está
                    anunciarPantallaActual(event.packageName?.toString())
                }

                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    // Leer lo que se tocó
                    leerElementoTocado(event)
                }

                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    // Leer elementos cuando reciben foco
                    leerElementoConFoco(event)
                }

                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    // Leer elementos seleccionados
                    leerElementoSeleccionado(event)
                }

                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // Leer texto escrito en campos
                    leerTextoEscrito(event)
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
            // hablar(nombreApp)  // COMENTADO: No hablar automáticamente
            Log.d(TAG, "🔄 App detectada: $nombreApp (SILENCIO)")
            if (lecturaActiva) {  // ← SOLO HABLAR SI ESTÁ ACTIVO
                hablar(nombreApp)
            }
            ultimaAppAnunciada = nombreApp
        }
    }

    private fun leerElementoTocado(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "👆 Elemento tocado (SILENCIO): $texto")
            if (lecturaActiva) {  // ← SOLO HABLAR SI ESTÁ ACTIVO
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
            if (lecturaActiva) {  // ← SOLO HABLAR SI ESTÁ ACTIVO
                hablar(mensaje)
            }
        } else if (tipoElemento != null) {
            Log.d(TAG, "🎯 Elemento con foco (SILENCIO): $tipoElemento")
            if (lecturaActiva) {  // ← SOLO HABLAR SI ESTÁ ACTIVO
                hablar(tipoElemento)
            }
        }
    }

    private fun leerElementoSeleccionado(event: AccessibilityEvent) {
        val texto = obtenerTextoDelEvento(event)
        if (texto.isNotBlank()) {
            Log.d(TAG, "✅ Elemento seleccionado (SILENCIO): $texto")
            if (lecturaActiva) {  // ← SOLO HABLAR SI ESTÁ ACTIVO
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