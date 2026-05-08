package com.trabajogrado.asistente

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class AsistenteVozNuevoActivity : AppCompatActivity() {

    private val TAG = "AsistenteVozNuevo"
    private val PREFS_NAME = "EvaPreferences"
    private val KEY_EVA_ACTIVE = "eva_active"
    private val REQUEST_RECORD_AUDIO = 1

    private lateinit var tvUltimoComando: TextView
    private lateinit var tvRespuesta: TextView
    private lateinit var switchEva: SwitchCompat
    private lateinit var tvEstadoEva: TextView

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra(EvaListeningService.EXTRA_ESTADO)?.let {
                tvEstadoEva.text = it
            }
            intent?.getStringExtra(EvaListeningService.EXTRA_COMANDO)?.let {
                tvUltimoComando.text = it
            }
            intent?.getStringExtra(EvaListeningService.EXTRA_RESPUESTA)?.let {
                tvRespuesta.text = it
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_asistente_voz_nuevo)
        inicializarVistas()
    }

    override fun onResume() {
        super.onResume()

        ContextCompat.registerReceiver(
            this, updateReceiver,
            IntentFilter(EvaListeningService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val prefsActivo = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_EVA_ACTIVE, false)

        // Si el usuario tenía EVA activo y el servicio fue matado, reiniciarlo
        if (prefsActivo && !EvaListeningService.isRunning) {
            EvaListeningService.iniciar(this)
        }

        // Sincronizar switch con el estado real del servicio sin disparar el listener
        switchEva.setOnCheckedChangeListener(null)
        switchEva.isChecked = EvaListeningService.isRunning || prefsActivo
        configurarSwitch()

        tvEstadoEva.text = if (EvaListeningService.isRunning)
            "👂 Esperando 'EVA'..."
        else
            "Di 'EVA' seguido de tu comando"
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (e: Exception) {}
    }

    private fun inicializarVistas() {
        tvUltimoComando = findViewById(R.id.tv_ultimo_comando)
        tvRespuesta = findViewById(R.id.tv_respuesta)
        switchEva = findViewById(R.id.switch_eva)
        tvEstadoEva = findViewById(R.id.tv_estado_eva)
    }

    private fun configurarSwitch() {
        switchEva.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) activarEva() else desactivarEva()
        }
    }

    private fun activarEva() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            switchEva.isChecked = false
            return
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_EVA_ACTIVE, true).apply()
        EvaListeningService.iniciar(this)
        tvEstadoEva.text = "Iniciando EVA..."
        Log.d(TAG, "✅ EVA activado")
    }

    private fun desactivarEva() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_EVA_ACTIVE, false).apply()
        EvaListeningService.detener(this)
        tvEstadoEva.text = "Di 'EVA' seguido de tu comando"
        tvUltimoComando.text = "Ninguno aún"
        tvRespuesta.text = "Esperando comando..."
        Log.d(TAG, "🔇 EVA desactivado")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                switchEva.isChecked = true
            } else {
                Toast.makeText(this, "Necesitas dar permiso de micrófono", Toast.LENGTH_LONG).show()
            }
        }
    }
}
