package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.ComponentName
import android.util.Log

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        val btnActivarServicio: Button = findViewById(R.id.btnActivarServicio)

        btnActivarServicio.setOnClickListener {
            // Abrir configuración de accesibilidad directamente
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        // Verificar si el servicio de accesibilidad ya está activo
        if (esServicioAccesibilidadActivo()) {
            // Si ya tiene permisos, redirigir directamente a ControlActivity
            val intent = Intent(this, ControlActivity::class.java)
            startActivity(intent)
            finish() // Cerrar esta actividad para que no quede en el stack
        }
    }
    private fun esServicioAccesibilidadActivo(): Boolean {
        try {
            val service = ComponentName(this, LecturaPantallaService::class.java)
            val enabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )

            if (enabled == 1) {
                val services = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                return services?.contains(service.flattenToString()) == true
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error verificando servicio: ${e.message}")
        }
        return false
    }
}