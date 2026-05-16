package com.trabajogrado.asistente

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class AsistenteVozNuevoActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var isInForeground: Boolean = false
            private set
    }

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

    // Llamado cuando la Activity ya está en el stack y se trae al frente (launchMode singleTop).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EvaListeningService.EXTRA_START_LISTENING, false)) {
            tvEstadoEva.text = "🎤 EVA activado, di tu comando..."
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true

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

        // Si la Activity fue lanzada por detección del hotword desde segundo plano,
        // mostrar estado "escuchando" en lugar del estado de espera normal.
        val startListening = intent.getBooleanExtra(EvaListeningService.EXTRA_START_LISTENING, false)
        if (startListening) {
            tvEstadoEva.text = "🎤 EVA activado, di tu comando..."
            intent.removeExtra(EvaListeningService.EXTRA_START_LISTENING)
        } else {
            tvEstadoEva.text = if (EvaListeningService.isRunning)
                "👂 Esperando 'HOLA EVA'..."
            else
                "Di 'HOLA EVA' seguido de tu comando"
        }
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
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
        // SYSTEM_ALERT_WINDOW es obligatorio en MIUI para lanzar la app desde segundo plano.
        // Sin este permiso, el overlay no puede mostrarse y MIUI bloquea el Intent.
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "EVA necesita el permiso 'Aparecer encima de otras apps' para funcionar desde segundo plano. Actívalo y vuelve a activar EVA.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
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
