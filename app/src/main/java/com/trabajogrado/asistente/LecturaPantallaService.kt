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
        Log.d(TAG, "Servicio conectado")
        configurarServicio()
        textToSpeech = TextToSpeech(this, this)
    }

    private fun configurarServicio() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            notificationTimeout = 100
        }
        this.serviceInfo = info
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale("es", "CO")
            hablar("Servicio activado")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> leerElemento(event)
                AccessibilityEvent.TYPE_VIEW_CLICKED -> leerElemento(event)
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    when (event.packageName?.toString()) {
                        "com.whatsapp" -> hablar("WhatsApp")
                        "com.android.chrome" -> hablar("Chrome")
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun leerElemento(event: AccessibilityEvent) {
        val texto = event.text?.firstOrNull()?.toString() ?: ""
        if (texto.isNotBlank()) {
            hablar(texto)
        }
    }

    override fun onInterrupt() {
        textToSpeech.stop()
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    private fun hablar(texto: String) {
        textToSpeech.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}