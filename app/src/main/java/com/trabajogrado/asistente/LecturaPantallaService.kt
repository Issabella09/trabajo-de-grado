package com.trabajogrado.asistente

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.Locale

class LecturaPantallaService : AccessibilityService(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech
    private val TAG = "LecturaPantallaService"

    override fun onServiceConnected() {
        Log.d(TAG, "✅ Servicio de lectura de pantalla CONECTADO")

        // Configurar el servicio de accesibilidad
        configurarServicio()

        // Inicializar TextToSpeech
        textToSpeech = TextToSpeech(this, this)
    }

    private fun configurarServicio() {
        val info = AccessibilityServiceInfo().apply {
            // Qué eventos queremos escuchar
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            // Tipo de feedback (voz)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN

            // Tiempo entre eventos
            notificationTimeout = 100

            // Capacidades del servicio
            flags = AccessibilityServiceInfo.DEFAULT
        }

        this.serviceInfo = info
        Log.d(TAG, "⚙️ Servicio configurado")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Configurar español
            val result = textToSpeech.setLanguage(Locale("es", "ES"))

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ Idioma español no soportado")
            } else {
                Log.d(TAG, "🎤 TextToSpeech listo en español")
                hablar("Servicio de lectura activado")
            }
        } else {
            Log.e(TAG, "❌ Error al inicializar TextToSpeech")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            Log.d(TAG, "📱 Evento tipo: ${event.eventType} - Clase: ${event.className}")

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    // Cuando un elemento recibe foco (toque o navegación)
                    leerContenidoElemento(event)
                }

                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    // Cuando se hace clic en un elemento
                    leerContenidoElemento(event)
                }

                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    // Cuando se selecciona un elemento
                    leerContenidoElemento(event)
                }

                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // Cuando cambia el texto (editores, búsquedas)
                    if (event.text?.isNotEmpty() == true) {
                        val texto = event.text.joinToString(", ")
                        if (texto.length > 1) {
                            Log.d(TAG, "📝 Texto cambiado: $texto")
                            hablar("Texto escrito: $texto")
                        }
                    }
                }

                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Cuando cambia la ventana/pantalla
                    val nombreApp = when (event.packageName) {
                        "com.android.launcher3" -> "Inicio"
                        "com.android.systemui" -> "Sistema"
                        else -> event.packageName ?: "Aplicación"
                    }
                    Log.d(TAG, "🔄 Cambio a: $nombreApp")
                    hablar("Abriendo $nombreApp")
                }

                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    // Cuando se desplaza la pantalla
                    Log.d(TAG, "📜 Desplazamiento detectado")
                    hablar("Desplazando")
                }
            }
        }
    }

    private fun leerContenidoElemento(event: AccessibilityEvent) {
        val texto = event.text?.joinToString(", ") ?: ""
        val contenido = event.contentDescription?.toString() ?: ""

        // Combinar texto y descripción
        val textoFinal = when {
            texto.isNotEmpty() && contenido.isNotEmpty() -> "$texto, $contenido"
            texto.isNotEmpty() -> texto
            contenido.isNotEmpty() -> contenido
            else -> null
        }

        if (textoFinal != null && textoFinal != "null" && textoFinal.length > 1) {
            Log.d(TAG, "🔊 Leyendo elemento: $textoFinal")
            hablar(textoFinal)
        } else {
            // Si no hay texto, leer el tipo de elemento
            val tipoElemento = obtenerTipoElemento(event.className?.toString())
            if (tipoElemento != null) {
                Log.d(TAG, "🔊 Elemento sin texto: $tipoElemento")
                hablar(tipoElemento)
            }
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