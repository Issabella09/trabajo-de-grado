package com.trabajogrado.asistente

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.widget.SwitchCompat

class AsistenteVozNuevoActivity : AppCompatActivity() {

    private val TAG = "AsistenteVozNuevo"
    private val REQUEST_RECORD_AUDIO = 1
    private val PREFS_NAME = "EvaPreferences"
    private val KEY_EVA_ACTIVE = "eva_active"

    private lateinit var tvUltimoComando: TextView
    private lateinit var tvRespuesta: TextView
    private lateinit var switchEva: SwitchCompat
    private lateinit var tvEstadoEva: TextView
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var modoEvaActivo = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var voskDetector: VoskHotwordDetector
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistente_voz_nuevo)
        inicializarVistas()
        listarAppsInstaladas()
        inicializarTTS()
        configurarListeners()
        inicializarVosk()
    }

    private fun listarAppsInstaladas() {
        try {
            Log.d(TAG, "========== LISTADO DE APPS INSTALADAS ==========")
            val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val nombre = packageManager.getApplicationLabel(app).toString()
                val packageName = app.packageName
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                val esLanzable = if (launchIntent != null) "SI" else "NO"
                Log.d(TAG, "App: $nombre | Package: $packageName | Lanzable: $esLanzable")
            }
            Log.d(TAG, "========== FIN DEL LISTADO ==========")
        } catch (e: Exception) {
            Log.e(TAG, "Error listando apps: ${e.message}")
        }
    }

    private fun inicializarVistas() {
        tvUltimoComando = findViewById(R.id.tv_ultimo_comando)
        tvRespuesta = findViewById(R.id.tv_respuesta)
        switchEva = findViewById(R.id.switch_eva)
        tvEstadoEva = findViewById(R.id.tv_estado_eva)
        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    private fun inicializarTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "CO")
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(0.9f)
                ttsListo = true
                Log.d(TAG, "✅ TTS listo")
            }
        }
    }

    private fun configurarListeners() {
        switchEva.setOnCheckedChangeListener { _, isChecked ->
            guardarEstadoEva(isChecked)
            if (isChecked) activarModoEva() else desactivarModoEva()
        }
    }

    private fun decirHora() {
        try {
            val formato = SimpleDateFormat("h:mm a", Locale("es", "CO"))
            val horaActual = formato.format(Date())
            Log.d(TAG, "🕐 Hora: $horaActual")
            responder("Son las $horaActual")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            responder("No pude obtener la hora")
        }
    }

    private fun decirFecha() {
        try {
            val formato = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", Locale("es", "CO"))
            val fechaActual = formato.format(Date())
            Log.d(TAG, "📅 Fecha: $fechaActual")
            responder("Hoy es $fechaActual")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            responder("No pude obtener la fecha")
        }
    }

    private fun contarChiste() {
        val chistes = listOf(
            "¿Por qué los pájaros no usan Facebook? Porque ya tienen Twitter",
            "¿Qué hace una abeja en el gimnasio? Zumba",
            "¿Cómo se despiden los químicos? Ácido un placer",
            "¿Qué le dice un techo a otro techo? Techo de menos",
            "¿Por qué el libro de matemáticas está triste? Porque tiene muchos problemas"
        )
        val chisteAleatorio = chistes.random()
        Log.d(TAG, "😄 Chiste: $chisteAleatorio")
        responderConRisas(chisteAleatorio)
    }

    private fun buscarClima() {
        try {
            val url = "https://www.google.com/search?q=${Uri.encode("clima Tuluá Colombia")}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            responder("Buscando el clima de Tuluá")
            Log.d(TAG, "✅ Búsqueda de clima")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            responder("Error al buscar el clima")
        }
    }

    private fun buscarEnInternet(query: String) {
        if (query.isEmpty()) {
            responder("¿Qué quieres buscar?")
            return
        }
        try {
            val url = "https://www.google.com/search?q=${Uri.encode(query)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            responder("Buscando $query")
            Log.d(TAG, "✅ Búsqueda: $query")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            responder("Error al buscar")
        }
    }

    private fun responder(mensaje: String) {
        runOnUiThread {
            mostrarRespuesta(mensaje)
            if (ttsListo) {
                tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        Log.d(TAG, "💬 $mensaje")
    }

    private fun responderConRisas(mensaje: String) {
        runOnUiThread {
            mostrarRespuesta(mensaje)
            if (ttsListo) {
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "chiste")

                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "chiste") {
                            Handler(Looper.getMainLooper()).postDelayed({
                                reproducirRisas()
                            }, 300)
                        }
                    }
                    override fun onError(utteranceId: String?) {}
                })

                tts?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, params, "chiste")
            }
        }
        Log.d(TAG, "💬 $mensaje 😂")
    }

    private fun reproducirRisas() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.risas)
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
            mediaPlayer?.start()
            Log.d(TAG, "🎵 Reproduciendo risas")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reproduciendo risas: ${e.message}")
        }
    }

    private fun mostrarRespuesta(mensaje: String) {
        tvRespuesta.text = mensaje
    }

    private fun leerNotificacionesPendientes() {
        val service = NotificationReaderService.instance
        if (service == null) {
            responder("No tengo permiso para leer notificaciones. Ve a Ajustes, Notificaciones, Acceso a notificaciones y activa EVA.")
            return
        }
        val notis = NotificationReaderService.getNotificacionesActivas(service)
        if (notis.isEmpty()) {
            responder("No tienes notificaciones pendientes.")
            return
        }
        responder("Tienes ${notis.size} notificaciones. ${notis.joinToString(". ")}")
    }

    private fun procesarComando(comando: String) {
        Log.d(TAG, "🎯 Procesando comando: $comando")
        val comandoLower = comando.lowercase()

        when {
            comandoLower.contains("notificacion") || comandoLower.contains("notificaciones") -> {
                leerNotificacionesPendientes()
            }
            comandoLower.contains("abrir") || comandoLower.contains("abre") -> {
                val nombreApp = comandoLower
                    .replace("eva", "")
                    .replace("abrir", "")
                    .replace("abre", "")
                    .replace("la aplicación", "")
                    .replace("la aplicacion", "")
                    .replace("la app", "")
                    .trim()
                if (nombreApp.isNotEmpty()) {
                    abrirAplicacion(nombreApp)
                } else {
                    responder("¿Qué aplicación quieres abrir?")
                }
            }
            comandoLower.contains("hora") -> decirHora()
            comandoLower.contains("fecha") -> decirFecha()
            comandoLower.contains("chiste") -> contarChiste()
            comandoLower.contains("clima") || comandoLower.contains("tiempo") -> buscarClima()
            comandoLower.contains("buscar") || comandoLower.contains("busca") -> {
                val busqueda = comandoLower
                    .replace("buscar", "")
                    .replace("busca", "")
                    .replace("en internet", "")
                    .replace("en google", "")
                    .trim()
                if (busqueda.isNotEmpty()) {
                    buscarEnInternet(busqueda)
                } else {
                    responder("¿Qué quieres buscar?")
                }
            }
            comandoLower.contains("ayuda") -> {
                responder("Puedo decirte la hora, la fecha, contarte chistes, abrir aplicaciones, leer notificaciones, o buscar en internet.")
            }
            else -> responder("No entendí el comando")
        }
    }

    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "whats" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "face" to "com.facebook.katana",
        "tiktok" to "com.zhiliaoapp.musically",
        "tik" to "com.zhiliaoapp.musically",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "fotos" to "com.coloros.gallery3d",
        "cámara" to "com.oplus.camera",
        "camara" to "com.oplus.camera",
        "camera" to "com.oplus.camera",
        "spotify" to "com.spotify.music"
    )

    private fun abrirAplicacion(nombreApp: String) {
        try {
            Log.d(TAG, "📱 Intentando abrir: $nombreApp")
            val nombreNormalizado = nombreApp.lowercase().trim()

            // ✅ URI schemes PRIMERO para WhatsApp y TikTok
            if (nombreNormalizado.contains("whats") || nombreNormalizado.contains("what")) {
                Log.d(TAG, "🔗 WhatsApp detectado - URI scheme")
                if (abrirConUriScheme("whatsapp://send", nombreNormalizado)) return
            }

            if (nombreNormalizado.contains("tik")) {
                Log.d(TAG, "🔗 TikTok detectado - URI schemes")
                if (abrirConUriScheme("snssdk1128://", nombreNormalizado)) return
                if (abrirConUriScheme("tiktok://", nombreNormalizado)) return
            }

            // Buscar en mapa
            val packageName = appPackages[nombreNormalizado]
            if (packageName != null) {
                Log.d(TAG, "🔍 Package del mapa: $packageName")
                if (abrirAppDirecto(packageName, nombreNormalizado)) return
            }

            // Búsqueda dinámica
            Log.d(TAG, "🔍 Buscando dinámicamente")
            val apps = packageManager.getInstalledApplications(0)
            for (app in apps) {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName) ?: continue
                val appName = packageManager.getApplicationLabel(app).toString().lowercase()
                if (appName.contains(nombreNormalizado) || nombreNormalizado.contains(appName)) {
                    Log.d(TAG, "✅ Encontrada: $appName")
                    if (abrirAppDirecto(app.packageName, nombreNormalizado)) return
                }
            }

            responder("No encontré $nombreApp")
            Log.d(TAG, "❌ No encontrada")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            responder("Error al buscar")
        }
    }

    private fun abrirConUriScheme(uriScheme: String, nombreAmigable: String): Boolean {
        return try {
            Log.d(TAG, "🔗 URI: $uriScheme")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriScheme))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            responder("Abriendo $nombreAmigable")
            Log.d(TAG, "✅ URI exitoso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ URI falló: ${e.message}")
            false
        }
    }

    private fun abrirAppDirecto(packageName: String, nombreAmigable: String): Boolean {
        try {
            Log.d(TAG, "🚀 Abriendo: $packageName")
            var intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                responder("Abriendo $nombreAmigable")
                Log.d(TAG, "✅ Exitoso")
                return true
            }
            Log.d(TAG, "⚠️ No se pudo abrir")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            voskDetector.stopListening()
            voskDetector.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando Vosk: ${e.message}")
        }
        tts?.shutdown()
        mediaPlayer?.release()
        Log.d(TAG, "🔚 Activity cerrada")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ Permiso concedido")
                switchEva.isChecked = true
            } else {
                Log.e(TAG, "❌ Permiso denegado")
                Toast.makeText(this, "Necesitas dar permiso de micrófono", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun activarModoEva() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            switchEva.isChecked = false
            return
        }
        modoEvaActivo = true
        tvEstadoEva.text = "Esperando 'EVA'..."
        Handler(Looper.getMainLooper()).postDelayed({
            voskDetector.startListening()
            Log.d(TAG, "✅ EVA activado")
        }, 500)
    }

    private fun desactivarModoEva() {
        modoEvaActivo = false
        tvEstadoEva.text = "Di 'EVA' seguido de tu comando"
        try {
            voskDetector.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
        Log.d(TAG, "🔇 EVA desactivado")
    }

    private fun guardarEstadoEva(activo: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EVA_ACTIVE, activo).apply()
        Log.d(TAG, "💾 Estado guardado: $activo")
    }

    private fun inicializarVosk() {
        voskDetector = VoskHotwordDetector(this) {
            evaDetectadoConVosk()
        }
        voskDetector.initialize(
            onReady = {
                Log.d(TAG, "✅ Vosk listo")
                restaurarEstadoEva()
            },
            onError = { error ->
                Log.e(TAG, "❌ Error Vosk: $error")
                runOnUiThread {
                    tvEstadoEva.text = "Error inicializando EVA"
                }
            }
        )
    }

    private fun restaurarEstadoEva() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val evaActivo = prefs.getBoolean(KEY_EVA_ACTIVE, false)
        Log.d(TAG, "📱 Estado guardado: $evaActivo")
        switchEva.isChecked = evaActivo
        if (evaActivo) {
            activarModoEva()
        }
    }

    private fun evaDetectadoConVosk() {
        Log.d(TAG, "🎯 EVA DETECTADO")
        runOnUiThread {
            try {
                vibrator.vibrate(100)
            } catch (e: Exception) {}
            tvEstadoEva.text = "🎤 EVA activado"
            Handler(Looper.getMainLooper()).postDelayed({
                iniciarEscuchaComandoConGoogle()
            }, 500)
        }
    }

    private fun iniciarEscuchaComandoConGoogle() {
        val commandRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        commandRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "✅ Listo")
            }
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 Detectando")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e(TAG, "❌ Error: $error")
                runOnUiThread {
                    tvEstadoEva.text = "No te entendí"
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (modoEvaActivo) {
                            voskDetector.startListening()
                            tvEstadoEva.text = "👂 Esperando 'EVA'..."
                        }
                    }, 2000)
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val comando = matches[0]
                    Log.d(TAG, "✅ COMANDO: $comando")
                    runOnUiThread {
                        tvUltimoComando.text = "EVA: $comando"
                        tvEstadoEva.text = "Procesando..."
                        procesarComando(comando)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (modoEvaActivo) {
                                voskDetector.startListening()
                                tvEstadoEva.text = "👂 Esperando 'EVA'..."
                            }
                        }, 3000)
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        commandRecognizer.startListening(intent)
    }
}