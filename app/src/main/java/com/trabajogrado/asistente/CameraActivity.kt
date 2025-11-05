package com.trabajogrado.asistente

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.ImageCaptureException

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapturar: Button
    private lateinit var btnCancelar: Button
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val TAG = "CameraActivity"
    private val CAMERA_PERMISSION_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        Log.d(TAG, "CameraActivity iniciada")
        inicializarVistas()
        verificarPermisosCamara()
    }

    private fun inicializarVistas() {
        previewView = findViewById(R.id.previewViewFullscreen)
        btnCapturar = findViewById(R.id.btnCapturarFoto)
        btnCancelar = findViewById(R.id.btnCancelarFoto)

        btnCapturar.setOnClickListener {
            tomarFoto()
        }

        btnCancelar.setOnClickListener {
            Log.d(TAG, "Cancelando captura")
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun verificarPermisosCamara() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            iniciarCamara()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamara()
            } else {
                Log.e(TAG, "Permiso de cámara denegado")
                finish()
            }
        }
    }

    private fun iniciarCamara() {
        Log.d(TAG, "Iniciando cámara pantalla completa")

        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = androidx.camera.core.Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                imageCapture = androidx.camera.core.ImageCapture.Builder()
                    .build()

                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            } catch (exc: Exception) {
                Log.e(TAG, "Error al iniciar cámara", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun tomarFoto() {
        Log.d(TAG, "Tomando foto en pantalla completa")

        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            "ocr_foto_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Foto guardada: ${photoFile.absolutePath}")

                    // Devolver la ruta de la foto a OCRActivity
                    val resultIntent = Intent().apply {
                        putExtra("foto_path", photoFile.absolutePath)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error al tomar foto: ${exception.message}", exception)
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}