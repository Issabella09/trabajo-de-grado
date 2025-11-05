package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

import android.graphics.Bitmap
import android.graphics.BitmapFactory

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager

class OCRActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var btnTomarFoto: Button
    private lateinit var btnElegirGaleria: Button
    private lateinit var btnLeerVoz: Button
    private lateinit var btnVolver: Button
    private lateinit var txtResultado: TextView
    private lateinit var txtInstrucciones: TextView
    private val TAG = "OCRActivity"

    private companion object {
        const val REQUEST_CODE_GALERIA = 200
        const val REQUEST_CODE_PERMISO_ALMACENAMIENTO = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)

        Log.d(TAG, "OCRActivity iniciada")
        inicializarVistas()
        inicializarTextToSpeech()
    }

    private fun procesarImagenConOCR(bitmap: Bitmap) {
        Log.d(TAG, "Iniciando procesamiento OCR con ML Kit...")
        txtInstrucciones.text = "Analizando texto..."

        try {
            // CONVERTIR bitmap a imagen que ML Kit entienda
            val image = InputImage.fromBitmap(bitmap, 0)

            // CREAR el reconocedor de texto
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            // PROCESAR la imagen con ML Kit
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // ✅ ÉXITO: Texto detectado
                    val textoDetectado = visionText.text

                    if (textoDetectado.isNotEmpty()) {
                        Log.d(TAG, "Se detectó: $textoDetectado")
                        txtInstrucciones.text = "Texto detectado"

                        // Mostrar resultado
                        mostrarResultadoOCR("TEXTO DETECTADO:\n\n$textoDetectado")

                        // Leer automáticamente
                        hablar("Texto detectado: $textoDetectado")

                    } else {
                        // ❌ No se detectó texto
                        Log.d(TAG, "No se detectó texto")
                        txtInstrucciones.text = "No se encontró texto"
                        mostrarResultadoOCR("No se detectó texto en la imagen\n\nApunta a texto más claro o acércate más.")
                    }
                }
                .addOnFailureListener { exception ->
                    // ❌ ERROR en el procesamiento
                    Log.e(TAG, "❌ Error en ML Kit: ${exception.message}", exception)
                    txtInstrucciones.text = "❌ Error en OCR"
                    mostrarResultadoOCR("❌ Error al procesar imagen:\n${exception.message}")
                }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error general: ${e.message}", e)
            txtInstrucciones.text = "❌ Error al procesar"
            mostrarResultadoOCR("❌ Error: ${e.message}")
        }
    }

    private fun abrirCamaraPantallaCompleta() {
        val intent = Intent(this, CameraActivity::class.java)
        startActivityForResult(intent, 100)
    }

    private fun abrirGaleria() {
        Log.d(TAG, "Abriendo galería...")

        // Verificar si tenemos permiso de almacenamiento
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ - No necesita permiso explícito para galería
            lanzarIntentGaleria()
        } else {
            // Android 9 o inferior - Necesita permiso
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                lanzarIntentGaleria()
            } else {
                // Solicitar permiso
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    REQUEST_CODE_PERMISO_ALMACENAMIENTO
                )
            }
        }
    }

    private fun lanzarIntentGaleria() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_GALERIA)
    }

    private fun mostrarResultadoOCR(texto: String) {
        runOnUiThread {
            // Solo mostrar en el TextView (sin diálogo)
            txtResultado.text = texto
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            100 -> { // Cámara
                if (resultCode == RESULT_OK) {
                    val fotoPath = data?.getStringExtra("foto_path")
                    if (fotoPath != null) {
                        Log.d(TAG, "Foto recibida: $fotoPath")
                        txtInstrucciones.text = "✅ Foto recibida - Procesando..."
                        val bitmap = BitmapFactory.decodeFile(fotoPath)
                        procesarImagenConOCR(bitmap)
                    }
                } else {
                    Log.d(TAG, "Captura cancelada por el usuario")
                    txtInstrucciones.text = "Captura cancelada"
                }
            }

            REQUEST_CODE_GALERIA -> { // Galería
                if (resultCode == RESULT_OK && data != null) {
                    Log.d(TAG, "Imagen seleccionada de galería")
                    txtInstrucciones.text = "✅ Imagen seleccionada - Procesando..."

                    try {
                        val uri = data.data
                        if (uri != null) {
                            val inputStream = contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()

                            if (bitmap != null) {
                                procesarImagenConOCR(bitmap)
                            } else {
                                Log.e(TAG, "Error al decodificar bitmap desde galería")
                                txtInstrucciones.text = "Error al cargar imagen"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar imagen de galería: ${e.message}", e)
                        txtInstrucciones.text = "Error al procesar imagen"
                    }
                } else {
                    Log.d(TAG, "Selección de galería cancelada")
                    txtInstrucciones.text = "Selección cancelada"
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_PERMISO_ALMACENAMIENTO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, abrir galería
                    lanzarIntentGaleria()
                } else {
                    // Permiso denegado
                    Log.e(TAG, "Permiso de almacenamiento denegado")
                    txtInstrucciones.text = "Se necesita permiso para acceder a la galería"
                    mostrarResultadoOCR("❌ Permiso denegado. No se puede acceder a la galería.")
                }
            }
        }
    }

    private fun inicializarVistas() {
        // Conectar variables con los elementos del layout
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnElegirGaleria = findViewById(R.id.btnElegirGaleria)
        btnLeerVoz = findViewById(R.id.btnLeerVoz)
        btnVolver = findViewById(R.id.btnVolver)
        txtResultado = findViewById(R.id.txtResultado)
        txtInstrucciones = findViewById(R.id.txtInstrucciones)

        // Configurar click listeners
        btnTomarFoto.setOnClickListener {
            Log.d(TAG, "Botón Tomar Foto presionado")
            abrirCamaraPantallaCompleta()
        }

        btnElegirGaleria.setOnClickListener {
            Log.d(TAG, "Botón Galería presionado")
            abrirGaleria()
        }

        btnLeerVoz.setOnClickListener {
            Log.d(TAG, "Botón Leer en Voz presionado")
            leerTextoEnVozAlta()
        }

        btnVolver.setOnClickListener {
            Log.d(TAG, "Volviendo al MainActivity")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun inicializarTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }

    /**
     * Función TEMPORAL para probar el OCR
     * Más adelante reemplazaremos con cámara y galería reales
     */
    private fun probarOCRConEjemplo() {
        // Ya no usamos ejemplo simulado, usamos cámara real
        abrirCamaraPantallaCompleta()
    }

    /**
     * Leer el texto detectado en voz alta
     */
    private fun leerTextoEnVozAlta() {
        val texto = txtResultado.text.toString()
        if (texto.isNotBlank() && texto != "Aquí aparecerá el texto detectado...") {
            Log.d(TAG, "Leyendo en voz alta: $texto")
            hablar(texto)
        } else {
            Log.d(TAG, "No hay texto para leer")
            txtInstrucciones.text = "⚠️ Primero detecta texto antes de leer"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("es", "CO"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ Idioma español no soportado")
            } else {
                Log.d(TAG, "✅ TextToSpeech listo en OCR")
                // Configurar voz
                textToSpeech.setPitch(0.95f)
                textToSpeech.setSpeechRate(1.0f)
            }
        } else {
            Log.e(TAG, "❌ Error al inicializar TextToSpeech")
        }
    }

    private fun hablar(texto: String) {
        if (texto.isNotBlank()) {
            textToSpeech.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        Log.d(TAG, "OCRActivity destruida")
    }
}