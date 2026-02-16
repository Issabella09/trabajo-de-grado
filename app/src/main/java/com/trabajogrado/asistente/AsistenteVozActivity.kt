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

class AsistenteVozActivity : AppCompatActivity() {

    private val TAG = "AsistenteVozActivity"
    private lateinit var switchAsistenteVoz: Switch
    private lateinit var txtEstado: TextView
    private lateinit var btnVolver: Button

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistente_voz)

        inicializarVistas()
        configurarListeners()
        verificarPermisos()
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
        // Verificar permiso de micrófono
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Permiso de micrófono NO concedido")

            // Mostrar explicación primero
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )) {
                // Explicar al usuario por qué necesitamos el permiso
                AlertDialog.Builder(this)
                    .setTitle("Permiso de micrófono necesario")
                    .setMessage("EVA necesita acceso al micrófono para escuchar el comando de voz 'EVA'")
                    .setPositiveButton("Entendido") { _, _ ->
                        // Solicitar permiso después de explicar
                        solicitarPermisoMicrofono()
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        switchAsistenteVoz.isChecked = false
                    }
                    .show()
            } else {
                // Solicitar permiso directamente
                solicitarPermisoMicrofono()
            }
            return false
        }

        Log.d(TAG, "✅ Permiso de micrófono concedido")
        txtEstado.text = "✅ Permiso de micrófono concedido"
        switchAsistenteVoz.isEnabled = true
        return true
    }

    override fun onResume() {
        super.onResume()

        // Verificar permisos cada vez que se vuelve a la actividad
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            switchAsistenteVoz.isEnabled = true
            txtEstado.text = "✅ Listo para activar"
        } else {
            switchAsistenteVoz.isEnabled = false
            switchAsistenteVoz.isChecked = false
            txtEstado.text = "❌ Se necesita permiso de micrófono"
        }
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

            Log.d(TAG, "🚀 Intentando iniciar servicio...")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                Log.d(TAG, "📲 startForegroundService llamado")
            } else {
                startService(intent)
                Log.d(TAG, "📲 startService llamado")
            }

            Toast.makeText(this, "✅ Asistente activado\nDi 'EVA' para comandos", Toast.LENGTH_LONG).show()
            Log.d(TAG, "🎉 Asistente activado")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO: ${e.message}", e)
            e.printStackTrace()
            txtEstado.text = "❌ Error al activar"
            switchAsistenteVoz.isChecked = false
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun desactivarAsistenteVoz() {
        txtEstado.text = "⏸️ Asistente de voz DESACTIVADO"

        // Detener el servicio
        val intent = Intent(this, AsistenteVozService::class.java)
        stopService(intent)

        Toast.makeText(this, "Asistente de voz desactivado", Toast.LENGTH_SHORT).show()
    }
}