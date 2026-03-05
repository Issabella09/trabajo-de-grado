package com.trabajogrado.asistente

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.io.IOException

class VoskHotwordDetector(
    private val context: Context,
    private val onHotwordDetected: () -> Unit
) {
    private val TAG = "VoskHotwordDetector"

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = false
    private var hotwordDetected = false

    fun initialize(onReady: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val modelPath = File(context.filesDir, "vosk-model-small-es-0.42")

                Log.d(TAG, "📂 Verificando modelo en: ${modelPath.absolutePath}")

                if (!modelPath.exists()) {
                    Log.d(TAG, "📦 Copiando modelo desde assets...")
                    copyModelFromAssets(modelPath)
                }

                // ✅ CORRECCIÓN: Los archivos están en una subcarpeta
                val actualModelPath = File(modelPath, "vosk-model-small-es-0.42")

                Log.d(TAG, "📂 Path real del modelo: ${actualModelPath.absolutePath}")
                Log.d(TAG, "📂 Contenido:")
                actualModelPath.listFiles()?.forEach {
                    Log.d(TAG, "   - ${it.name} (${if (it.isDirectory) "DIR" else "FILE"})")
                }

                // Verificar carpetas necesarias en el path correcto
                val requiredDirs = listOf("am", "conf", "graph", "ivector")
                val missing = requiredDirs.filter { !File(actualModelPath, it).exists() }

                if (missing.isNotEmpty()) {
                    throw Exception("Faltan carpetas: $missing")
                }

                Log.d(TAG, "✅ Todas las carpetas necesarias presentes")

                // Cargar modelo con el path correcto
                model = Model(actualModelPath.absolutePath)

                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "✅ Modelo Vosk cargado")
                    onReady()
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Log.e(TAG, "❌ Error cargando modelo: ${e.message}")
                    e.printStackTrace()
                    onError(e.message ?: "Error desconocido")
                }
            }
        }.start()
    }

    private fun copyModelFromAssets(targetDir: File) {
        Log.d(TAG, "📦 Creando directorio: ${targetDir.absolutePath}")
        targetDir.mkdirs()

        Log.d(TAG, "📦 Iniciando copia recursiva...")
        copyAssetFolder("vosk-model-small-es-0.42", targetDir)

        Log.d(TAG, "📦 Modelo copiado completamente")
    }

    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assetManager = context.assets

        Log.d(TAG, "📁 Procesando: $assetPath")

        val files = assetManager.list(assetPath)

        if (files == null || files.isEmpty()) {
            Log.e(TAG, "❌ No se encontraron archivos en: $assetPath")
            return
        }

        Log.d(TAG, "📁 Encontrados ${files.size} items en $assetPath")

        for (filename in files) {
            val fullAssetPath = "$assetPath/$filename"
            val targetFile = File(targetDir, filename)

            val subFiles = assetManager.list(fullAssetPath)

            if (subFiles != null && subFiles.isNotEmpty()) {
                // Es una carpeta
                Log.d(TAG, "📂 Creando carpeta: ${targetFile.name}")
                targetFile.mkdirs()
                copyAssetFolder(fullAssetPath, targetFile)
            } else {
                // Es un archivo
                try {
                    Log.d(TAG, "📄 Copiando archivo: ${targetFile.name}")
                    assetManager.open(fullAssetPath).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "✅ Copiado: ${targetFile.name} (${targetFile.length()} bytes)")
                } catch (e: IOException) {
                    Log.e(TAG, "❌ Error copiando $fullAssetPath: ${e.message}")
                }
            }
        }
    }

    fun startListening() {
        if (isListening) return
        if (model == null) {
            Log.e(TAG, "❌ Modelo no inicializado")
            return
        }
        hotwordDetected = false

        try {
            val recognizer = Recognizer(model, 16000.0f, "[\"eva\"]")
            speechService = SpeechService(recognizer, 16000.0f)

            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let {
                        try {
                            val json = JSONObject(it)
                            val partial = json.optString("partial", "")

                            if (partial.contains("eva", ignoreCase = true) && !hotwordDetected) {
                                hotwordDetected = true  // ✅ Marcar como detectado

                                Log.d(TAG, "🎯 HOTWORD DETECTADO: $partial")

                                // ✅ Detener escucha
                                stopListening()

                                // ✅ Notificar en UI thread
                                Handler(Looper.getMainLooper()).post {
                                    onHotwordDetected()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing: ${e.message}")
                        }
                    }
                }

                override fun onResult(hypothesis: String?) {}
                override fun onFinalResult(hypothesis: String?) {}

                override fun onError(e: Exception?) {
                    Log.e(TAG, "Error Vosk: ${e?.message}")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isListening) {
                            stopListening()
                            startListening()
                        }
                    }, 1000)
                }

                override fun onTimeout() {}
            })

            isListening = true
            Log.d(TAG, "👂 Escuchando hotword 'EVA'...")

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando escucha: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            speechService = null
            isListening = false
            Log.d(TAG, "🔇 Hotword detector detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo: ${e.message}")
        }
    }

    fun cleanup() {
        stopListening()
        model?.close()
        model = null
    }
}