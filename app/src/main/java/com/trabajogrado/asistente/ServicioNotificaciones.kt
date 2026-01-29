package com.trabajogrado.asistente

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.Toast

class ServicioNotificaciones : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val TAG = "ServicioNotificaciones"
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Servicio de notificaciones CREADO")
        tts = TextToSpeech(this, this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✅ Servicio de notificaciones CONECTADO")
        // Mostrar toast para confirmar que está funcionando
        Toast.makeText(this, "Servicio de notificaciones activado", Toast.LENGTH_SHORT).show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "CO"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ Idioma español no soportado")
            } else {
                isInitialized = true
                Log.d(TAG, "✅ TTS inicializado correctamente para notificaciones")
            }
        } else {
            Log.e(TAG, "❌ Error inicializando TTS")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "📩 Notificación RECIBIDA - Paquete: ${sbn.packageName}")

        if (!isInitialized) {
            Log.d(TAG, "⏳ TTS no inicializado, ignorando notificación")
            return
        }

        val notification = sbn.notification
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = notification.extras.getString(Notification.EXTRA_TEXT) ?: ""

        Log.d(TAG, "📋 Notificación - App: ${sbn.packageName}, Titulo: '$title', Texto: '$text'")

        // Leer solo notificaciones importantes (evitar spam)
        if (esNotificacionImportante(sbn.packageName, title, text)) {
            leerNotificacion(title, text, sbn.packageName)
        } else {
            Log.d(TAG, "🚫 Notificación filtrada (no importante)")
        }
    }

    private fun esNotificacionImportante(packageName: String, title: String, text: String): Boolean {
        // Filtrar notificaciones del sistema o vacías
        if (text.isBlank() && title.isBlank()) {
            Log.d(TAG, "🚫 Filtrada: título y texto vacíos")
            return false
        }

        // Filtrar apps del sistema
        if (packageName.contains("systemui") ||
            packageName.contains("android") ||
            packageName.contains("google") ||
            packageName.contains("launcher")) {
            Log.d(TAG, "🚫 Filtrada: app del sistema - $packageName")
            return false
        }

        // Filtrar notificaciones silenciosas o de baja prioridad
        if (text.contains("sync") || text.contains("actualizando") || text.contains("updating")) {
            return false
        }

        Log.d(TAG, "✅ Notificación IMPORTANTE - App: $packageName")
        return true
    }

    private fun leerNotificacion(titulo: String, texto: String, packageName: String) {
        val mensaje = when {
            titulo.isNotBlank() && texto.isNotBlank() -> "$titulo: $texto"
            titulo.isNotBlank() -> titulo
            texto.isNotBlank() -> texto
            else -> {
                Log.d(TAG, "🚫 Mensaje vacío, no se lee")
                return
            }
        }

        Log.d(TAG, "🔊 Leyendo notificación: $mensaje")

        tts?.speak(mensaje, TextToSpeech.QUEUE_ADD, null, "notif_${System.currentTimeMillis()}")

        // Mostrar toast para confirmar visualmente
        Toast.makeText(this, "🔊 Leyendo notificación", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Log.d(TAG, "❌ Servicio de notificaciones DESTRUIDO")
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        fun estaActivado(): Boolean {
            // Metodo para verificar si el servicio está activo
            return true
        }
    }
}