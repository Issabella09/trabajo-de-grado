package com.trabajogrado.asistente

import android.content.Intent
import android.content.pm.PackageManager
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

                // ✅ SIN FILTROS - mostrar todas
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
        Log.d(TAG, "🎯 Procesando comando: $comando")

        val comandoLower = comando.lowercase()

        when {
            // ✅ Abrir aplicaciones
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

            comandoLower.contains("hora") -> {
                decirHora()
            }

            comandoLower.contains("fecha") -> {
                decirFecha()
            }

            comandoLower.contains("chiste") -> {
                contarChiste()
            }

            comandoLower.contains("clima") || comandoLower.contains("tiempo") -> {
                buscarClima()
            }

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
                responder("Puedo decirte la hora, la fecha, contarte chistes, abrir aplicaciones, o buscar en internet. Solo di EVA seguido de tu comando.")
            }

            else -> {
                responder("No entendí el comando. Di ayuda para conocer qué puedo hacer.")
            }
        }
    }

    // ✅ Mapa de nombres comunes a package names (con variantes conocidas)
    private val appPackages = mapOf(
        // Redes sociales - Facebook (múltiples variantes)
        "facebook" to "com.facebook.katana",
        "face" to "com.facebook.katana",
        "whatsapp" to "com.whatsapp",
        "whats" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "messenger" to "com.facebook.orca",
        "telegram" to "org.telegram.messenger",
        "twitter" to "com.twitter.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "snapchat" to "com.snapchat.android",
        "linkedin" to "com.linkedin.android",

        // Google Apps
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "maps" to "com.google.android.apps.maps",
        "mapas" to "com.google.android.apps.maps",
        "fotos" to "com.coloros.gallery3d",
        "photos" to "com.coloros.gallery3d",
        "galería" to "com.coloros.gallery3d",
        "galeria" to "com.coloros.gallery3d",
        "gallery" to "com.coloros.gallery3d",
        "drive" to "com.google.android.apps.docs",
        "calendar" to "com.google.android.calendar",
        "calendario" to "com.google.android.calendar",
        "play store" to "com.android.vending",
        "tienda" to "com.android.vending",

        // Sistema
        "configuración" to "com.android.settings",
        "configuracion" to "com.android.settings",
        "ajustes" to "com.android.settings",
        "settings" to "com.android.settings",
        "teléfono" to "com.android.dialer",
        "telefono" to "com.android.dialer",
        "llamadas" to "com.android.dialer",
        "phone" to "com.android.dialer",
        "mensajes" to "com.google.android.apps.messaging",
        "messages" to "com.google.android.apps.messaging",
        "contactos" to "com.android.contacts",
        "contacts" to "com.android.contacts",
        "cámara" to "com.oplus.camera",
        "camara" to "com.oplus.camera",
        "camera" to "com.oplus.camera",
        "reloj" to "com.google.android.deskclock",
        "clock" to "com.google.android.deskclock",
        "alarma" to "com.google.android.deskclock",
        "calculadora" to "com.google.android.calculator",
        "calculator" to "com.google.android.calculator",

        // Otras apps comunes
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "uber" to "com.ubercab",
        "rappi" to "com.grability.rappi",
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "outlook" to "com.microsoft.office.outlook",
        "discord" to "com.discord",
        "slack" to "com.slack"
    )

    // ✅ Packages alternativos conocidos para apps populares
    private val alternativePackages = mapOf(
        "facebook" to listOf(
            "com.facebook.katana",      // Facebook oficial
            "com.facebook.lite",         // Facebook Lite
            "com.facebook.android"       // Facebook alternativo
        ),
        "whatsapp" to listOf(
            "com.whatsapp",             // WhatsApp oficial
            "com.whatsapp.w4b"          // WhatsApp Business
        )
    )

    private fun abrirAplicacion(nombreApp: String) {
        try {
            Log.d(TAG, "📱 Intentando abrir: $nombreApp")

            val nombreNormalizado = nombreApp.lowercase().trim()
            Log.d(TAG, "🔍 Nombre normalizado: $nombreNormalizado")

            // ✅ PASO 1: Buscar en el mapa primero
            val packageName = appPackages[nombreNormalizado]

            if (packageName != null) {
                Log.d(TAG, "🔍 Package del mapa: $packageName")
                if (abrirAppDirecto(packageName, nombreNormalizado)) {
                    return
                }
            }

            // ✅ PASO 1.5: Probar packages alternativos si existen
            val packagesAlternativos = alternativePackages[nombreNormalizado]
            if (packagesAlternativos != null) {
                Log.d(TAG, "🔍 Probando packages alternativos para: $nombreNormalizado")
                for (altPackage in packagesAlternativos) {
                    Log.d(TAG, "  Intentando: $altPackage")
                    if (abrirAppDirecto(altPackage, nombreNormalizado)) {
                        return
                    }
                }
            }

            // ✅ PASO 2: Búsqueda dinámica MEJORADA - buscar SOLO entre apps lanzables
            Log.d(TAG, "🔍 Buscando dinámicamente: $nombreNormalizado")
            val apps = packageManager.getInstalledApplications(0)
            var mejorCandidato: String? = null
            var mejorScore = 0

            for (app in apps) {
                // ✅ SOLO considerar apps que sean lanzables
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent == null) continue

                val appName = packageManager.getApplicationLabel(app).toString().lowercase()
                val packageNameActual = app.packageName.lowercase()

                // Calcular similitud
                var score = 0

                // Match exacto de nombre
                if (appName == nombreNormalizado) {
                    score = 1000
                }
                // Contiene el nombre completo
                else if (appName.contains(nombreNormalizado) || nombreNormalizado.contains(appName)) {
                    score += 100
                }
                // Package name contiene la palabra
                else if (packageNameActual.contains(nombreNormalizado)) {
                    score += 50
                }

                // ✅ Palabras clave específicas con alta prioridad
                if (nombreNormalizado.contains("whats") && (appName.contains("whatsapp") || packageNameActual.contains("whatsapp"))) {
                    score = 200
                }
                if (nombreNormalizado.contains("face") && (appName.contains("facebook") || packageNameActual.contains("facebook"))) {
                    score = 200
                }
                if (nombreNormalizado.contains("insta") && (appName.contains("instagram") || packageNameActual.contains("instagram"))) {
                    score = 200
                }
                if (nombreNormalizado.contains("cám") && (appName.contains("cámara") || appName.contains("camera") || packageNameActual.contains("camera"))) {
                    score = 200
                }
                if (nombreNormalizado.contains("spot") && (appName.contains("spotify") || packageNameActual.contains("spotify"))) {
                    score = 200
                }

                if (score > mejorScore) {
                    mejorScore = score
                    mejorCandidato = app.packageName
                    Log.d(TAG, "  ✅ Candidato: $appName ($packageNameActual) score: $score")
                }
            }

            if (mejorCandidato != null && mejorScore > 0) {
                Log.d(TAG, "🎯 Mejor candidato: $mejorCandidato (score: $mejorScore)")
                if (abrirAppDirecto(mejorCandidato, nombreNormalizado)) {
                    return
                }
            }

            // ✅ PASO 3: Si nada funcionó, buscar apps similares y mostrarlas
            Log.d(TAG, "🔍 Buscando apps con nombres similares a: $nombreNormalizado")

            val appsSimilares = apps.filter { app ->
                val appName = packageManager.getApplicationLabel(app).toString().lowercase()
                val packageNameActual = app.packageName.lowercase()
                appName.contains(nombreNormalizado) ||
                        packageNameActual.contains(nombreNormalizado) ||
                        nombreNormalizado.contains(appName.split(" ").firstOrNull() ?: "")
            }

            if (appsSimilares.isNotEmpty()) {
                Log.d(TAG, "📱 Apps similares encontradas:")
                appsSimilares.take(5).forEach { app ->
                    val appName = packageManager.getApplicationLabel(app).toString()
                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                    val esLanzable = if (launchIntent != null) "✅ LANZABLE" else "❌ NO LANZABLE"
                    Log.d(TAG, "  - $appName (${app.packageName}) $esLanzable")
                }
            }

            responder("No encontré $nombreApp. ¿Está instalada?")
            Log.d(TAG, "❌ No encontrada: $nombreApp")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            responder("Error al buscar la aplicación")
        }
    }

    private fun abrirAppDirecto(packageName: String, nombreAmigable: String): Boolean {
        try {
            Log.d(TAG, "🚀 Intentando abrir: $packageName")

            // ✅ MÉTODO 1: Intent normal (el más confiable)
            var intent = packageManager.getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                responder("Abriendo $nombreAmigable")
                Log.d(TAG, "✅ Método 1 exitoso")
                return true
            }

            Log.d(TAG, "⚠️ Método 1 falló, intentando comando shell...")

            // ✅ MÉTODO 2: Comando shell monkey
            val proceso = Runtime.getRuntime().exec("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
            proceso.waitFor()

            if (proceso.exitValue() == 0) {
                responder("Abriendo $nombreAmigable")
                Log.d(TAG, "✅ Método 2 exitoso (shell)")
                return true
            }

            Log.d(TAG, "⚠️ Método 2 falló, intentando método 3...")

            // ✅ MÉTODO 3: Intent genérico con MAIN/LAUNCHER
            val intentGenerico = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(intentGenerico)
            responder("Abriendo $nombreAmigable")
            Log.d(TAG, "✅ Método 3 exitoso")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error abriendo $packageName: ${e.message}", e)
            return false
        }
    }

    private fun estaAppInstalada(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }


    private fun hablar(mensaje: String) {
        responder(mensaje)
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

        // ✅ Esperar 500ms antes de iniciar Vosk
        Handler(Looper.getMainLooper()).postDelayed({
            voskDetector.startListening()
            Log.d(TAG, "✅ Modo EVA activado")
        }, 500)
    }

    private fun desactivarModoEva() {
        modoEvaActivo = false
        tvEstadoEva.text = "Di 'EVA' seguido de tu comando"

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
        switchEva.isChecked = evaActivo

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