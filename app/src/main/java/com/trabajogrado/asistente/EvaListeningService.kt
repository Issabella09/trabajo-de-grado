package com.trabajogrado.asistente

import android.app.Notification
import android.app.KeyguardManager
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
import android.os.PowerManager
import android.os.VibrationEffect
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConversationState { IDLE }

class EvaListeningService : Service(), TextToSpeech.OnInitListener {

    private val TAG = "EvaListeningService"
    private val CHANNEL_ID = "eva_listening_channel"
    private val NOTIF_ID = 1001
    private val LAUNCH_NOTIF_ID = 1002
    private val PREFS_NAME = "EvaPreferences"
    private val KEY_EVA_ACTIVE = "eva_active"

    private var voskDetector: VoskHotwordDetector? = null
    private var tts: TextToSpeech? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private var ttsListo = false
    private var overlayView: View? = null
    private var wmService: WindowManager? = null
    private var conversationState = ConversationState.IDLE

    companion object {
        @Volatile
        var isRunning = false
            private set
        @Volatile
        var instance: EvaListeningService? = null
        @Volatile
        var pendingLaunchIntent: Intent? = null

        const val ACTION_UPDATE = "com.trabajogrado.asistente.EVA_UPDATE"
        const val EXTRA_ESTADO = "estado"
        const val EXTRA_COMANDO = "comando"
        const val EXTRA_RESPUESTA = "respuesta"
        const val EXTRA_START_LISTENING = "start_listening"

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
        instance = this
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
        instance = null
        removerOverlay()
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
                enviarBroadcast(estado = "👂 Esperando 'HOLA EVA'...")
                actualizarNotificacion("Escuchando 'HOLA EVA'...")
                Log.d(TAG, "✅ Vosk listo, escuchando")
            },
            onError = { error ->
                Log.e(TAG, "❌ Error Vosk: $error")
                enviarBroadcast(estado = "Error inicializando EVA")
            }
        )
    }

    private fun onHotwordDetected() {
        Log.d(TAG, "🎯 HOLA EVA DETECTADO")
        vibrar()
        enviarBroadcast(estado = "🎤 EVA activado, di tu comando...")
        actualizarNotificacion("EVA activado")

        if (!AsistenteVozNuevoActivity.isInForeground) {
            val intent = Intent(this, AsistenteVozNuevoActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_START_LISTENING, true)
            }
            lanzarConOverlay(intent)
            Handler(Looper.getMainLooper()).postDelayed({ iniciarEscuchaComando() }, 1200)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ iniciarEscuchaComando() }, 500)
        }
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
                    enviarBroadcast(estado = "No te entendí, di 'HOLA EVA' de nuevo")
                    Handler(Looper.getMainLooper()).postDelayed({
                        voskDetector?.startListening()
                        enviarBroadcast(estado = "👂 Esperando 'HOLA EVA'...")
                        actualizarNotificacion("Escuchando 'HOLA EVA'...")
                    }, 2000)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches.isNullOrEmpty()) return
                    val texto = matches[0]
                    Log.d(TAG, "✅ COMANDO: $texto")
                    enviarBroadcast(estado = "Procesando...", comando = "EVA: $texto")
                    procesarComando(texto)
                    Handler(Looper.getMainLooper()).postDelayed({
                        voskDetector?.startListening()
                        enviarBroadcast(estado = "👂 Esperando 'HOLA EVA'...")
                        actualizarNotificacion("Escuchando 'HOLA EVA'...")
                    }, 3000)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Umbrales generosos para flujo conversacional: el usuario puede tomarse su tiempo
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }
            arrancarReconocedor(intent)
        }
    }

    // Abre el micrófono solo cuando el TTS ya terminó de hablar, con 500ms extra para que
    // el hardware de audio libere completamente el canal del altavoz antes de abrir el micrófono.
    private fun arrancarReconocedor(intent: Intent) {
        if (tts?.isSpeaking == true) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        tts?.setOnUtteranceProgressListener(null)
                        commandRecognizer?.startListening(intent)
                    }, 500)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        tts?.setOnUtteranceProgressListener(null)
                        commandRecognizer?.startListening(intent)
                    }, 500)
                }
            })
        } else {
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
                responder("Puedo decirte la hora, la fecha, contarte chistes, abrir aplicaciones, leer notificaciones, buscar en internet, o enviar mensajes por WhatsApp.")
            }
            comandoLower.contains("whatsapp") || comandoLower.contains("wasa") -> {
                procesarComandoWhatsApp(comando)
            }
            else -> responder("No entendí")
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
        if (!estaDesbloqueado()) { responder("Primero desbloquea el teléfono"); return }
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode("clima Tuluá Colombia")}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        hablarYEjecutar("Buscando el clima") {
            if (!lanzarActivity(intent)) mostrarNotificacionLanzamiento(intent)
        }
    }

    private fun buscarEnInternet(query: String) {
        if (!estaDesbloqueado()) { responder("Primero desbloquea el teléfono"); return }
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        hablarYEjecutar("Buscando $query") {
            if (!lanzarActivity(intent)) mostrarNotificacionLanzamiento(intent)
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
        if (!estaDesbloqueado()) { responder("Primero desbloquea el teléfono"); return }
        val nombreNorm = nombreApp.lowercase().trim()
        val intent = resolverIntentApp(nombreNorm)
        if (intent == null) { responder("No encontré $nombreApp"); return }
        hablarYEjecutar("Abriendo $nombreApp") {
            if (!lanzarActivity(intent)) mostrarNotificacionLanzamiento(intent)
        }
    }

    // Resuelve el Intent sin lanzarlo. Devuelve null si la app no está instalada.
    private fun resolverIntentApp(nombreNorm: String): Intent? {
        if (nombreNorm.contains("whats")) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (packageManager.resolveActivity(i, 0) != null) return i
        }
        if (nombreNorm.contains("tik")) {
            for (uri in listOf("snssdk1128://", "tiktok://")) {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (packageManager.resolveActivity(i, 0) != null) return i
            }
        }
        val pkg = appPackages[nombreNorm]
        if (pkg != null) {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                return it.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
        }
        for (app in packageManager.getInstalledApplications(0)) {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName) ?: continue
            val appName = packageManager.getApplicationLabel(app).toString().lowercase()
            if (appName.contains(nombreNorm) || nombreNorm.contains(appName)) {
                return launchIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
        }
        return null
    }

    /**
     * Tres capas para lanzar desde segundo plano:
     *  1. AccessibilityService — exento de background launch restrictions en Android 10+
     *  2. Directo desde el Service — funciona en algunos dispositivos (foreground service exemption)
     *  3. OverlayLauncherActivity vía WindowManager TYPE_APPLICATION_OVERLAY (MIUI y Android 12+)
     */
    private fun lanzarActivity(intent: Intent): Boolean {
        if (LecturaPantallaService.lanzarActividad(intent)) return true
        try { startActivity(intent); return true } catch (e: Exception) {
            Log.w(TAG, "Lanzamiento directo bloqueado: ${e.message}")
        }
        if (Settings.canDrawOverlays(this)) {
            lanzarConOverlay(intent)
            return true
        }
        return false
    }

    // Habla la confirmación con TTS y ejecuta la acción DESPUÉS de que termina de hablar.
    private fun hablarYEjecutar(mensaje: String, accion: () -> Unit) {
        enviarBroadcast(respuesta = mensaje)
        Log.d(TAG, "💬 $mensaje → acción después")
        if (!ttsListo) { accion(); return }
        val uid = "eva_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == uid) Handler(Looper.getMainLooper()).post { accion() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == uid) Handler(Looper.getMainLooper()).post { accion() }
            }
        })
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid) }
        tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, params, uid)
    }

    // Notificación de alta prioridad como último recurso: el usuario la toca y la app se abre
    // desde un contexto de primer plano (tap en notificación).
    private fun mostrarNotificacionLanzamiento(intent: Intent) {
        val pending = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        getSystemService(NotificationManager::class.java).notify(
            LAUNCH_NOTIF_ID,
            androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("EVA")
                .setContentText("Toca para continuar")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()
        )
        Log.d(TAG, "🔔 Notificación de lanzamiento mostrada")
    }

    // Añade una View 1×1 invisible con TYPE_APPLICATION_OVERLAY. En MIUI, tener un overlay
    // activo hace que el sistema trate el proceso como visible y permita startActivity().
    @Suppress("DEPRECATION")
    internal fun mostrarOverlay() {
        if (!Settings.canDrawOverlays(this) || overlayView != null) return
        try {
            wmService = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = View(this)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            val params = WindowManager.LayoutParams(
                1, 1, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            wmService?.addView(overlayView, params)
            Log.d(TAG, "🪟 Overlay añadido")
        } catch (e: Exception) {
            Log.e(TAG, "Error añadiendo overlay: ${e.message}")
        }
    }

    internal fun removerOverlay() {
        try {
            overlayView?.let { wmService?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removiendo overlay: ${e.message}")
        } finally {
            overlayView = null
        }
    }

    // Muestra el overlay y lanza OverlayLauncherActivity, que desde su contexto de Activity
    // puede abrir cualquier intent sin restricciones de background launch.
    private fun lanzarConOverlay(intent: Intent) {
        mostrarOverlay()
        pendingLaunchIntent = intent
        try {
            startActivity(Intent(this, OverlayLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Log.d(TAG, "🚀 OverlayLauncherActivity lanzada")
            // Limpieza de seguridad si OverlayLauncherActivity no llega a ejecutar onDestroy
            Handler(Looper.getMainLooper()).postDelayed({ removerOverlay() }, 3000)
        } catch (e: Exception) {
            Log.e(TAG, "❌ OverlayLauncher falló: ${e.message}")
            removerOverlay()
            pendingLaunchIntent = null
        }
    }

    private fun estaDesbloqueado(): Boolean {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return power.isInteractive && !keyguard.isKeyguardLocked
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "EVA Asistente de Voz",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "EVA está escuchando en segundo plano"
                    setShowBadge(false)
                }
            )
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

    private fun volverAEsperarHotword(delayMs: Long = 2000) {
        conversationState = ConversationState.IDLE
        Handler(Looper.getMainLooper()).postDelayed({
            voskDetector?.startListening()
            enviarBroadcast(estado = "👂 Esperando 'HOLA EVA'...")
            actualizarNotificacion("Escuchando 'HOLA EVA'...")
        }, delayMs)
    }

    private fun procesarComandoWhatsApp(textoOriginal: String) {
        val textoLower = textoOriginal.lowercase()
        val idxA = textoLower.indexOf(" a ")
        val idxQueDiga = textoLower.indexOf(" que diga")

        if (idxA == -1 || idxQueDiga == -1 || idxA >= idxQueDiga) {
            responder("No entendí. Di por ejemplo: envía un mensaje por WhatsApp a Juan que diga hola.")
            return
        }

        val nombreContacto = textoOriginal.substring(idxA + 3, idxQueDiga).trim()
        val mensaje = textoOriginal.substring(idxQueDiga + " que diga".length).trim()

        if (nombreContacto.isBlank()) {
            responder("No escuché el nombre del contacto.")
            return
        }
        if (mensaje.isBlank()) {
            responder("No escuché el mensaje.")
            return
        }

        enviarBroadcast(estado = "Buscando contacto $nombreContacto...")
        Thread {
            val numero = buscarNumeroContacto(nombreContacto)
            Handler(Looper.getMainLooper()).post {
                if (numero == null) {
                    responder("No encontré a $nombreContacto en tu agenda.")
                } else {
                    val numLimpio = normalizarNumeroInternacional(numero)
                    val mensajeCodificado = java.net.URLEncoder.encode(mensaje, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://api.whatsapp.com/send?phone=$numLimpio&text=$mensajeCodificado")).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    hablarYEjecutar("Enviando mensaje a $nombreContacto por WhatsApp") {
                        LecturaPantallaService.pendingAutoSend = true
                        if (!lanzarActivity(intent)) mostrarNotificacionLanzamiento(intent)
                        Handler(Looper.getMainLooper()).postDelayed({
                            LecturaPantallaService.clicarBotonEnviar("com.whatsapp")
                        }, 2500)
                    }
                }
            }
        }.start()
    }

    private fun normalizarNumeroInternacional(numero: String): String {
        val soloDigitos = numero.replace(Regex("[^0-9+]"), "")
        return when {
            soloDigitos.startsWith("+") -> soloDigitos.removePrefix("+")
            soloDigitos.length == 10 -> "57$soloDigitos"
            soloDigitos.startsWith("0") -> "57${soloDigitos.removePrefix("0")}"
            else -> soloDigitos
        }
    }

    private fun buscarNumeroContacto(nombre: String): String? {
        val tienePermiso = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val nombreNorm = nombre.normalizado()
        Log.d("EVA_WA", "Permiso contactos: $tienePermiso, buscando: $nombreNorm")
        if (!tienePermiso) return null
        if (nombreNorm.isBlank()) return null
        return try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    ) ?: continue
                    if (displayName.normalizado().contains(nombreNorm)) {
                        return@use cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        )
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando número de contacto: ${e.message}")
            null
        }
    }

    private fun String.normalizado(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()
}
