package com.trabajogrado.asistente

import android.content.Intent
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

    // ✅ NUEVO: Código de permiso
    private val REQUEST_RECORD_AUDIO = 1

    // ✅ SharedPreferences
    private val PREFS_NAME = "EvaPreferences"
    private val KEY_EVA_ACTIVE = "eva_active"

    // UI
    private lateinit var tvUltimoComando: TextView
    private lateinit var tvRespuesta: TextView
    private lateinit var switchEva: SwitchCompat
    private lateinit var tvEstadoEva: TextView

    // Speech
    // Speech
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    // ✅ NUEVO: Sistema EVA con Vosk
    private var modoEvaActivo = false
    private lateinit var voskDetector: VoskHotwordDetector

    // Vibrador
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistente_voz_nuevo)

        inicializarVistas()
        inicializarTTS()
        configurarListeners()
        inicializarVosk()
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
            if (isChecked) {
                activarModoEva()
            } else {
                desactivarModoEva()
            }
        }
    }

    // ===== COMANDOS =====

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
            "¿Por qué el libro de matemáticas está triste? Porque tiene muchos problemas",
            "¿Qué le dice una impresora a otra? Esa hoja es tuya o es impresión mía",
            "¿Cómo se llama el campeón de buceo japonés? Tokofondo",
            "¿Cuál es el colmo de un electricista? Que su hijo sea un poco corto",
            "¿Por qué las focas del circo miran hacia arriba? Porque es donde están los focos",
            "¿Qué le dice un semáforo a otro? No me mires que me estoy cambiando"
        )

        val chisteAleatorio = chistes.random()
        Log.d(TAG, "😄 Chiste: $chisteAleatorio")
        responder(chisteAleatorio)
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

    private fun mostrarRespuesta(mensaje: String) {
        tvRespuesta.text = mensaje
    }

    private fun procesarComando(comando: String) {
        Log.d(TAG, "🔄 Procesando: $comando")

        val comandoLower = comando.lowercase().trim()

        when {
            // Hora
            comandoLower.contains("hora") || comandoLower.contains("qué hora") -> {
                decirHora()
            }

            // Fecha
            comandoLower.contains("fecha") ||
                    comandoLower.contains("qué día") ||
                    comandoLower.contains("que dia") -> {
                decirFecha()
            }

            // Chiste
            comandoLower.contains("chiste") || comandoLower.contains("cuéntame") -> {
                contarChiste()
            }

            // Clima
            comandoLower.contains("clima") ||
                    comandoLower.contains("tiempo") ||
                    comandoLower.contains("temperatura") -> {
                buscarClima()
            }

            // Buscar en internet
            comandoLower.contains("busca") || comandoLower.contains("buscar") -> {
                val query = comandoLower
                    .replace("busca", "")
                    .replace("buscar", "")
                    .replace("en google", "")
                    .replace("en internet", "")
                    .trim()
                buscarEnInternet(query)
            }

            // Ayuda
            comandoLower.contains("ayuda") -> {
                responder("Puedo decir la hora, la fecha, contar chistes, buscar en internet, y más")
            }

            else -> {
                responder("No entendí el comando. Di 'ayuda' para ver qué puedo hacer")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // ✅ Solo detener Vosk, NO cambiar el estado guardado
        try {
            voskDetector.stopListening()
            voskDetector.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando Vosk: ${e.message}")
        }

        tts?.shutdown()

        Log.d(TAG, "🔚 Activity cerrada")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ Permiso de micrófono concedido")
                // Reintentar activar EVA
                switchEva.isChecked = true
            } else {
                Log.e(TAG, "❌ Permiso de micrófono denegado")
                Toast.makeText(this, "Necesitas dar permiso de micrófono para usar EVA", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ===== SISTEMA EVA =====

    private fun activarModoEva() {
        // ✅ Verificar permiso de micrófono
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            switchEva.isChecked = false
            return
        }

        modoEvaActivo = true
        tvEstadoEva.text = "👂 Esperando 'EVA'..."

        // ✅ Guardar estado
        guardarEstadoEva(true)

        // ✅ Esperar 500ms antes de iniciar Vosk
        Handler(Looper.getMainLooper()).postDelayed({
            voskDetector.startListening()
            Log.d(TAG, "✅ Modo EVA activado")
        }, 500)
    }

    private fun desactivarModoEva() {
        modoEvaActivo = false
        tvEstadoEva.text = "Di 'EVA' seguido de tu comando"

        // ✅ Guardar estado
        guardarEstadoEva(false)

        try {
            voskDetector.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo Vosk: ${e.message}")
        }

        Log.d(TAG, "🔇 Modo EVA desactivado")
    }

    private fun guardarEstadoEva(activo: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EVA_ACTIVE, activo).apply()
        Log.d(TAG, "💾 Estado de EVA guardado: $activo")
    }
    // ===== VOSK HOTWORD =====

    private fun inicializarVosk() {
        voskDetector = VoskHotwordDetector(this) {
            // ✅ Callback cuando detecta "EVA"
            evaDetectadoConVosk()
        }

        voskDetector.initialize(
            onReady = {
                Log.d(TAG, "✅ Vosk listo para hotword detection")

                // ✅ RESTAURAR ESTADO DESPUÉS de que Vosk esté listo
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

        Log.d(TAG, "📱 Estado guardado de EVA: $evaActivo")

        // ✅ Restaurar switch SIN activar el listener
        switchEva.setOnCheckedChangeListener(null)
        switchEva.isChecked = evaActivo
        switchEva.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activarModoEva()
            } else {
                desactivarModoEva()
            }
        }

        // ✅ Activar EVA si estaba activo
        if (evaActivo) {
            activarModoEva()
        }
    }

    private fun evaDetectadoConVosk() {
        Log.d(TAG, "🎯 ¡EVA DETECTADO POR VOSK!")

        runOnUiThread {
            try {
                vibrator.vibrate(100)
            } catch (e: Exception) {}

            tvEstadoEva.text = "🎤 EVA activado, di tu comando..."

            // ✅ Esperar 500ms y escuchar comando con Google
            Handler(Looper.getMainLooper()).postDelayed({
                iniciarEscuchaComandoConGoogle()
            }, 500)
        }
    }

    private fun iniciarEscuchaComandoConGoogle() {
        // Crear recognizer temporal para el comando
        val commandRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        commandRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "✅ Listo para comando")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 Detectando comando...")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.e(TAG, "❌ Error comando: $error")

                runOnUiThread {
                    tvEstadoEva.text = "No te entendí, di 'EVA' de nuevo"

                    // Volver a escuchar "EVA"
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

                        // Volver a escuchar "EVA" después de 3 segundos
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (modoEvaActivo) {
                                voskDetector.startListening()
                                tvEstadoEva.text = "👂 Esperando 'EVA'..."
                            }
                        }, 3000)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Mostrar parciales opcionalmente
            }

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