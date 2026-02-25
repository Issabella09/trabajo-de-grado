package com.trabajogrado.asistente

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class ConfiguracionPrefijoActivity : AppCompatActivity() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioNotificacion: RadioButton
    private lateinit var radioMensaje: RadioButton
    private lateinit var radioSinPrefijo: RadioButton
    private lateinit var btnGuardar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion_prefijo)

        inicializarVistas()
        cargarPreferencia()
        configurarListeners()
    }

    private fun inicializarVistas() {
        radioGroup = findViewById(R.id.radio_group_prefijo)
        radioNotificacion = findViewById(R.id.radio_notificacion)
        radioMensaje = findViewById(R.id.radio_mensaje)
        radioSinPrefijo = findViewById(R.id.radio_sin_prefijo)
        btnGuardar = findViewById(R.id.btn_guardar_prefijo)
    }

    private fun cargarPreferencia() {
        val prefs = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
        val prefijo = prefs.getString("prefijo_lectura", "notificacion") ?: "notificacion"

        when (prefijo) {
            "notificacion" -> radioNotificacion.isChecked = true
            "mensaje" -> radioMensaje.isChecked = true
            "sin_prefijo" -> radioSinPrefijo.isChecked = true
        }
    }

    private fun configurarListeners() {
        btnGuardar.setOnClickListener {
            guardarPreferencia()
            finish()
        }
    }

    private fun guardarPreferencia() {
        val prefijo = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_notificacion -> "notificacion"
            R.id.radio_mensaje -> "mensaje"
            R.id.radio_sin_prefijo -> "sin_prefijo"
            else -> "notificacion"
        }

        val prefs = getSharedPreferences("config_notificaciones", MODE_PRIVATE)
        prefs.edit().putString("prefijo_lectura", prefijo).apply()

        setResult(RESULT_OK)
    }
}