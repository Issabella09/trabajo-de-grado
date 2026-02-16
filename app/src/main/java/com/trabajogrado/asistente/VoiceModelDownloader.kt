package com.trabajogrado.asistente

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class VoiceModelDownloader(private val context: Context) {

    private val TAG = "VoiceModelDownloader"

    fun descargarModelosSiNoExisten(): File {
        val assetsDir = File(context.filesDir, "models")

        if (assetsDir.exists() && assetsDir.listFiles()?.isNotEmpty() == true) {
            Log.d(TAG, "✅ Modelos ya descargados")
            return assetsDir
        }

        Log.d(TAG, "📥 Descargando modelos de voz...")

        // Crear directorio
        val esDir = File(assetsDir, "es-es")
        esDir.mkdirs()

        // Por ahora, usamos el modelo incluido en PocketSphinx
        // La librería ya trae los archivos necesarios

        return assetsDir
    }
}