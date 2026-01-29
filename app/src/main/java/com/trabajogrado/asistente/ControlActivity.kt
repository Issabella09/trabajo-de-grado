package com.trabajogrado.asistente

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ControlActivity : AppCompatActivity() {

    private lateinit var switchLectura: Switch
    private lateinit var radioGroupNiveles: RadioGroup
    private lateinit var radioNivel1: RadioButton
    private lateinit var radioNivel2: RadioButton
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button
    private val PREFS_NAME = "PrefsAsistente"
    private val KEY_NIVEL_LECTURA = "nivel_lectura"
    private val KEY_SWITCH_ACTIVADO = "switch_activado"
    private val TAG = "ControlActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        inicializarVistas()
        cargarPreferencias()
        configurarSwitch()
        verificarEstadoServicio()

        // ← AGREGAR ESTO: Activar automáticamente si el switch estaba activado
        if (switchLectura.isChecked) {
            activarLecturaPantalla()
        }
    }

    private fun inicializarVistas() {
        switchLectura = findViewById(R.id.switchLectura)
        radioGroupNiveles = findViewById(R.id.radioGroupNiveles)
        radioNivel1 = findViewById(R.id.radioNivel1)
        radioNivel2 = findViewById(R.id.radioNivel2)
        txtEstado = findViewById(R.id.txtEstado)
        btnVolver = findViewById(R.id.btnVolver)

        actualizarVisibilidadSelector()

        btnVolver.setOnClickListener {
            finish()
        }

        radioGroupNiveles.setOnCheckedChangeListener { _, checkedId ->
            guardarPreferenciaNivel(checkedId)
            // ← AGREGAR ESTO: Actualizar servicio inmediatamente al cambiar nivel
            if (switchLectura.isChecked) {
                actualizarServicioConNivel()
            }
        }
    }

    private fun cargarPreferencias() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cargar nivel de lectura (por defecto nivel 1)
        val nivelSeleccionado = prefs.getInt(KEY_NIVEL_LECTURA, R.id.radioNivel1)
        radioGroupNiveles.check(nivelSeleccionado)

        // Cargar estado del switch
        val switchActivado = prefs.getBoolean(KEY_SWITCH_ACTIVADO, false)
        switchLectura.isChecked = switchActivado

        Log.d(TAG, "Preferencias cargadas - Switch: $switchActivado, Nivel: $nivelSeleccionado")
    }

    private fun guardarPreferenciaNivel(checkedId: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_NIVEL_LECTURA, checkedId).apply()
        Log.d(TAG, "Nivel guardado: $checkedId")
    }

    private fun guardarEstadoSwitch(activado: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SWITCH_ACTIVADO, activado).apply()
        Log.d(TAG, "Estado switch guardado: $activado")
    }

    private fun configurarSwitch() {
        switchLectura.setOnCheckedChangeListener { _, isChecked ->
            actualizarVisibilidadSelector()
            guardarEstadoSwitch(isChecked)

            if (isChecked) {
                // ← AGREGAR ESTO: Asegurar que hay un nivel seleccionado
                if (radioGroupNiveles.checkedRadioButtonId == -1) {
                    radioGroupNiveles.check(R.id.radioNivel1)
                }
                activarLecturaPantalla()
            } else {
                desactivarLecturaPantalla()
            }
        }
    }

    private fun actualizarVisibilidadSelector() {
        radioGroupNiveles.visibility = if (switchLectura.isChecked) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun activarLecturaPantalla() {
        Log.d(TAG, "Usuario activó lectura de pantalla")

        // ← AGREGAR ESTO: Asegurar nivel por defecto si no hay selección
        if (radioGroupNiveles.checkedRadioButtonId == -1) {
            radioGroupNiveles.check(R.id.radioNivel1)
            guardarPreferenciaNivel(R.id.radioNivel1)
        }

        txtEstado.text = "Estado: Lectura ACTIVADA - ${obtenerTextoNivelSeleccionado()}"

        try {
            val nivel = obtenerNivelSeleccionado()
            LecturaPantallaService.activarLecturaDesdeExterno(nivel)

            android.widget.Toast.makeText(
                this,
                "Lectura activada - ${obtenerTextoNivelSeleccionado()}",
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

        try {
            LecturaPantallaService.desactivarLecturaDesdeExterno()

            detenerServicioAccesibilidad()

            android.widget.Toast.makeText(
                this,
                "Lectura de pantalla desactivada",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error desactivando lectura: ${e.message}")
        }
    }

    private fun detenerServicioAccesibilidad() {
        try {
            // Crear intent para detener el servicio
            val intent = Intent(this, LecturaPantallaService::class.java)
            stopService(intent)

            Log.d(TAG, "🛑 Servicio de accesibilidad detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servicio: ${e.message}")
        }
    }

    private fun actualizarServicioConNivel() {
        if (switchLectura.isChecked) {
            val nivel = obtenerNivelSeleccionado()
            LecturaPantallaService.actualizarNivelLectura(nivel)
            txtEstado.text = "Estado: Lectura ACTIVADA - ${obtenerTextoNivelSeleccionado()}"

            android.widget.Toast.makeText(
                this,
                "Nivel actualizado: ${obtenerTextoNivelSeleccionado()}",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            guardarPreferenciaNivel(radioGroupNiveles.checkedRadioButtonId)
        }
    }

    private fun obtenerNivelSeleccionado(): Int {
        return when (radioGroupNiveles.checkedRadioButtonId) {
            R.id.radioNivel1 -> 1
            R.id.radioNivel2 -> 2
            else -> 1 // ← POR DEFECTO NIVEL 1
        }
    }

    private fun obtenerTextoNivelSeleccionado(): String {
        return when (obtenerNivelSeleccionado()) {
            1 -> "Nivel 1 (Solo mensajes)"
            2 -> "Nivel 2 (Todo el contenido)"
            else -> "Nivel 1 (Solo mensajes)"
        }
    }

    private fun verificarEstadoServicio() {
        if (esServicioAccesibilidadActivo()) {
            txtEstado.text = "Estado: Permisos concedidos - Listo para usar"
            switchLectura.isEnabled = true
        } else {
            txtEstado.text = "Estado: Permisos NO concedidos"
            switchLectura.isEnabled = false

            android.widget.Toast.makeText(
                this,
                "Primero activa los permisos en Ajustes del Sistema",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Si el usuario cierra la app con el switch activado, mantenerlo
        // Si lo cierra con el switch desactivado
        if (!switchLectura.isChecked) {
            desactivarLecturaPantalla()
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

    private fun irAAjustesAccesibilidad() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }
}