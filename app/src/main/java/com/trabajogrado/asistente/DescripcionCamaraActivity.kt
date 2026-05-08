package com.trabajogrado.asistente

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.camera.core.ExperimentalGetImage
class DescripcionCamaraActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val TAG = "DescripcionCamara"
    private val REQUEST_CAMERA = 100

    private lateinit var previewView: PreviewView
    private lateinit var btnVolver: AppCompatButton
    private lateinit var txtEstado: TextView
    private lateinit var txtDescripcion: TextView
    private lateinit var tts: TextToSpeech

    private var ttsListo = false
    private var procesando = AtomicBoolean(false)
    private lateinit var cameraExecutor: ExecutorService

    // Intervalo entre análisis (ms)
    private val INTERVALO_MS = 2000L
    private var ultimoAnalisis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_descripcion_camara)

        previewView = findViewById(R.id.previewDescripcion)
        btnVolver = findViewById(R.id.btnVolverDescripcion)
        txtEstado = findViewById(R.id.txtEstadoDescripcion)
        txtDescripcion = findViewById(R.id.txtDescripcion)

        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (tienePermisoCamara()) {
            iniciarCamara()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA
            )
        }

        btnVolver.setOnClickListener { finish() }
    }

    private fun tienePermisoCamara() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun iniciarCamara() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // ImageAnalysis para frames en tiempo real
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analizarFrame(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                actualizarEstado("Analizando entorno en tiempo real...")
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando cámara: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analizarFrame(imageProxy: ImageProxy) {
        val ahora = System.currentTimeMillis()

        // Respetar intervalo y no procesar si ya hay uno en curso
        if (ahora - ultimoAnalisis < INTERVALO_MS || procesando.get()) {
            imageProxy.close()
            return
        }

        ultimoAnalisis = ahora
        procesando.set(true)

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            procesando.set(false)
            return
        }

        val imagen = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        val opciones = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.70f)
            .build()

        val labeler = ImageLabeling.getClient(opciones)

        labeler.process(imagen)
            .addOnSuccessListener { etiquetas ->
                imageProxy.close()

                if (etiquetas.isEmpty()) {
                    procesando.set(false)
                    return@addOnSuccessListener
                }

                // Traducción básica de etiquetas comunes
                val traduccion = mapOf(
                    // Personas y cuerpo
                    "person" to "persona","nail" to "uña", "man" to "hombre", "woman" to "mujer",
                    "child" to "niño", "boy" to "niño", "girl" to "niña",
                    "face" to "cara", "hand" to "mano", "eye" to "ojo",
                    "mouth" to "boca", "nose" to "nariz", "ear" to "oreja",
                    "head" to "cabeza", "hair" to "cabello", "skin" to "piel",
                    "flesh" to "piel", "neck" to "cuello", "shoulder" to "hombro",
                    "arm" to "brazo", "leg" to "pierna", "finger" to "dedo",
                    "lip" to "labio", "tooth" to "diente", "chin" to "mentón",
                    "cheek" to "mejilla", "forehead" to "frente", "eyebrow" to "ceja",
                    "beard" to "barba", "mustache" to "bigote", "smile" to "sonrisa",
                    "body" to "cuerpo", "back" to "espalda", "chest" to "pecho",
                    "foot" to "pie", "thumb" to "pulgar", "palm" to "palma",

                    // Emociones y actividades
                    "fun" to "diversión", "joy" to "alegría", "happiness" to "felicidad",
                    "smile" to "sonrisa", "laugh" to "risa", "play" to "juego",
                    "sport" to "deporte", "game" to "juego", "entertainment" to "entretenimiento",
                    "recreation" to "recreación", "leisure" to "ocio",

                    // Comida y utensilios
                    "tableware" to "vajilla", "cutlery" to "cubiertos", "plate" to "plato",
                    "bowl" to "tazón", "cup" to "taza", "glass" to "vaso",
                    "fork" to "tenedor", "spoon" to "cuchara", "knife" to "cuchillo",
                    "bottle" to "botella", "food" to "comida", "drink" to "bebida",
                    "meal" to "comida", "fruit" to "fruta", "vegetable" to "vegetal",
                    "bread" to "pan", "meat" to "carne", "fish" to "pescado",
                    "dish" to "plato", "cuisine" to "cocina", "snack" to "merienda",
                    "dessert" to "postre", "coffee" to "café", "tea" to "té",
                    "juice" to "jugo", "soup" to "sopa", "salad" to "ensalada",

                    // Instrumentos musicales
                    "musical instrument" to "instrumento musical",
                    "guitar" to "guitarra", "piano" to "piano", "violin" to "violín",
                    "drum" to "tambor", "trumpet" to "trompeta", "flute" to "flauta",
                    "keyboard" to "teclado", "bass" to "bajo", "instrument" to "instrumento",
                    "music" to "música", "song" to "canción", "concert" to "concierto",

                    // Muebles y hogar
                    "chair" to "silla", "table" to "mesa", "desk" to "escritorio",
                    "sofa" to "sofá", "bed" to "cama", "furniture" to "mueble",
                    "shelf" to "estante", "cabinet" to "gabinete", "drawer" to "cajón",
                    "lamp" to "lámpara", "light" to "luz", "mirror" to "espejo",
                    "curtain" to "cortina", "carpet" to "alfombra", "pillow" to "almohada",
                    "blanket" to "cobija", "wardrobe" to "armario",

                    // Tecnología
                    "computer" to "computador", "phone" to "teléfono", "laptop" to "portátil",
                    "screen" to "pantalla", "mouse" to "ratón", "television" to "televisor",
                    "camera" to "cámara", "tablet" to "tableta", "headphone" to "audífono",
                    "speaker" to "parlante", "microphone" to "micrófono", "charger" to "cargador",
                    "cable" to "cable", "remote" to "control remoto",

                    // Ropa y accesorios
                    "clothing" to "ropa", "shirt" to "camisa", "pants" to "pantalón",
                    "shoe" to "zapato", "hat" to "sombrero", "bag" to "bolso",
                    "jacket" to "chaqueta", "dress" to "vestido", "skirt" to "falda",
                    "tie" to "corbata", "glasses" to "gafas", "watch" to "reloj",
                    "ring" to "anillo", "necklace" to "collar", "earring" to "arete",

                    // Naturaleza
                    "tree" to "árbol", "plant" to "planta", "flower" to "flor",
                    "grass" to "pasto", "leaf" to "hoja", "sky" to "cielo",
                    "water" to "agua", "cloud" to "nube", "sun" to "sol",
                    "rain" to "lluvia", "snow" to "nieve", "mountain" to "montaña",
                    "river" to "río", "sea" to "mar", "beach" to "playa",
                    "forest" to "bosque", "garden" to "jardín", "rock" to "roca",
                    "soil" to "tierra", "sand" to "arena",

                    // Vehículos y transporte
                    "car" to "carro", "vehicle" to "vehículo", "truck" to "camión",
                    "bicycle" to "bicicleta", "motorcycle" to "moto", "bus" to "bus",
                    "train" to "tren", "airplane" to "avión", "boat" to "bote",
                    "road" to "calle", "street" to "calle", "highway" to "autopista",

                    // Animales
                    "dog" to "perro", "cat" to "gato", "bird" to "pájaro",
                    "fish" to "pez", "horse" to "caballo", "cow" to "vaca",
                    "pig" to "cerdo", "chicken" to "pollo", "rabbit" to "conejo",
                    "lion" to "león", "tiger" to "tigre", "elephant" to "elefante",
                    "bear" to "oso", "monkey" to "mono", "snake" to "serpiente",

                    // Edificios y lugares
                    "building" to "edificio", "house" to "casa", "office" to "oficina",
                    "kitchen" to "cocina", "bathroom" to "baño", "room" to "habitación",
                    "floor" to "piso", "wall" to "pared", "door" to "puerta",
                    "window" to "ventana", "ceiling" to "techo", "stairs" to "escaleras",
                    "city" to "ciudad", "outdoor" to "exterior", "indoor" to "interior",

                    // Materiales y objetos
                    "wood" to "madera", "metal" to "metal", "plastic" to "plástico",
                    "paper" to "papel", "book" to "libro", "pen" to "bolígrafo",
                    "box" to "caja", "bag" to "bolsa", "ball" to "pelota",
                    "toy" to "juguete", "art" to "arte", "painting" to "pintura",
                    "photo" to "foto", "image" to "imagen",

                    // Colores y formas
                    "black" to "negro", "white" to "blanco", "color" to "color",
                    "rectangle" to "rectángulo", "circle" to "círculo",

                    // Clima
                    "fire" to "fuego", "smoke" to "humo", "night" to "noche",
                    "day" to "día", "nature" to "naturaleza", "ground" to "suelo"
                )

                val top3 = etiquetas.take(3).map { etiqueta ->
                    traduccion[etiqueta.text.lowercase()] ?: etiqueta.text.lowercase()
                }

                val descripcion = "Se detecta: " + top3.joinToString(", ")

                runOnUiThread {
                    txtDescripcion.text = descripcion
                    actualizarEstado("Analizando entorno en tiempo real...")
                    if (ttsListo && !tts.isSpeaking) {
                        tts.speak(descripcion, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }

                procesando.set(false)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error ML Kit: ${e.message}")
                imageProxy.close()
                procesando.set(false)
            }
    }

    private fun actualizarEstado(texto: String) {
        runOnUiThread { txtEstado.text = texto }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "CO")
            ttsListo = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCamara()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        cameraExecutor.shutdown()
    }
}