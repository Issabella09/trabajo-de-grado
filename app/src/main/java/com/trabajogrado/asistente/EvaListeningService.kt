package com.trabajogrado.asistente

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvaListeningService : Service(), TextToSpeech.OnInitListener {

    private val TAG = "EvaListeningService"
    private val CHANNEL_ID = "eva_listening_channel"
    private val NOTIF_ID = 1001
    private val PREFS_NAME = "EvaPreferences"
    private val KEY_EVA_ACTIVE = "eva_active"

    private var voskDetector: VoskHotwordDetector? = null
    private var tts: TextToSpeech? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private var ttsListo = false

    companion object {
        @Volatile
        var isRunning = false
            private set

        const val ACTION_UPDATE = "com.trabajogrado.asistente.EVA_UPDATE"
        const val EXTRA_ESTADO = "estado"
        const val EXTRA_COMANDO = "comando"
        const val EXTRA_RESPUESTA = "respuesta"

        fun iniciar(context: Context) {
            val intent = Intent(context, EvaListeningService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun detener(context: Context) {
            context.stopService(Intent(context, EvaListeningService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        crearCanalNotificacion()
        startForeground(NOTIF_ID, crearNotificacion("Iniciando..."))
        tts = TextToSpeech(this, this)
        inicializarVosk()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_EVA_ACTIVE, false).apply()
        voskDetector?.cleanup()
        tts?.shutdown()
        commandRecognizer?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "CO")
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.9f)
            ttsListo = true
            Log.d(TAG, "✅ TTS listo")
        } else {
            Log.e(TAG, "❌ Error TTS")
        }
    }

    private fun inicializarVosk() {
        voskDetector = VoskHotwordDetector(this) { onHotwordDetected() }
        voskDetector?.initialize(
            onReady = {
                voskDetector?.startListening()
                enviarBroadcast(estado = "👂 Esperando 'EVA'...")
                actualizarNotificacion("Escuchando 'EVA'...")
                Log.d(TAG, "✅ Vosk listo, escuchando")
            },
            onError = { error ->
                Log.e(TAG, "❌ Error Vosk: $error")
                enviarBroadcast(estado = "Error inicializando EVA")
            }
        )
    }

    private fun onHotwordDetected() {
        Log.d(TAG, "🎯 EVA DETECTADO")
        vibrar()
        enviarBroadcast(estado = "🎤 EVA activado, di tu comando...")
        actualizarNotificacion("EVA activado")
        Handler(Looper.getMainLooper()).postDelayed({ iniciarEscuchaComando() }, 500)
    }

    private fun iniciarEscuchaComando() {
        Handler(Looper.getMainLooper()).post {
            commandRecognizer?.destroy()
            commandRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            commandRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.e(TAG, "❌ Error SpeechRecognizer: $error")
                    enviarBroadcast(estado = "No te entendí, di 'EVA' de nuevo")
                    Handler(Looper.getMainLooper()).postDelayed({
                        voskDetector?.startListening()
                        enviarBroadcast(estado = "👂 Esperando 'EVA'...")
                        actualizarNotificacion("Escuchando 'EVA'...")
                    }, 2000)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val comando = matches[0]
                        Log.d(TAG, "✅ COMANDO: $comando")
                        enviarBroadcast(estado = "Procesando...", comando = "EVA: $comando")
                        procesarComando(comando)
                        Handler(Looper.getMainLooper()).postDelayed({
                            voskDetector?.startListening()
                            enviarBroadcast(estado = "👂 Esperando 'EVA'...")
                            actualizarNotificacion("Escuchando 'EVA'...")
                        }, 3000)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            commandRecognizer?.startListening(intent)
        }
    }

    private fun procesarComando(comando: String) {
        val comandoLower = comando.lowercase()
        when {
            comandoLower.contains("notificacion") || comandoLower.contains("notificaciones") -> {
                val service = NotificationReaderService.instance
                if (service == null) {
                    responder("No tengo permiso para leer notificaciones. Ve a Ajustes y activa EVA.")
                } else {
                    val notis = NotificationReaderService.getNotificacionesActivas(service)
                    if (notis.isEmpty()) responder("No tienes notificaciones pendientes.")
                    else responder("Tienes ${notis.size} notificaciones. ${notis.joinToString(". ")}")
                }
            }
            comandoLower.contains("abrir") || comandoLower.contains("abre") -> {
                val nombreApp = comandoLower
                    .replace("eva", "").replace("abrir", "").replace("abre", "")
                    .replace("la aplicación", "").replace("la aplicacion", "")
                    .replace("la app", "").trim()
                if (nombreApp.isNotEmpty()) abrirAplicacion(nombreApp)
                else responder("¿Qué aplicación quieres abrir?")
            }
            comandoLower.contains("hora") -> decirHora()
            comandoLower.contains("fecha") -> decirFecha()
            comandoLower.contains("chiste") -> contarChiste()
            comandoLower.contains("clima") || comandoLower.contains("tiempo") -> buscarClima()
            comandoLower.contains("buscar") || comandoLower.contains("busca") -> {
                val busqueda = comandoLower
                    .replace("buscar", "").replace("busca", "")
                    .replace("en internet", "").replace("en google", "").trim()
                if (busqueda.isNotEmpty()) buscarEnInternet(busqueda)
                else responder("¿Qué quieres buscar?")
            }
            comandoLower.contains("ayuda") -> {
                responder("Puedo decirte la hora, la fecha, contarte chistes, abrir aplicaciones, leer notificaciones, o buscar en internet.")
            }
            else -> responder("No entendí el comando: $comando")
        }
    }

    private fun decirHora() {
        val formato = SimpleDateFormat("h:mm a", Locale("es", "CO"))
        responder("Son las ${formato.format(Date())}")
    }

    private fun decirFecha() {
        val formato = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "CO"))
        responder("Hoy es ${formato.format(Date())}")
    }

    private fun contarChiste() {
        val chistes = listOf(
            "¿Por qué los pájaros no usan Facebook? Porque ya tienen Twitter",
            "¿Qué hace una abeja en el gimnasio? Zumba",
            "¿Cómo se despiden los químicos? Ácido un placer",
            "¿Qué le dice un techo a otro techo? Techo de menos",
            "¿Por qué el libro de matemáticas está triste? Porque tiene muchos problemas"
        )
        responder(chistes.random())
    }

    private fun buscarClima() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode("clima Tuluá Colombia")}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            responder("Buscando el clima")
        } catch (e: Exception) {
            responder("No pude buscar el clima")
        }
    }

    private fun buscarEnInternet(query: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            responder("Buscando $query")
        } catch (e: Exception) {
            responder("No pude buscar")
        }
    }

    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp", "whats" to "com.whatsapp",
        "instagram" to "com.instagram.android", "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana", "face" to "com.facebook.katana",
        "tiktok" to "com.zhiliaoapp.musically", "tik" to "com.zhiliaoapp.musically",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "spotify" to "com.spotify.music",
        "fotos" to "com.coloros.gallery3d",
        "cámara" to "com.oplus.camera", "camara" to "com.oplus.camera"
    )

    private fun abrirAplicacion(nombreApp: String) {
        val nombreNorm = nombreApp.lowercase().trim()
        if (nombreNorm.contains("whats")) {
            if (abrirConUri("whatsapp://send", "WhatsApp")) return
        }
        if (nombreNorm.contains("tik")) {
            if (abrirConUri("snssdk1128://", "TikTok")) return
            if (abrirConUri("tiktok://", "TikTok")) return
        }
        val pkg = appPackages[nombreNorm]
        if (pkg != null && abrirPackage(pkg, nombreApp)) return
        val apps = packageManager.getInstalledApplications(0)
        for (app in apps) {
            if (packageManager.getLaunchIntentForPackage(app.packageName) == null) continue
            val appName = packageManager.getApplicationLabel(app).toString().lowercase()
            if (appName.contains(nombreNorm) || nombreNorm.contains(appName)) {
                if (abrirPackage(app.packageName, nombreApp)) return
            }
        }
        responder("No encontré $nombreApp")
    }

    private fun abrirConUri(uri: String, nombre: String): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            responder("Abriendo $nombre")
            true
        } catch (e: Exception) { false }
    }

    private fun abrirPackage(packageName: String, nombre: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            responder("Abriendo $nombre")
            true
        } catch (e: Exception) { false }
    }

    private fun responder(mensaje: String) {
        enviarBroadcast(respuesta = mensaje)
        hablar(mensaje)
        Log.d(TAG, "💬 $mensaje")
    }

    private fun hablar(texto: String) {
        if (ttsListo) tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    @Suppress("DEPRECATION")
    private fun vibrar() {
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrando: ${e.message}")
        }
    }

    private fun enviarBroadcast(
        estado: String? = null,
        comando: String? = null,
        respuesta: String? = null
    ) {
        val intent = Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            estado?.let { putExtra(EXTRA_ESTADO, it) }
            comando?.let { putExtra(EXTRA_COMANDO, it) }
            respuesta?.let { putExtra(EXTRA_RESPUESTA, it) }
        }
        sendBroadcast(intent)
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EVA Asistente de Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "EVA está escuchando en segundo plano"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun crearNotificacion(texto: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, AsistenteVozNuevoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EVA")
            .setContentText(texto)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun actualizarNotificacion(texto: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, crearNotificacion(texto))
    }
}
