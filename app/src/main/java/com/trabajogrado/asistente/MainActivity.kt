package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private var redireccionAutomatica = true
    private lateinit var btnActivarServicio: Button
    private lateinit var btnIrAControl: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnActivarServicio = findViewById(R.id.btnActivarServicio)
        btnIrAControl = findViewById(R.id.btnIrAControl)

        btnActivarServicio.setOnClickListener {
            // Abrir configuración de accesibilidad directamente
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnIrAControl.setOnClickListener {
            val intent = Intent(this, ControlActivity::class.java)
            startActivity(intent)
        }

        // BOTÓN NUEVO OCR - AGREGA ESTAS LÍNEAS
        val btnAbrirOCR: Button = findViewById(R.id.btnAbrirOCR)
        btnAbrirOCR.setOnClickListener {
            abrirActividadOCR()
        }

        val btnNotificaciones = findViewById<Button>(R.id.btnNotificaciones)
        if (btnNotificaciones == null) {
            Log.e("MainActivity", "❌❌❌ ERROR: btnNotificaciones NO EXISTE")
            Toast.makeText(this, "ERROR: Botón no encontrado", Toast.LENGTH_LONG).show()
            return
        } else {
            Log.d("MainActivity", "✅ Botón notificaciones encontrado")
        }

        btnNotificaciones.setOnClickListener {
            Log.d("MainActivity", "🎯🎯🎯 BOTÓN PRESIONADO - INICIANDO ACTIVIDAD")

            try {
                val intent = Intent(this, NotificacionesActivity::class.java)
                startActivity(intent)
                Log.d("MainActivity", "✅✅✅ ACTIVIDAD INICIADA")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌❌❌ ERROR CRÍTICO: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun abrirActividadOCR() {
        try {
            val intent = Intent(this, OCRActivity::class.java)
            startActivity(intent)
            Log.d("MainActivity", "Abriendo actividad OCR")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al abrir OCR: ${e.message}")
            android.widget.Toast.makeText(
                this,
                "Error al abrir lector de texto",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarEstadoBotonControl()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceId = "$packageName/${LecturaPantallaService::class.java.canonicalName}" // ← CORREGIDO
        val settings = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return settings?.contains(serviceId) == true
    }

    private fun actualizarEstadoBotonControl() {
        if (isAccessibilityServiceEnabled()) {
            // Si el servicio está activado, habilita el botón de control
            btnIrAControl.isEnabled = true
            btnIrAControl.alpha = 1.0f // Restaura la opacidad completa
        } else {
            // Si no, lo deshabilita y lo hace semitransparente
            btnIrAControl.isEnabled = false
            btnIrAControl.alpha = 0.5f // Efecto visual para que se vea deshabilitado
        }
    }
}