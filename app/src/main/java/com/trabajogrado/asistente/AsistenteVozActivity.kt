package com.trabajogrado.asistente

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.content.Context
import android.app.ActivityManager

class AsistenteVozActivity : AppCompatActivity() {

    private val TAG = "AsistenteVozActivity"
    private lateinit var switchAsistenteVoz: Switch
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private const val PREFS_NAME = "EVAPreferences"
        private const val KEY_ASISTENTE_ACTIVO = "asistente_activo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistente_voz)

        inicializarVistas()
        verificarPermisos()
        configurarListeners()

        // Restaurar estado guardado
        restaurarEstado()
    }

    override fun onResume() {
        super.onResume()
        // Actualizar el switch según si el servicio está corriendo
        actualizarEstadoUI()
    }

    private fun inicializarVistas() {
        switchAsistenteVoz = findViewById(R.id.switchAsistenteVoz)
        txtEstado = findViewById(R.id.txtEstadoAsistente)
        btnVolver = findViewById(R.id.btnVolverAsistente)
    }

    private fun configurarListeners() {
        switchAsistenteVoz.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                activarAsistenteVoz()
            } else {
                desactivarAsistenteVoz()
            }
        }

        btnVolver.setOnClickListener {
            finish()
        }
    }

    private fun verificarPermisos(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Permiso de micrófono NO concedido")

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )) {
                AlertDialog.Builder(this)
                    .setTitle("Permiso de micrófono necesario")
                    .setMessage("EVA necesita acceso al micrófono para escuchar el comando de voz 'EVA'")
                    .setPositiveButton("Entendido") { _, _ ->
                        solicitarPermisoMicrofono()
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        switchAsistenteVoz.isChecked = false
                    }
                    .show()
            } else {
                solicitarPermisoMicrofono()
            }
            return false
        }

        Log.d(TAG, "✅ Permiso de micrófono concedido")
        txtEstado.text = "✅ Permiso de micrófono concedido"
        switchAsistenteVoz.isEnabled = true
        return true
    }

    private fun solicitarPermisoMicrofono() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    txtEstado.text = "✅ Permiso de micrófono concedido"
                    switchAsistenteVoz.isEnabled = true
                    Toast.makeText(this, "Permiso de micrófono concedido", Toast.LENGTH_SHORT).show()
                } else {
                    txtEstado.text = "❌ Permiso de micrófono denegado"
                    switchAsistenteVoz.isEnabled = false
                    switchAsistenteVoz.isChecked = false
                    Toast.makeText(this, "Se necesita permiso de micrófono para el asistente de voz", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun activarAsistenteVoz() {
        Log.d(TAG, "🎤 Activando asistente...")

        if (!verificarPermisos()) {
            Log.e(TAG, "❌ No se puede activar - Permisos faltantes")
            switchAsistenteVoz.isChecked = false
            return
        }

        txtEstado.text = "✅ Asistente de voz ACTIVADO\nDi 'EVA' seguido de tu comando"

        try {
            val intent = Intent(this, AsistenteVozService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Guardar estado
            guardarEstado(true)

            Toast.makeText(this, "✅ Asistente activado\nDi 'EVA' para comandos", Toast.LENGTH_LONG).show()
            Log.d(TAG, "🎉 Asistente activado")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: ${e.message}", e)
            txtEstado.text = "❌ Error al activar"
            switchAsistenteVoz.isChecked = false
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun desactivarAsistenteVoz() {
        txtEstado.text = "⏸️ Asistente de voz DESACTIVADO"

        val intent = Intent(this, AsistenteVozService::class.java)
        stopService(intent)

        // Guardar estado
        guardarEstado(false)

        Toast.makeText(this, "Asistente de voz desactivado", Toast.LENGTH_SHORT).show()
    }

    private fun guardarEstado(activo: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ASISTENTE_ACTIVO, activo).apply()
        Log.d(TAG, "💾 Estado guardado: $activo")
    }

    private fun restaurarEstado() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val estabaActivo = prefs.getBoolean(KEY_ASISTENTE_ACTIVO, false)

        Log.d(TAG, "📂 Estado guardado: $estabaActivo")

        if (estabaActivo) {
            // Verificar si el servicio realmente está corriendo
            val servicioActivo = isServiceRunning(AsistenteVozService::class.java)

            if (servicioActivo) {
                // El servicio está corriendo, actualizar UI
                switchAsistenteVoz.isChecked = true
                txtEstado.text = "✅ Asistente de voz ACTIVADO\nDi 'EVA' seguido de tu comando"
            } else {
                // El servicio no está corriendo, reiniciarlo
                if (verificarPermisos()) {
                    Log.d(TAG, "🔄 Reiniciando servicio...")
                    val intent = Intent(this, AsistenteVozService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    switchAsistenteVoz.isChecked = true
                    txtEstado.text = "✅ Asistente de voz ACTIVADO\nDi 'EVA' seguido de tu comando"
                }
            }
        } else {
            switchAsistenteVoz.isChecked = false
            txtEstado.text = "⏸️ Asistente de voz DESACTIVADO"
        }
    }

    private fun actualizarEstadoUI() {
        val servicioActivo = isServiceRunning(AsistenteVozService::class.java)

        if (servicioActivo) {
            if (!switchAsistenteVoz.isChecked) {
                switchAsistenteVoz.isChecked = true
                txtEstado.text = "✅ Asistente de voz ACTIVADO\nDi 'EVA' seguido de tu comando"
            }
        } else {
            if (switchAsistenteVoz.isChecked) {
                switchAsistenteVoz.isChecked = false
                txtEstado.text = "⏸️ Asistente de voz DESACTIVADO"
                guardarEstado(false)
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}