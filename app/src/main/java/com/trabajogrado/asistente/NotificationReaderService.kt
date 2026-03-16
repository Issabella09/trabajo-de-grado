package com.trabajogrado.asistente

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class NotificationReaderService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val TAG = "NotificationReader"
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    // ✅ Caché para evitar repetir notificaciones de llamadas
    private val llamadasAnunciadas = mutableSetOf<String>()
    private var ultimaLlamadaAnunciada: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Servicio de notificaciones creado")

        // Inicializar Text-to-Speech
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "CO")
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.9f)
            ttsListo = true
            Log.d(TAG, "✅ TTS inicializado")
        } else {
            Log.e(TAG, "❌ Error inicializando TTS")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            val notification = sbn.notification

            Log.d(TAG, "📬 Nueva notificación de: $packageName")

            // ✅ Detectar si es una llamada entrante
            if (esLlamadaEntrante(sbn)) {
                manejarLlamadaEntrante(sbn)
                return
            }

            // Verificar si la lectura automática está activada
            if (!isLecturaAutomaticaActivada()) {
                Log.d(TAG, "⏸️ Lectura automática desactivada")
                return
            }

            // Verificar si esta app está en la lista permitida
            val appName = getAppName(packageName)
            if (!isAppPermitida(appName)) {
                Log.d(TAG, "🚫 App no permitida: $appName")
                return
            }

            // Extraer información de la notificación
            val titulo = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val texto = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val textoGrande = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: texto

            Log.d(TAG, "📱 App: $appName")
            Log.d(TAG, "📌 Título: $titulo")
            Log.d(TAG, "💬 Texto: $textoGrande")

            // Leer la notificación
            if (titulo.isNotEmpty() || textoGrande.isNotEmpty()) {
                leerNotificacion(appName, titulo, textoGrande)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando notificación: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // ✅ Limpiar caché cuando se elimina notificación de llamada
        if (esLlamadaEntrante(sbn)) {
            val caller = obtenerNombreLlamante(sbn)
            llamadasAnunciadas.remove(caller)
            Log.d(TAG, "🔄 Llamada finalizada: $caller")

            // ✅ Detener TTS si está hablando
            tts?.stop()
        }
    }

    // ✅ NUEVO: Detectar si es una llamada entrante
    private fun esLlamadaEntrante(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification

        // Verificar categoría de llamada
        if (notification.category == Notification.CATEGORY_CALL) {
            return true
        }

        // Verificar package de teléfono
        val packageName = sbn.packageName
        if (packageName.contains("dialer", ignoreCase = true) ||
            packageName.contains("phone", ignoreCase = true) ||
            packageName.contains("call", ignoreCase = true) ||
            packageName == "com.android.server.telecom") {
            return true
        }

        // Verificar flags de llamada
        if ((notification.flags and Notification.FLAG_INSISTENT) != 0) {
            return true
        }

        return false
    }

    // ✅ NUEVO: Manejar llamada entrante
    private fun manejarLlamadaEntrante(sbn: StatusBarNotification) {
        val caller = obtenerNombreLlamante(sbn)
        val ahora = System.currentTimeMillis()

        // ✅ Evitar anunciar la misma llamada múltiples veces
        if (llamadasAnunciadas.contains(caller)) {
            Log.d(TAG, "🔇 Llamada ya anunciada: $caller")
            return
        }

        // ✅ Evitar anuncios muy seguidos (menos de 3 segundos)
        if (ahora - ultimaLlamadaAnunciada < 3000) {
            Log.d(TAG, "⏱️ Anuncio muy reciente, esperando...")
            return
        }

        // ✅ Anunciar la llamada UNA SOLA VEZ
        llamadasAnunciadas.add(caller)
        ultimaLlamadaAnunciada = ahora

        val mensaje = "Llamada entrante de $caller"
        Log.d(TAG, "📞 Anunciando: $mensaje")

        // ✅ Detener cualquier TTS anterior y anunciar
        tts?.stop()
        tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, null)

        // ✅ Limpiar caché después de 30 segundos (por si no se elimina la notificación)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            llamadasAnunciadas.remove(caller)
        }, 30000)
    }

    // ✅ NUEVO: Obtener nombre de quien llama
    private fun obtenerNombreLlamante(sbn: StatusBarNotification): String {
        val notification = sbn.notification

        // Intentar obtener el nombre del título
        val titulo = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!titulo.isNullOrEmpty()) {
            return titulo
        }

        // Intentar obtener del texto
        val texto = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!texto.isNullOrEmpty()) {
            return texto
        }

        return "Número desconocido"
    }

    private fun leerNotificacion(appName: String, titulo: String, texto: String) {
        if (!ttsListo) {
            Log.e(TAG, "❌ TTS no está listo")
            return
        }

        // Obtener el prefijo configurado
        val prefs = getSharedPreferences("config_notificaciones", Context.MODE_PRIVATE)
        val tipoPrefijo = prefs.getString("prefijo_lectura", "notificacion") ?: "notificacion"

        // Construir el mensaje según el prefijo
        val mensaje = buildString {
            when (tipoPrefijo) {
                "notificacion" -> append("Notificación de $appName. ")
                "mensaje" -> append("Mensaje de $appName. ")
                "sin_prefijo" -> append("$appName. ")
            }

            if (titulo.isNotEmpty()) {
                append("$titulo. ")
            }
            if (texto.isNotEmpty() && texto != titulo) {
                append(texto)
            }
        }

        Log.d(TAG, "🔊 Leyendo: $mensaje")
        tts?.speak(mensaje, TextToSpeech.QUEUE_ADD, null, null)
    }

    private fun isLecturaAutomaticaActivada(): Boolean {
        val prefs = getSharedPreferences("config_notificaciones", Context.MODE_PRIVATE)
        return prefs.getBoolean("lectura_automatica", false)
    }

    private fun isAppPermitida(appName: String): Boolean {
        val prefs = getSharedPreferences("apps_permitidas", Context.MODE_PRIVATE)
        return prefs.getBoolean(appName, true) // Por defecto activadas
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            // Si no se puede obtener el nombre, usar mapeo manual
            when (packageName) {
                "com.whatsapp" -> "WhatsApp"
                "com.instagram.android" -> "Instagram"
                "com.facebook.katana" -> "Facebook"
                "org.telegram.messenger" -> "Telegram"
                "com.twitter.android" -> "Twitter"
                "com.google.android.gm" -> "Gmail"
                "com.android.messaging" -> "Mensajes"
                else -> packageName
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        llamadasAnunciadas.clear()
        Log.d(TAG, "🔚 Servicio de notificaciones destruido")
    }
}