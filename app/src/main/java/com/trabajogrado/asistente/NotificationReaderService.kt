package com.trabajogrado.asistente

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class NotificationReaderService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val TAG = "NotificationReader"
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    private val snapshotInicial = HashMap<String, Int>()
    private val ultimoTextoLeido = LinkedHashMap<String, String>()
    private val MAX_ULTIMOS_TEXTOS = 50
    private val llamadasAnunciadas = LinkedHashMap<String, Long>()

    // CORRECCIÓN 2: detiene TTS cuando el usuario presiona el botón de bloqueo/apagado
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                tts?.stop()
                Log.d(TAG, "🔲 Pantalla apagada — TTS detenido")
            }
        }
    }

    companion object {
        var instance: NotificationReaderService? = null

        fun getNotificacionesActivas(service: NotificationReaderService): List<String> {
            return try {
                service.activeNotifications
                    .filter { sbn ->
                        val pkg = sbn.packageName
                        !pkg.startsWith("android") &&
                                !pkg.startsWith("com.android.systemui") &&
                                !pkg.startsWith("com.miui") &&
                                !pkg.startsWith("com.xiaomi")
                    }
                    .mapNotNull { sbn ->
                        val titulo = sbn.notification.extras
                            .getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                        val texto = sbn.notification.extras
                            .getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        val app = service.getAppName(sbn.packageName)
                        if (titulo.isNotEmpty() || texto.isNotEmpty())
                            "$app: $titulo. $texto"
                        else null
                    }
            } catch (e: Exception) { emptyList() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "✅ Servicio de notificaciones creado")
        tts = TextToSpeech(this, this)
        // CORRECCIÓN 2: registrar el receiver al crear el servicio
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        capturarSnapshotInicial()
    }

    private fun capturarSnapshotInicial() {
        snapshotInicial.clear()
        try {
            activeNotifications?.forEach { sbn ->
                val titulo = sbn.notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val texto = sbn.notification.extras
                    .getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                snapshotInicial[sbn.key] = "$titulo|$texto".hashCode()
            }
            Log.d(TAG, "📋 Snapshot inicial: ${snapshotInicial.size} notificaciones preexistentes")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error capturando snapshot: ${e.message}")
        }
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

            // Ignorar notificaciones propias de EVA y las del sistema Android
            // ("android" emite p.ej. "EVA se muestra sobre otras apps" al activar el overlay)
            if (packageName == applicationContext.packageName || packageName == "android") return

            Log.d(TAG, "📬 Notificación de: $packageName | key: ${sbn.key}")

            if (esLlamadaEntrante(sbn)) {
                manejarLlamadaEntrante(sbn)
                return
            }

            if (!isLecturaAutomaticaActivada()) return

            // CORRECCIÓN 3: filtrar por packageName (no por nombre de app)
            if (!isAppPermitida(packageName)) {
                Log.d(TAG, "🚫 App no permitida: $packageName")
                return
            }

            val appName = getAppName(packageName)
            val titulo = notification.extras
                .getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val texto = notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            if (titulo.isEmpty() && texto.isEmpty()) return

            val contenidoHash = "$titulo|$texto".hashCode()
            val hashSnapshot = snapshotInicial[sbn.key]
            if (hashSnapshot != null) {
                if (hashSnapshot == contenidoHash) {
                    Log.d(TAG, "📋 Preexistente sin cambios, omitida")
                    return
                } else {
                    snapshotInicial.remove(sbn.key)
                }
            }

            if (ultimoTextoLeido[sbn.key] == texto) {
                Log.d(TAG, "🔇 Texto sin cambios, omitido")
                return
            }

            ultimoTextoLeido[sbn.key] = texto
            if (ultimoTextoLeido.size > MAX_ULTIMOS_TEXTOS) {
                ultimoTextoLeido.entries.iterator().let { it.next(); it.remove() }
            }

            Log.d(TAG, "📱 $appName | 📌 $titulo | 💬 $texto")
            leerNotificacion(appName, titulo, texto)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando notificación: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (esLlamadaEntrante(sbn)) {
            llamadasAnunciadas.remove(sbn.key)
            tts?.stop()
            Log.d(TAG, "🔄 Llamada finalizada: ${sbn.key}")
        }
        ultimoTextoLeido.remove(sbn.key)
        snapshotInicial.remove(sbn.key)
    }

    private fun esLlamadaEntrante(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (notification.category == Notification.CATEGORY_CALL) return true
        val packageName = sbn.packageName
        if (packageName.contains("dialer", ignoreCase = true) ||
            packageName.contains("phone", ignoreCase = true) ||
            packageName.contains("call", ignoreCase = true) ||
            packageName == "com.android.server.telecom") return true
        if ((notification.flags and Notification.FLAG_INSISTENT) != 0) return true
        return false
    }

    // Búsqueda de contacto en hilo secundario con fallback de 1500 ms.
    // Nunca bloquea el hilo de callbacks del NotificationListenerService.
    private fun manejarLlamadaEntrante(sbn: StatusBarNotification) {
        val claveLlamada = sbn.key
        val ahora = System.currentTimeMillis()

        val ultimaVez = llamadasAnunciadas[claveLlamada]
        if (ultimaVez != null && (ahora - ultimaVez) < 30_000L) {
            Log.d(TAG, "🔇 Llamada ya anunciada: $claveLlamada")
            return
        }

        llamadasAnunciadas[claveLlamada] = ahora
        limpiarCacheLlamadas()

        val extras = sbn.notification.extras
        // valorBruto puede ser vacío si la notificación no trae título ni texto
        val valorBruto = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()?.takeIf { it.isNotBlank() }
            ?: ""

        // Centraliza el speak para que el check de ttsListo y el utteranceId
        // estén siempre presentes, sin importar desde qué rama se llame.
        fun anunciarLlamada(nombre: String) {
            if (!ttsListo) {
                Log.e(TAG, "❌ TTS no listo, no se puede anunciar la llamada")
                return
            }
            val texto = if (nombre.isNotBlank()) "Llamada entrante de $nombre"
                        else "Llamada de número desconocido"
            val uid = "llamada_${System.currentTimeMillis()}"
            Log.d(TAG, "📞 Anunciando: $texto")
            tts?.stop()
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, uid)
        }

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val anunciado = AtomicBoolean(false)

        // Fallback a 1500 ms: si el hilo de contacto no respondió, anuncia con valorBruto
        val fallbackRunnable = Runnable {
            if (anunciado.compareAndSet(false, true)) {
                Log.d(TAG, "📞 [1.5 s fallback] valorBruto='$valorBruto'")
                anunciarLlamada(valorBruto)
            }
        }
        mainHandler.postDelayed(fallbackRunnable, 1_500L)

        // Hilo secundario: consulta ContactsContract con Dispatchers.IO equivalente
        Thread {
            val nombre = buscarNombreContacto(valorBruto)
            mainHandler.post {
                mainHandler.removeCallbacks(fallbackRunnable)
                if (anunciado.compareAndSet(false, true)) {
                    val callerName = nombre?.takeIf { it.isNotBlank() } ?: valorBruto
                    Log.d(TAG, "📞 [contacto] callerName='$callerName'")
                    anunciarLlamada(callerName)
                }
            }
        }.start()
    }

    private fun buscarNombreContacto(numeroPosible: String): String? {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null

        val soloDigitos = numeroPosible.replace(Regex("[\\s\\-\\+\\(\\)\\.#\\*]"), "")
        if (soloDigitos.length < 4 || !soloDigitos.all { it.isDigit() }) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(numeroPosible)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando contacto: ${e.message}")
            null
        }
    }

    private fun limpiarCacheLlamadas() {
        val ahora = System.currentTimeMillis()
        val iter = llamadasAnunciadas.entries.iterator()
        while (iter.hasNext()) {
            if (ahora - iter.next().value > 60_000L) iter.remove()
        }
    }

    private fun leerNotificacion(appName: String, titulo: String, texto: String) {
        if (!ttsListo) {
            Log.e(TAG, "❌ TTS no está listo")
            return
        }

        val prefs = getSharedPreferences("config_notificaciones", Context.MODE_PRIVATE)
        val tipoPrefijo = prefs.getString("prefijo_lectura", "notificacion") ?: "notificacion"

        val mensaje = buildString {
            when (tipoPrefijo) {
                "notificacion" -> append("Notificación de $appName. ")
                "app_solo", "sin_prefijo", "mensaje" -> append("$appName. ")
                "solo_mensaje" -> { /* sin prefijo ni nombre de app */ }
                else -> append("Notificación de $appName. ")
            }
            if (titulo.isNotEmpty()) append("$titulo. ")
            if (texto.isNotEmpty() && texto != titulo) append(texto)
        }

        Log.d(TAG, "🔊 Leyendo: $mensaje")
        hablarEnFragmentos(mensaje)
    }

    // Habla un texto de cualquier longitud respetando el límite del motor TTS (~4000 chars).
    // El primer fragmento usa [primerModo] (QUEUE_FLUSH o QUEUE_ADD); los siguientes QUEUE_ADD.
    private fun hablarEnFragmentos(texto: String, primerModo: Int = TextToSpeech.QUEUE_ADD) {
        if (!ttsListo || texto.isBlank()) return
        val maxChars = 3500
        if (texto.length <= maxChars) {
            tts?.speak(texto, primerModo, null, "msg_${System.currentTimeMillis()}")
            return
        }
        dividirTexto(texto, maxChars).forEachIndexed { i, fragmento ->
            val modo = if (i == 0) primerModo else TextToSpeech.QUEUE_ADD
            tts?.speak(fragmento, modo, null, "msg_part_$i")
        }
    }

    // Divide un texto largo en fragmentos de hasta [maxChars] caracteres.
    // Prioriza cortar en ". ", luego en "\n", luego en el último espacio.
    private fun dividirTexto(texto: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        var pos = 0
        while (pos < texto.length) {
            if (texto.length - pos <= maxChars) {
                result.add(texto.substring(pos).trim())
                break
            }
            val segmento = texto.substring(pos, pos + maxChars)
            val mitad = maxChars / 2
            val corteLocal = segmento.lastIndexOf(". ").takeIf { it > mitad }?.plus(2)
                ?: segmento.lastIndexOf('\n').takeIf { it > mitad }?.plus(1)
                ?: segmento.lastIndexOf(' ').takeIf { it > mitad }
                ?: maxChars
            result.add(texto.substring(pos, pos + corteLocal).trim())
            pos += corteLocal
            while (pos < texto.length && texto[pos] == ' ') pos++
        }
        return result.filter { it.isNotEmpty() }
    }

    private fun isLecturaAutomaticaActivada(): Boolean {
        val prefs = getSharedPreferences("config_notificaciones", Context.MODE_PRIVATE)
        return prefs.getBoolean("lectura_automatica", false)
    }

    // CORRECCIÓN 3: clave = packageName en SharedPreferences "apps_seleccionadas"
    private fun isAppPermitida(packageName: String): Boolean {
        val prefs = getSharedPreferences("apps_seleccionadas", Context.MODE_PRIVATE)
        return prefs.getBoolean(packageName, true)
    }

    fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
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
        instance = null
        // CORRECCIÓN 2: desregistrar el receiver al destruir el servicio
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
        tts?.stop()
        tts?.shutdown()
        llamadasAnunciadas.clear()
        ultimoTextoLeido.clear()
        snapshotInicial.clear()
        Log.d(TAG, "🔚 Servicio de notificaciones destruido")
    }
}
