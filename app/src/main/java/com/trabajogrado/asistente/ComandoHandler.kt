package com.trabajogrado.asistente

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

class ComandoHandler(
    private val context: Context,
    private val textToSpeech: TextToSpeech? = null
) {
    private val TAG = "ComandoHandler"

    fun procesarComando(comando: String): Boolean {
        val comandoLower = comando.lowercase().trim()
        Log.d(TAG, "🔄 Procesando comando: '$comandoLower'")

        return when {
            // ========== ABRIR APLICACIONES ==========
            comandoLower.contains("abre") || comandoLower.contains("abrir") -> {
                abrirAplicacion(comandoLower)
            }

            // ========== NOTIFICACIONES ==========
            comandoLower.contains("notificaciones") || comandoLower.contains("notificación") -> {
                leerNotificaciones()
            }

            // ========== LEER PANTALLA ==========
            comandoLower.contains("lee la pantalla") ||
                    comandoLower.contains("qué hay en pantalla") ||
                    comandoLower.contains("que hay en pantalla") -> {
                leerPantalla()
            }

            // ========== LLAMADAS ==========
            comandoLower.contains("llama") || comandoLower.contains("llamar") -> {
                hacerLlamada(comandoLower)
            }

            // ========== MENSAJES ==========
            comandoLower.contains("envía mensaje") ||
                    comandoLower.contains("envia mensaje") ||
                    comandoLower.contains("manda mensaje") -> {
                enviarMensaje(comandoLower)
            }

            // ========== BÚSQUEDA EN INTERNET ==========
            comandoLower.contains("busca") || comandoLower.contains("buscar") -> {
                buscarEnInternet(comandoLower)
            }

            // ========== AYUDA ==========
            comandoLower.contains("ayuda") || comandoLower.contains("qué puedes hacer") -> {
                mostrarAyuda()
            }

            // ========== COMANDO NO RECONOCIDO ==========
            else -> {
                responder("No entendí el comando: $comando")
                false
            }
        }
    }

    // ========================================
    // ABRIR APLICACIONES
    // ========================================
    private fun abrirAplicacion(comando: String): Boolean {
        val app = when {
            comando.contains("whatsapp") || comando.contains("wasap") -> "com.whatsapp"
            comando.contains("facebook") || comando.contains("face") -> "com.facebook.katana"
            comando.contains("instagram") || comando.contains("insta") -> "com.instagram.android"
            comando.contains("youtube") -> "com.google.android.youtube"
            comando.contains("chrome") || comando.contains("navegador") -> "com.android.chrome"
            comando.contains("gmail") || comando.contains("correo") -> "com.google.android.gm"
            comando.contains("spotify") || comando.contains("música") -> "com.spotify.music"
            comando.contains("cámara") || comando.contains("camara") -> "com.android.camera"
            comando.contains("galería") || comando.contains("galeria") || comando.contains("fotos") -> "com.google.android.apps.photos"
            comando.contains("maps") || comando.contains("mapas") -> "com.google.android.apps.maps"
            comando.contains("calendario") -> "com.google.android.calendar"
            comando.contains("reloj") -> "com.google.android.deskclock"
            else -> null
        }

        return if (app != null) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    responder("Abriendo aplicación")
                    true
                } else {
                    responder("La aplicación no está instalada")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error abriendo app: ${e.message}")
                responder("No pude abrir la aplicación")
                false
            }
        } else {
            responder("No sé qué aplicación quieres abrir")
            false
        }
    }

    // ========================================
    // NOTIFICACIONES
    // ========================================
    private fun leerNotificaciones(): Boolean {
        try {
            // Abrir la actividad de notificaciones
            val intent = Intent(context, NotificacionesActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            responder("Abriendo notificaciones")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error abriendo notificaciones: ${e.message}")
            responder("No pude acceder a las notificaciones")
            return false
        }
    }

    // ========================================
    // LEER PANTALLA
    // ========================================
    private fun leerPantalla(): Boolean {
        // Aquí deberías comunicarte con LecturaPantallaService
        // Por ahora, mostrar mensaje
        responder("Para leer la pantalla, activa el servicio de accesibilidad en configuración")
        return true
    }

    // ========================================
    // LLAMADAS
    // ========================================
    private fun hacerLlamada(comando: String): Boolean {
        // Extraer nombre del contacto
        val nombre = extraerNombre(comando, listOf("llama a", "llamar a", "llama", "llamar"))

        return if (nombre.isNotEmpty()) {
            try {
                // Opción 1: Llamada directa con número
                val intent = Intent(Intent.ACTION_CALL)
                intent.data = Uri.parse("tel:$nombre") // Por ahora usa el nombre, luego buscaremos en contactos
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // Verificar permiso
                if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    context.startActivity(intent)
                    responder("Llamando a $nombre")
                    true
                } else {
                    // Mostrar marcador sin llamar automáticamente
                    val dialIntent = Intent(Intent.ACTION_DIAL)
                    dialIntent.data = Uri.parse("tel:$nombre")
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(dialIntent)
                    responder("Abriendo marcador")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error haciendo llamada: ${e.message}")
                responder("No pude hacer la llamada")
                false
            }
        } else {
            responder("¿A quién quieres llamar?")
            false
        }
    }

    // ========================================
    // MENSAJES
    // ========================================
    private fun enviarMensaje(comando: String): Boolean {
        val destinatario = extraerNombre(
            comando,
            listOf("envía mensaje a", "envia mensaje a", "manda mensaje a")
        )

        return if (destinatario.isNotEmpty()) {
            try {
                // Abrir WhatsApp o SMS
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("sms:$destinatario")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                responder("Abriendo mensajes para $destinatario")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando mensaje: ${e.message}")
                responder("No pude enviar el mensaje")
                false
            }
        } else {
            responder("¿A quién quieres enviar el mensaje?")
            false
        }
    }

    // ========================================
    // BÚSQUEDA EN INTERNET
    // ========================================
    private fun buscarEnInternet(comando: String): Boolean {
        val query = extraerQuery(comando, listOf("busca", "buscar", "busca en google", "buscar en google"))

        return if (query.isNotEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH)
                intent.putExtra("query", query)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                responder("Buscando: $query")
                true
            } catch (e: Exception) {
                // Alternativa: abrir en navegador
                try {
                    val url = "https://www.google.com/search?q=${Uri.encode(query)}"
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                    responder("Buscando: $query")
                    true
                } catch (e2: Exception) {
                    Log.e(TAG, "Error buscando: ${e2.message}")
                    responder("No pude hacer la búsqueda")
                    false
                }
            }
        } else {
            responder("¿Qué quieres buscar?")
            false
        }
    }

    // ========================================
    // AYUDA
    // ========================================
    private fun mostrarAyuda(): Boolean {
        val mensaje = """
            Puedo ayudarte con:
            - Abrir aplicaciones: di 'abre WhatsApp'
            - Leer notificaciones: di 'lee mis notificaciones'
            - Hacer llamadas: di 'llama a mamá'
            - Enviar mensajes: di 'envía mensaje a Juan'
            - Buscar en internet: di 'busca recetas de pasta'
        """.trimIndent()

        responder(mensaje)
        return true
    }

    // ========================================
    // UTILIDADES
    // ========================================
    private fun extraerNombre(comando: String, prefijos: List<String>): String {
        var texto = comando.lowercase()

        for (prefijo in prefijos) {
            if (texto.contains(prefijo)) {
                texto = texto.substringAfter(prefijo).trim()
                break
            }
        }

        return texto
    }

    private fun extraerQuery(comando: String, prefijos: List<String>): String {
        var texto = comando.lowercase()

        for (prefijo in prefijos) {
            if (texto.contains(prefijo)) {
                texto = texto.substringAfter(prefijo).trim()
                break
            }
        }

        // Limpiar palabras comunes
        texto = texto.replace("en google", "").trim()

        return texto
    }

    private fun responder(mensaje: String) {
        Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()

        // Si hay TTS configurado, hablar
        textToSpeech?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, null)

        Log.d(TAG, "💬 Respuesta: $mensaje")
    }
}