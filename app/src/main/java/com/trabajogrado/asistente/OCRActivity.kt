package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager

// IMPORTS CORRECTOS PARA CAMERAX - PEGA ESTOS
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import android.graphics.Bitmap
import android.graphics.BitmapFactory

// AGREGA ESTOS IMPORTS NUEVOS PARA ML KIT
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OCRActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var btnTomarFoto: Button
    private lateinit var btnElegirGaleria: Button
    private lateinit var btnLeerVoz: Button
    private lateinit var btnVolver: Button
    private lateinit var txtResultado: TextView
    private lateinit var txtInstrucciones: TextView
    private val TAG = "OCRActivity"

    // CONSTANTES NUEVAS PARA PERMISOS - AGREGA ESTAS LÍNEAS
    private val CAMERA_PERMISSION_CODE = 100
    private val GALLERY_PERMISSION_CODE = 101

    // VARIABLES NUEVAS PARA CAMERAX - AGREGA ESTAS LÍNEAS
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService
    private var imageCapture: androidx.camera.core.ImageCapture? = null

    private fun verificarPermisosCamara() {
        val permisosCamara = arrayOf(android.Manifest.permission.CAMERA)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // Pedir permisos
            ActivityCompat.requestPermissions(this, permisosCamara, CAMERA_PERMISSION_CODE)
        } else {
            // Ya tiene permisos, podemos abrir cámara
            abrirCamara()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, abrir cámara
                    abrirCamara()
                } else {
                    // Permiso denegado
                    Log.e(TAG, "Permiso de cámara denegado")
                    txtInstrucciones.text = "❌ Se necesitan permisos de cámara"
                }
            }
        }
    }

    private fun abrirCamara() {
        Log.d(TAG, "Iniciando cámara con CameraX")

        // Mostrar vista previa y ocultar instrucciones temporales
        previewView.visibility = android.view.View.VISIBLE
        txtInstrucciones.text = "📸 Apunta la cámara al texto"

        // Ocultar botones temporales y mostrar botón de captura
        btnTomarFoto.text = "📷 Capturar Foto"
        btnTomarFoto.setOnClickListener {
            tomarFoto()
        }

        // Configurar CameraX
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // ImageCapture
                imageCapture = ImageCapture.Builder()
                    .build()

                // Seleccionar cámara trasera
                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind use cases antes de rebind
                cameraProvider.unbindAll()

                // Bind use cases al ciclo de vida de la cámara
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Error al bindear use cases", exc)
                txtInstrucciones.text = "❌ Error al iniciar cámara"
            }

        }, ContextCompat.getMainExecutor(this)) // ← CORREGIDO AQUÍ

        // Inicializar executor para procesar imágenes
        cameraExecutor = Executors.newSingleThreadExecutor()
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

    private fun mostrarResultadoOCR(texto: String) {
        runOnUiThread {
            // Solo mostrar en el TextView (sin diálogo)
            txtResultado.text = texto
        }
    }

    private fun tomarFoto() {
        Log.d(TAG, "Tomando foto...")

        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture no está listo")
            txtInstrucciones.text = "La cámara aún no está lista, espera un momento"
            return
        }

        // Crear archivo temporal para la foto
        val photoFile = java.io.File(
            externalMediaDirs.firstOrNull(),
            "${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Foto guardada: ${photoFile.absolutePath}")
                    txtInstrucciones.text = "✅ Foto tomada - Procesando texto..."

                    // Procesar la imagen con OCR
                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                    procesarImagenConOCR(bitmap)
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    Log.e(TAG, "Error al tomar foto: ${exception.message}", exception)
                    txtInstrucciones.text = "Error al tomar foto"
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)

        Log.d(TAG, "OCRActivity iniciada")
        inicializarVistas()
        inicializarTextToSpeech()
    }

    private fun inicializarVistas() {
        // Conectar variables con los elementos del layout
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnElegirGaleria = findViewById(R.id.btnElegirGaleria)
        btnLeerVoz = findViewById(R.id.btnLeerVoz)
        btnVolver = findViewById(R.id.btnVolver)
        txtResultado = findViewById(R.id.txtResultado)
        txtInstrucciones = findViewById(R.id.txtInstrucciones)
        previewView = findViewById(R.id.previewView)

        // Configurar click listeners
        btnTomarFoto.setOnClickListener {
            Log.d(TAG, "Botón Tomar Foto presionado")
            verificarPermisosCamara()
        }

        btnElegirGaleria.setOnClickListener {
            Log.d(TAG, "Botón Galería presionado")
            probarOCRConEjemplo() // Temporal - luego será galería real
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
        verificarPermisosCamara()
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