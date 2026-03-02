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
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.widget.SwitchCompat

class AsistenteVozNuevoActivity : AppCompatActivity() {

    private val TAG = "AsistenteVozNuevo"

    // UI
    private lateinit var cardBoton: CardView
    private lateinit var btnHablar: RelativeLayout
    private lateinit var tvEstado: TextView
    private lateinit var tvUltimoComando: TextView
    private lateinit var tvRespuesta: TextView
    private lateinit var switchEva: SwitchCompat
    private lateinit var tvEstadoEva: TextView

    // Speech
    // Speech
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var escuchando = false
    private var ultimoComandoParcial = ""

    // ✅ NUEVO: Sistema EVA
    private var modoEvaActivo = false
    private var esperandoComandoDespuesDeEva = false
    private var escuchaActivaEva: SpeechRecognizer? = null

    // Vibrador
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistente_voz_nuevo)

        inicializarVistas()
        inicializarTTS()
        inicializarSpeechRecognizer()
        configurarListeners()
    }

    private fun inicializarVistas() {
        cardBoton = findViewById(R.id.card_boton_eva)
        btnHablar = findViewById(R.id.btn_hablar)
        tvEstado = findViewById(R.id.tv_estado)
        tvUltimoComando = findViewById(R.id.tv_ultimo_comando)
        tvRespuesta = findViewById(R.id.tv_respuesta)

        // ✅ NUEVO
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

    private fun inicializarSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "✅ Listo para escuchar")
                    runOnUiThread {
                        tvRespuesta.text = "Listo! Habla ahora..."
                    }
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "🎤 Detectando voz...")
                    runOnUiThread {
                        tvRespuesta.text = "Te estoy escuchando..."
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "🔚 Fin de voz detectado")
                    runOnUiThread {
                        tvRespuesta.text = "Procesando..."
                    }
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                        SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                        SpeechRecognizer.ERROR_CLIENT -> "Error de cliente"
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        else -> "Error $error"
                    }
                    Log.e(TAG, "❌ $errorMsg")

                    runOnUiThread {
                        detenerEscucha()

                        // ✅ Mensaje más claro según el error
                        val mensaje = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No te entendí. Habla más claro"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché nada. Intenta de nuevo"
                            SpeechRecognizer.ERROR_NETWORK -> "Sin internet. Intenta de nuevo"
                            else -> "Error. Intenta de nuevo"
                        }

                        mostrarRespuesta(mensaje)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                    if (matches != null && matches.isNotEmpty()) {
                        // ✅ Caso 1: Hay resultado final
                        val comando = matches[0]
                        Log.d(TAG, "✅ COMANDO FINAL: $comando")

                        runOnUiThread {
                            detenerEscucha()
                            tvUltimoComando.text = comando

                            Handler(Looper.getMainLooper()).postDelayed({
                                procesarComando(comando)
                            }, 300)
                        }

                    } else if (ultimoComandoParcial.isNotEmpty()) {
                        // ✅ Caso 2: No hay resultado final, PERO SÍ hay parcial
                        val comando = ultimoComandoParcial
                        Log.d(TAG, "✅ USANDO PARCIAL: $comando")

                        runOnUiThread {
                            detenerEscucha()
                            tvUltimoComando.text = comando

                            Handler(Looper.getMainLooper()).postDelayed({
                                procesarComando(comando)
                                ultimoComandoParcial = "" // Limpiar
                            }, 300)
                        }

                    } else {
                        // ✅ Caso 3: No hay NADA
                        Log.e(TAG, "❌ Sin resultados finales ni parciales")
                        runOnUiThread {
                            detenerEscucha()
                            mostrarRespuesta("No te entendí. Intenta de nuevo")
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val parcial = matches[0].lowercase().trim()
                        Log.d(TAG, "📝 EVA parcial: $parcial")

                        // ✅ DETECTAR "EVA" + COMANDO EN LA MISMA FRASE
                        if (parcial.contains("eva")) {
                            // Extraer el comando después de "eva"
                            val comando = parcial.substringAfter("eva").trim()

                            // ✅ SOLO PROCESAR SI EL COMANDO TIENE AL MENOS 3 PALABRAS
                            val palabras = comando.split(" ").filter { it.isNotBlank() }

                            if (palabras.size >= 2) {  // Al menos 2 palabras (ej: "qué hora")
                                Log.d(TAG, "🎯 EVA DETECTADO! Comando: $comando (${palabras.size} palabras)")

                                // ✅ Cancelar escucha actual
                                try {
                                    escuchaActivaEva?.cancel()
                                    escuchaActivaEva?.destroy()
                                    escuchaActivaEva = null
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error: ${e.message}")
                                }

                                // ✅ Procesar comando
                                runOnUiThread {
                                    try {
                                        vibrator.vibrate(100)
                                    } catch (e: Exception) {}

                                    tvEstadoEva.text = "✅ Procesando: $comando"
                                    tvUltimoComando.text = "EVA: $comando"

                                    Handler(Looper.getMainLooper()).postDelayed({
                                        procesarComando(comando)

                                        // ✅ Volver a escuchar después de 3 segundos
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            if (modoEvaActivo) {
                                                iniciarEscuchaContinua()
                                            }
                                        }, 3000)
                                    }, 300)
                                }
                            } else {
                                // Detectó "eva" + comando muy corto, seguir escuchando
                                Log.d(TAG, "⏳ EVA detectado, esperando comando completo... (${palabras.size} palabras)")
                                runOnUiThread {
                                    tvEstadoEva.text = "⏳ EVA detectado, sigue hablando..."
                                }
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Toast.makeText(this, "Reconocimiento de voz no disponible", Toast.LENGTH_LONG).show()
        }
    }

    private fun configurarListeners() {
        btnHablar.setOnClickListener {
            if (escuchando) {
                detenerEscucha()
            } else {
                iniciarEscucha()
            }
        }

        // ✅ NUEVO: Switch EVA
        switchEva.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activarModoEva()
            } else {
                desactivarModoEva()
            }
        }
    }

    private fun iniciarEscucha() {
        if (escuchando) return

        escuchando = true

        // Vibración
        try {
            vibrator.vibrate(50)
        } catch (e: Exception) {
            Log.e(TAG, "Vibración no disponible")
        }

        // Animación
        val animation = AnimationUtils.loadAnimation(this, R.anim.pulse)
        cardBoton.startAnimation(animation)

        // UI
        tvEstado.text = "ESCUCHANDO..."
        tvRespuesta.text = "Te estoy escuchando..."

        // ✅ DESTRUIR Y RECREAR Speech Recognizer
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destruyendo recognizer: ${e.message}")
        }

        // ✅ Crear nuevo recognizer
        inicializarSpeechRecognizer()

        // Speech
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)  // ✅ Activar parciales
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)  // ✅ 3 segundos
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)  // ✅ 3 segundos
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)  // ✅ Mínimo 5 segundos
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "🎤 Escuchando...")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando escucha: ${e.message}")
            detenerEscucha()
            mostrarRespuesta("Error al iniciar micrófono")
        }
    }

    private fun detenerEscucha() {
        escuchando = false

        // Detener animación
        cardBoton.clearAnimation()

        // UI
        tvEstado.text = "TOCA AQUÍ"

        // Speech - Cancelar y destruir
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo: ${e.message}")
        }

        Log.d(TAG, "🔇 Escucha detenida")
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

    override fun onDestroy() {
        super.onDestroy()

        // ✅ Limpiar EVA
        desactivarModoEva()

        speechRecognizer?.destroy()
        tts?.shutdown()

        Log.d(TAG, "🔚 Activity cerrada")
    }

    // ===== SISTEMA EVA =====

    private fun activarModoEva() {
        modoEvaActivo = true
        tvEstadoEva.text = "🎤 Di 'EVA' seguido de tu comando"

        iniciarEscuchaContinua()

        Log.d(TAG, "✅ Modo EVA activado")
    }

    private fun desactivarModoEva() {
        modoEvaActivo = false
        esperandoComandoDespuesDeEva = false
        tvEstadoEva.text = "Di 'EVA' seguido de tu comando"

        try {
            escuchaActivaEva?.cancel()
            escuchaActivaEva?.destroy()
            escuchaActivaEva = null
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo EVA: ${e.message}")
        }

        Log.d(TAG, "🔇 Modo EVA desactivado")
    }

    private fun iniciarEscuchaContinua() {
        if (!modoEvaActivo) return

        // Crear nuevo recognizer para escucha continua
        escuchaActivaEva = SpeechRecognizer.createSpeechRecognizer(this)

        escuchaActivaEva?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "👂 EVA listo")
                runOnUiThread {
                    tvEstadoEva.text = "🎤 Di 'EVA' + comando"
                }
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 EVA escuchando...")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "🔚 EVA fin de voz")
            }

            override fun onError(error: Int) {
                Log.d(TAG, "⚠️ EVA error: $error")

                // ✅ Reiniciar solo después de error
                Handler(Looper.getMainLooper()).postDelayed({
                    if (modoEvaActivo) {
                        iniciarEscuchaContinua()
                    }
                }, 1000)
            }

            override fun onResults(results: Bundle?) {
                Log.d(TAG, "📝 EVA onResults")

                // ✅ Reiniciar después de resultado
                Handler(Looper.getMainLooper()).postDelayed({
                    if (modoEvaActivo) {
                        iniciarEscuchaContinua()
                    }
                }, 1000)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val parcial = matches[0].lowercase().trim()
                    Log.d(TAG, "📝 EVA parcial: $parcial")

                    // ✅ DETECTAR "EVA" + COMANDO EN LA MISMA FRASE
                    if (parcial.contains("eva")) {
                        // Extraer el comando después de "eva"
                        val comando = parcial.substringAfter("eva").trim()

                        // ✅ CONTAR PALABRAS DEL COMANDO
                        val palabras = comando.split(" ").filter { it.isNotBlank() }

                        // ✅ SOLO PROCESAR SI TIENE AL MENOS 2 PALABRAS
                        if (palabras.size >= 2) {
                            Log.d(TAG, "🎯 COMANDO COMPLETO! '$comando' (${palabras.size} palabras)")

                            // ✅ Cancelar escucha actual
                            try {
                                escuchaActivaEva?.cancel()
                                escuchaActivaEva?.destroy()
                                escuchaActivaEva = null
                            } catch (e: Exception) {
                                Log.e(TAG, "Error: ${e.message}")
                            }

                            // ✅ Procesar comando
                            runOnUiThread {
                                try {
                                    vibrator.vibrate(100)
                                } catch (e: Exception) {}

                                tvEstadoEva.text = "✅ Procesando: $comando"
                                tvUltimoComando.text = "EVA: $comando"

                                Handler(Looper.getMainLooper()).postDelayed({
                                    procesarComando(comando)

                                    // ✅ Volver a escuchar después de 3 segundos
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (modoEvaActivo) {
                                            iniciarEscuchaContinua()
                                        }
                                    }, 3000)
                                }, 300)
                            }
                        } else if (palabras.size == 1) {
                            // Solo 1 palabra, esperar más
                            Log.d(TAG, "⏳ EVA + 1 palabra ('$comando'), esperando más...")
                            runOnUiThread {
                                tvEstadoEva.text = "⏳ Sigue hablando..."
                            }
                        } else {
                            // Solo "eva", esperar comando
                            Log.d(TAG, "⏳ Solo 'EVA', esperando comando...")
                            runOnUiThread {
                                tvEstadoEva.text = "⏳ EVA detectado, di tu comando"
                            }
                        }
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "CO"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // ✅ 10 SEGUNDOS antes de timeout
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000)
        }

        try {
            escuchaActivaEva?.startListening(intent)
            Log.d(TAG, "👂 Escucha EVA iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }
    }
}