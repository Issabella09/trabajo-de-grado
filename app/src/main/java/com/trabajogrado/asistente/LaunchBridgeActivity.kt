package com.trabajogrado.asistente

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Activity transparente sin UI que actúa como puente para lanzar apps desde segundo plano.
 * EvaListeningService la inicia (con FLAG_ACTIVITY_NEW_TASK), y desde aquí — ya dentro de
 * un contexto de Activity — se lanza el Intent destino sin restricciones de Android 10+.
 */
class LaunchBridgeActivity : Activity() {

    companion object {
        const val EXTRA_TARGET_INTENT = "target_intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TARGET_INTENT)
        }

        if (target != null) {
            try {
                startActivity(target)
            } catch (e: Exception) {
                Log.e("LaunchBridge", "No se pudo lanzar destino: ${e.message}")
            }
        }

        finish()
    }
}
