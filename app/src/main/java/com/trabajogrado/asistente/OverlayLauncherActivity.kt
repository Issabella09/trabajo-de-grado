package com.trabajogrado.asistente

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Activity transparente sin UI. Se lanza desde EvaListeningService con un overlay activo
 * (WindowManager TYPE_APPLICATION_OVERLAY) para sortear las restricciones de lanzamiento
 * en segundo plano de MIUI/Android 10+. Una vez en primer plano, lanza el intent
 * pendiente (guardado en EvaListeningService.pendingLaunchIntent) y termina de inmediato.
 */
class OverlayLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pending = EvaListeningService.pendingLaunchIntent
        EvaListeningService.pendingLaunchIntent = null

        if (pending != null) {
            try {
                startActivity(pending)
                Log.d("OverlayLauncher", "✅ Intent lanzado: ${pending.component ?: pending.action}")
            } catch (e: Exception) {
                Log.e("OverlayLauncher", "❌ Error lanzando intent: ${e.message}")
            }
        }

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Quitar el overlay del WindowManager en cuanto esta Activity termina
        EvaListeningService.instance?.removerOverlay()
    }
}
