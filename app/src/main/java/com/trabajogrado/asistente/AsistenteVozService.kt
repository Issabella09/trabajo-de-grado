package com.trabajogrado.asistente

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.Locale

class AsistenteVozService : Service() {

    private val TAG = "AsistenteVozService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "asistente_voz_channel"

    // Vosk para escucha silenciosa
    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null

    // Google Speech para comandos
    private var googleSpeechRecognizer: SpeechRecognizer? = null
    private var esperandoComando = false

    // Text-to-Speech
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    // Handler para el hilo principal
    private val mainHandler = Handler(Looper.getMainLooper())

    // ✅ CACHE DE APPS
    private val appsCacheadas = mutableMapOf<String, String>()
    private var cacheListo = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Servicio EVA iniciado")

        try {
            // 1. Iniciar foreground service
            crearCanalNotificacion()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    crearNotificacion(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, crearNotificacion())
            }

            // 2. Inicializar TTS
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("es", "CO")
                    tts?.setPitch(1.0f)
                    tts?.setSpeechRate(0.9f)
                    ttsListo = true
                    Log.d(TAG, "✅ TTS listo")
                }
            }

            // 3. Inicializar Google Speech
            inicializarGoogleSpeech()

            // 4. ✅ Cachear apps en background
            Thread {
                cachearAppsInstaladas()
            }.start()

            // 5. Inicializar Vosk en hilo separado
            Thread {
                try {
                    inicializarVosk()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error en hilo Vosk: ${e.message}", e)
                    mainHandler.post {
                        Toast.makeText(this, "Error iniciando Vosk", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO: ${e.message}", e)
            e.printStackTrace()
            stopSelf()
        }
    }

    // ✅ CACHEAR APPS AL INICIAR
    private fun cachearAppsInstaladas() {
        try {
            Log.d(TAG, "🔄 Cacheando apps instaladas...")

            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val apps = pm.queryIntentActivities(intent, 0)

            for (app in apps) {
                try {
                    val packageName = app.activityInfo.packageName
                    val label = app.loadLabel(pm).toString().lowercase()

                    // Guardar múltiples variaciones del nombre
                    appsCacheadas[label] = packageName

                    // También guardar palabras individuales
                    label.split(" ").forEach { palabra ->
                        if (palabra.length > 3) {
                            appsCacheadas[palabra] = packageName
                        }
                    }

                } catch (e: Exception) {
                    // Ignorar apps que den error
                }
            }

            cacheListo = true
            Log.d(TAG, "✅ ${appsCacheadas.size} variaciones de apps cacheadas")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cacheando apps: ${e.message}", e)
        }
    }

    private fun inicializarVosk() {
        try {
            Log.d(TAG, "🔧 Cargando modelo Vosk...")

            val modelDir = File(filesDir, "vosk-model-es")
            if (!modelDir.exists()) {
                Log.d(TAG, "📥 Copiando modelo desde assets...")
                copiarModeloDesdeAssets("vosk-model-es", modelDir)
            }

            voskModel = Model(modelDir.absolutePath)
            Log.d(TAG, "✅ Modelo cargado")

            val recognizer = Recognizer(voskModel, 16000.0f)
            recognizer.setMaxAlternatives(1)
            recognizer.setWords(true)

            voskSpeechService = SpeechService(recognizer, 16000.0f)

            voskSpeechService?.startListening(object : VoskListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let { texto ->
                        if (texto.lowercase().contains("eva") && !esperandoComando) {
                            Log.d(TAG, "🎯 EVA detectado en parcial")
                            onEvaDetectado()
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { texto ->
                        if (texto.lowercase().contains("eva") && !esperandoComando) {
                            Log.d(TAG, "🎯 EVA detectado en resultado")
                            onEvaDetectado()
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) {}

                override fun onError(exception: Exception?) {
                    Log.e(TAG, "❌ Vosk error: ${exception?.message}")
                    mainHandler.postDelayed({
                        try {
                            voskSpeechService?.startListening(null)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reiniciando Vosk: ${e.message}")
                        }
                    }, 1000)
                }

                override fun onTimeout() {}
            })

            Log.d(TAG, "✅ Vosk escuchando...")
            mainHandler.post {
                Toast.makeText(this, "✅ EVA activo", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando Vosk: ${e.message}", e)
            throw e
        }
    }

    private fun copiarModeloDesdeAssets(assetPath: String, destDir: File) {
        destDir.mkdirs()
        val assetFiles = assets.list(assetPath) ?: return

        for (filename in assetFiles) {
            val fullAssetPath = "$assetPath/$filename"
            val dest = File(destDir, filename)

            val subFiles = assets.list(fullAssetPath)
            if (subFiles != null && subFiles.isNotEmpty()) {
                copiarModeloDesdeAssets(fullAssetPath, dest)
            } else {
                assets.open(fullAssetPath).use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun inicializarGoogleSpeech() {
        try {
            googleSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

            var ultimoComandoParcial = ""

            googleSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "✅ Google Speech listo")
                    ultimoComandoParcial = ""
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "🎤 Detectando voz...")
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "🔚 Fin de voz")

                    if (ultimoComandoParcial.isNotEmpty()) {
                        // ✅ GUARDAR EL COMANDO ANTES DE LIMPIARLO
                        val comandoFinal = ultimoComandoParcial

                        mainHandler.postDelayed({
                            Log.d(TAG, "⏰ Procesando comando parcial: $comandoFinal")
                            Thread {
                                procesarComando(comandoFinal)  // ✅ USAR LA COPIA
                            }.start()
                            volverAEscucharEva()
                        }, 500)

                        ultimoComandoParcial = ""  // ✅ LIMPIAR DESPUÉS
                    }
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                        else -> "Error $error"
                    }
                    Log.e(TAG, "❌ $errorMsg")

                    if (ultimoComandoParcial.isNotEmpty()) {
                        // ✅ GUARDAR EL COMANDO ANTES DE LIMPIARLO
                        val comandoFinal = ultimoComandoParcial
                        ultimoComandoParcial = ""

                        Log.d(TAG, "⚠️ Procesando parcial por error: $comandoFinal")
                        Thread {
                            procesarComando(comandoFinal)  // ✅ USAR LA COPIA
                        }.start()
                    }

                    volverAEscucharEva()
                }

                override fun onResults(results: Bundle?) {
                    try {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                        if (matches != null && matches.isNotEmpty()) {
                            val comando = matches[0]
                            Log.d(TAG, "✅ COMANDO: '$comando'")

                            Thread {
                                procesarComando(comando)
                            }.start()
                        } else if (ultimoComandoParcial.isNotEmpty()) {
                            // ✅ GUARDAR EL COMANDO ANTES DE LIMPIARLO
                            val comandoFinal = ultimoComandoParcial

                            Log.d(TAG, "⚠️ Usando parcial: $comandoFinal")
                            Thread {
                                procesarComando(comandoFinal)  // ✅ USAR LA COPIA
                            }.start()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error: ${e.message}", e)
                    }

                    ultimoComandoParcial = ""
                    volverAEscucharEva()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    try {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (matches != null && matches.isNotEmpty()) {
                            val parcial = matches[0]
                            Log.d(TAG, "📝 Parcial: $parcial")

                            if (parcial.isNotBlank()) {
                                ultimoComandoParcial = parcial
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error en parcial: ${e.message}")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando Google Speech: ${e.message}", e)
        }
    }

    private fun onEvaDetectado() {
        if (esperandoComando) return

        esperandoComando = true

        try {
            voskSpeechService?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo Vosk: ${e.message}")
        }

        mainHandler.post {
            Toast.makeText(this, "🎤 Escuchando...", Toast.LENGTH_SHORT).show()
        }

        responder("Dime")

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        mainHandler.postDelayed({
            try {
                googleSpeechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error activando Google Speech: ${e.message}", e)
                volverAEscucharEva()
            }
        }, 300)
    }

    private fun volverAEscucharEva() {
        esperandoComando = false

        try {
            voskSpeechService?.startListening(null)
            Log.d(TAG, "🔄 Volviendo a escuchar EVA...")
        } catch (e: Exception) {
            Log.e(TAG, "Error reiniciando Vosk: ${e.message}", e)
        }
    }

    private fun procesarComando(comando: String) {
        Log.d(TAG, "🔄 Procesando: '$comando'")

        try {
            val comandoLower = comando.lowercase().trim()

            when {
                comandoLower.contains("lista apps") || comandoLower.contains("listar aplicaciones") -> {
                    listarAppsInstaladas()
                }
                comandoLower.contains("abre") || comandoLower.contains("abrir") -> {
                    val app = comandoLower
                        .replace("abre", "")
                        .replace("abrir", "")
                        .trim()
                    abrirAplicacion(app)
                }

                comandoLower.contains("busca") || comandoLower.contains("buscar") -> {
                    val query = comandoLower
                        .replace("busca", "")
                        .replace("buscar", "")
                        .replace("en google", "")
                        .replace("en internet", "")
                        .trim()
                    buscarEnInternet(query)
                }

                comandoLower.contains("notificaciones") || comandoLower.contains("notificación") -> {
                    abrirNotificaciones()
                }

                comandoLower.contains("ayuda") -> {
                    responder("Puedo abrir aplicaciones, buscar en internet o leer tus notificaciones")
                }

                else -> {
                    responder("No entendí el comando")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando: ${e.message}", e)
            mainHandler.post {
                Toast.makeText(this, "Error ejecutando comando", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ SISTEMA HÍBRIDO DE APERTURA DE APPS
    private fun abrirAplicacion(nombreApp: String) {
        Log.d(TAG, "🔍 Buscando app: $nombreApp")

        try {
            val nombreLower = nombreApp.lowercase().trim()
            val pm = packageManager

            // Obtener TODAS las apps que se pueden lanzar
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val todasLasApps = pm.queryIntentActivities(intent, 0)

            Log.d(TAG, "📱 Total apps disponibles: ${todasLasApps.size}")

            // Buscar la app
            val appEncontrada = todasLasApps.find {
                val label = it.loadLabel(pm).toString().lowercase()
                label.contains(nombreLower) || nombreLower.contains(label)
            }

            if (appEncontrada != null) {
                val packageName = appEncontrada.activityInfo.packageName
                val nombreReal = appEncontrada.loadLabel(pm).toString()

                Log.d(TAG, "✅ ENCONTRADA: $nombreReal ($packageName)")

                val launchIntent = pm.getLaunchIntentForPackage(packageName)

                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    mainHandler.post {
                        try {
                            startActivity(launchIntent)
                            responder("Abriendo $nombreReal")
                            Log.d(TAG, "✅✅✅ APP ABIERTA EXITOSAMENTE")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al lanzar: ${e.message}", e)
                            responder("Error al abrir")
                        }
                    }
                } else {
                    Log.e(TAG, "❌ No se puede lanzar: $packageName")
                    mainHandler.post {
                        responder("No puedo abrir $nombreReal")
                    }
                }
            } else {
                Log.e(TAG, "❌ No encontré ninguna app con: $nombreApp")
                Log.d(TAG, "📋 Apps disponibles:")
                todasLasApps.take(10).forEach {
                    Log.d(TAG, "  - ${it.loadLabel(pm)}")
                }

                mainHandler.post {
                    responder("No encontré la aplicación $nombreApp")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO: ${e.message}", e)
            e.printStackTrace()
            mainHandler.post {
                responder("Error buscando la aplicación")
            }
        }
    }

    private fun buscarEnInternet(query: String) {
        if (query.isEmpty()) {
            responder("¿Qué quieres buscar?")
            return
        }

        try {
            Log.d(TAG, "🔍 Buscando: $query")

            val url = "https://www.google.com/search?q=${Uri.encode(query)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            mainHandler.post {
                try {
                    startActivity(intent)
                    responder("Buscando $query")
                    Log.d(TAG, "✅ Búsqueda iniciada")
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}", e)
                    responder("Error al buscar")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            mainHandler.post {
                responder("Error al buscar")
            }
        }
    }

    private fun abrirNotificaciones() {
        try {
            val intent = Intent(this, NotificacionesActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            mainHandler.post {
                try {
                    startActivity(intent)
                    responder("Abriendo notificaciones")
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}", e)
                    responder("Error al abrir notificaciones")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
        }
    }

    private fun responder(mensaje: String) {
        mainHandler.post {
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()

            if (ttsListo) {
                try {
                    tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en TTS: ${e.message}")
                }
            }
        }
        Log.d(TAG, "💬 $mensaje")
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asistente EVA",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "EVA escuchando"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun crearNotificacion(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎤 EVA Activo")
            .setContentText("Di 'EVA' para dar comandos")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun listarAppsInstaladas() {
        Thread {
            try {
                Log.d(TAG, "")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "📱 APPS INSTALADAS EN TU CELULAR:")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")

                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val apps = pm.queryIntentActivities(intent, 0)
                    .sortedBy { it.loadLabel(pm).toString() }

                apps.forEachIndexed { index, app ->
                    val nombre = app.loadLabel(pm).toString()
                    val packageName = app.activityInfo.packageName

                    // Verificar si se puede lanzar
                    val intent = pm.getLaunchIntentForPackage(packageName)
                    val estado = if (intent != null) "✅" else "❌"

                    Log.d(TAG, "${index + 1}. $estado $nombre")
                    Log.d(TAG, "   Package: $packageName")
                    Log.d(TAG, "")
                }

                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "TOTAL: ${apps.size} apps instaladas")
                Log.d(TAG, "═══════════════════════════════════════════════════════════")
                Log.d(TAG, "")

                mainHandler.post {
                    responder("Encontré ${apps.size} aplicaciones. Mira el logcat")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error listando apps: ${e.message}", e)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            voskSpeechService?.stop()
            voskSpeechService?.shutdown()
            googleSpeechRecognizer?.destroy()
            tts?.shutdown()
            appsCacheadas.clear()
            Log.d(TAG, "🔚 EVA desactivado")
        } catch (e: Exception) {
            Log.e(TAG, "Error en onDestroy: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}