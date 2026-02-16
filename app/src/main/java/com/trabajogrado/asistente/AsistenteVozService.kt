package com.trabajogrado.asistente

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

class AsistenteVozService : Service() {

    private val TAG = "AsistenteVozService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "asistente_voz_channel"

    private var googleSpeechRecognizer: SpeechRecognizer? = null
    private var escuchando = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅✅✅ SERVICIO CREADO - INICIO")

        try {
            crearCanalNotificacion()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    crearNotificacion(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
                Log.d(TAG, "✅ Foreground service iniciado (Android 10+)")
            } else {
                startForeground(NOTIFICATION_ID, crearNotificacion())
                Log.d(TAG, "✅ Foreground service iniciado")
            }

            Toast.makeText(this, "🎤 Servicio de voz iniciado", Toast.LENGTH_LONG).show()

            // Inicializar reconocedor
            inicializarGoogleSpeech()

            // Empezar a escuchar inmediatamente
            iniciarEscucha()

        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ ERROR CRÍTICO: ${e.message}", e)
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun inicializarGoogleSpeech() {
        Log.d(TAG, "🔧 Inicializando Google Speech...")

        googleSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        googleSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "✅ Listo para escuchar")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 Detectando voz...")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Log.d(TAG, "📊 Nivel de audio: $rmsdB")
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "🔚 Fin de voz")
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos"
                    else -> "Error $error"
                }
                Log.d(TAG, "⚠️ Error: $errorMsg")

                // Reiniciar escucha después de 2 segundos
                android.os.Handler(mainLooper).postDelayed({
                    if (!escuchando) {
                        iniciarEscucha()
                    }
                }, 2000)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (matches != null && matches.isNotEmpty()) {
                    val texto = matches[0].lowercase()
                    Log.d(TAG, "🎯 RECONOCIDO: '$texto'")
                    Toast.makeText(this@AsistenteVozService, "Escuché: $texto", Toast.LENGTH_SHORT).show()

                    // Procesar comando
                    if (texto.contains("eva")) {
                        Log.d(TAG, "✅✅✅ EVA DETECTADO!")
                        procesarComando(texto)
                    }
                }

                // Reiniciar escucha
                android.os.Handler(mainLooper).postDelayed({
                    iniciarEscucha()
                }, 1000)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    Log.d(TAG, "📝 Parcial: ${matches[0]}")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        Log.d(TAG, "✅ Google Speech inicializado")
    }

    private fun iniciarEscucha() {
        escuchando = true

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            googleSpeechRecognizer?.startListening(intent)
            Log.d(TAG, "🎤 ESCUCHANDO...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando escucha: ${e.message}")
            escuchando = false
        }
    }

    private fun procesarComando(textoCompleto: String) {
        Log.d(TAG, "🔄 Procesando: '$textoCompleto'")

        val texto = textoCompleto.lowercase().trim()

        // Remover "eva" del inicio si está presente
        val comando = if (texto.startsWith("eva ")) {
            texto.substring(4).trim()

        } else {
            texto.replace("eva", "").trim()
        }

        Log.d(TAG, "📌 Comando limpio: '$comando'")

        when {
            // ========== ABRIR APLICACIONES ==========
            comando.contains("abrir") || comando.contains("abre") || comando.contains("abra") -> {
                val nombreApp = comando
                    .replace("abrir", "")
                    .replace("abre", "")
                    .replace("abra", "")
                    .trim()

                if (nombreApp.isNotEmpty()) {
                    buscarYAbrirApp(nombreApp)
                } else {
                    Toast.makeText(this, "¿Qué app quieres abrir?", Toast.LENGTH_SHORT).show()
                }
            }


            // ========== LEER NOTIFICACIONES ==========
            comando.contains("notificaciones") || comando.contains("notificación") -> {
                try {
                    val intent = Intent(this, NotificacionesActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    Toast.makeText(this, "🔔 Abriendo notificaciones", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "✅ Notificaciones abiertas")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error abriendo notificaciones: ${e.message}")
                    Toast.makeText(this, "No pude abrir notificaciones", Toast.LENGTH_SHORT).show()
                }
            }

            // ========== BUSCAR EN INTERNET ==========
            comando.contains("busca") || comando.contains("buscar") || comando.contains("búsqueda") -> {
                val query = comando
                    .replace("busca", "")
                    .replace("buscar", "")
                    .replace("en google", "")
                    .replace("google", "")
                    .trim()

                if (query.isNotEmpty()) {
                    try {
                        val url = "https://www.google.com/search?q=${android.net.Uri.encode(query)}"
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        Toast.makeText(this, "🔍 Buscando: $query", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "✅ Búsqueda: $query")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error buscando: ${e.message}")
                        Toast.makeText(this, "No pude hacer la búsqueda", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "¿Qué quieres buscar?", Toast.LENGTH_SHORT).show()
                }
            }

            // ========== AYUDA ==========
            comando.contains("ayuda") || comando.contains("qué puedes hacer") || comando.contains("que puedes hacer") -> {
                val mensaje = """
                Puedo ayudarte con:
                - Abre WhatsApp
                - Abre Instagram
                - Lee mis notificaciones
                - Busca recetas de pizza
            """.trimIndent()
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                Log.d(TAG, "ℹ️ Ayuda mostrada")
            }

            // ========== COMANDO NO RECONOCIDO ==========
            else -> {
                Toast.makeText(this, "Comando: $comando", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "⚠️ Comando no reconocido: $comando")
            }
        }
    }

    private fun buscarYAbrirApp(nombreApp: String) {
        Log.d(TAG, "🔍 Buscando app: $nombreApp")

        try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            // Buscar app que contenga el nombre
            val appEncontrada = apps.find {
                val label = pm.getApplicationLabel(it).toString().lowercase()
                label.contains(nombreApp.lowercase()) &&
                        pm.getLaunchIntentForPackage(it.packageName) != null
            }

            if (appEncontrada != null) {
                val packageName = appEncontrada.packageName
                val nombre = pm.getApplicationLabel(appEncontrada).toString()

                Log.d(TAG, "✅ App encontrada: $nombre ($packageName)")
                abrirApp(packageName, nombre)
            } else {
                Log.e(TAG, "❌ No se encontró app con nombre: $nombreApp")
                Toast.makeText(this, "No encontré la app $nombreApp", Toast.LENGTH_SHORT).show()

                // Sugerir apps similares
                val similares = apps.filter {
                    val label = pm.getApplicationLabel(it).toString().lowercase()
                    label.contains(nombreApp.lowercase().take(3))
                }.take(3)

                if (similares.isNotEmpty()) {
                    Log.d(TAG, "💡 Apps similares:")
                    similares.forEach {
                        Log.d(TAG, "  → ${pm.getApplicationLabel(it)}")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error buscando app: ${e.message}", e)
            Toast.makeText(this, "Error buscando app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirApp(packageName: String, nombre: String) {
        Log.d(TAG, "🚀 Intentando abrir: $nombre ($packageName)")

        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Toast.makeText(this, "✅ Abriendo $nombre", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ $nombre abierto exitosamente")
            } else {
                Toast.makeText(this, "❌ $nombre no está instalado", Toast.LENGTH_LONG).show()
                Log.e(TAG, "❌ $nombre no encontrado - package: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error abriendo $nombre: ${e.message}", e)
            e.printStackTrace()
            Toast.makeText(this, "Error abriendo $nombre", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asistente de Voz EVA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "EVA escuchando"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            Log.d(TAG, "✅ Canal de notificación creado")
        }
    }

    private fun crearNotificacion(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎤 EVA Escuchando")
            .setContentText("Di 'EVA' seguido de tu comando")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        escuchando = false
        googleSpeechRecognizer?.destroy()
        Log.d(TAG, "🔚 Servicio destruido")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
