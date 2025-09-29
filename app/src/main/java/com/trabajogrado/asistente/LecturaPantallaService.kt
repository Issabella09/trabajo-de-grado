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
            Log.d(TAG, "📱 Evento: ${event.eventType}")

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    // Cuando un elemento recibe foco, leerlo
                    val texto = event.text?.joinToString(", ") ?: "Elemento sin texto"
                    if (texto.isNotEmpty() && texto != "null") {
                        Log.d(TAG, "🔊 Leyendo: $texto")
                        hablar(texto)
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Cuando cambia la pantalla
                    val nombreApp = event.packageName ?: "Aplicación"
                    Log.d(TAG, "🔄 Cambio de pantalla: $nombreApp")
                    hablar("Abriendo $nombreApp")
                }
            }
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