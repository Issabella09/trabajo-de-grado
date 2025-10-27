package com.trabajogrado.asistente

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {

    private lateinit var switchLectura: Switch
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button
    private val TAG = "ControlActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        inicializarVistas()
        configurarSwitch()
        verificarEstadoServicio()
    }

    private fun inicializarVistas() {
        switchLectura = findViewById(R.id.switchLectura)
        txtEstado = findViewById(R.id.txtEstado)
        btnVolver = findViewById(R.id.btnVolver)

        btnVolver.setOnClickListener {
            // SOLUCIÓN: Solo finalizar esta actividad
            finish()
        }
    }

    private fun configurarSwitch() {
        switchLectura.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activarLecturaPantalla()
            } else {
                desactivarLecturaPantalla()
            }
        }
    }

    private fun activarLecturaPantalla() {
        Log.d(TAG, "Usuario activó lectura de pantalla")
        txtEstado.text = "Estado: Lectura ACTIVADA"

        // Activar el servicio de lectura
        try {
            LecturaPantallaService.activarLecturaDesdeExterno()
            android.widget.Toast.makeText(
                this,
                "Lectura de pantalla activada",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error activando lectura: ${e.message}")
            android.widget.Toast.makeText(
                this,
                "Error al activar. Reinicia la app.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun desactivarLecturaPantalla() {
        Log.d(TAG, "Usuario desactivó lectura de pantalla")
        txtEstado.text = "Estado: Lectura DESACTIVADA"

        // Desactivar el servicio de lectura
        try {
            LecturaPantallaService.desactivarLecturaDesdeExterno()
            android.widget.Toast.makeText(
                this,
                "Lectura de pantalla desactivada",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error desactivando lectura: ${e.message}")
        }
    }

    private fun verificarEstadoServicio() {
        if (esServicioAccesibilidadActivo()) {
            txtEstado.text = "Estado: Permisos concedidos - Listo para usar"
            switchLectura.isEnabled = true
        } else {
            txtEstado.text = "Estado: Permisos NO concedidos"
            switchLectura.isEnabled = false

            // Si no tiene permisos, ofrecer ir a ajustes
            android.widget.Toast.makeText(
                this,
                "Primero activa los permisos en Ajustes del Sistema",
                android.widget.Toast.LENGTH_LONG
            ).show()
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
            Log.e(TAG, "Error verificando servicio: ${e.message}")
        }
        return false
    }

    // Método para ir a ajustes desde esta actividad si es necesario
    private fun irAAjustesAccesibilidad() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}