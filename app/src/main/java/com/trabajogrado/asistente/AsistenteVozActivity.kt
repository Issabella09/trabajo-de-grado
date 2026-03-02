package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AsistenteVozActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Redirigir a la nueva activity
        val intent = Intent(this, AsistenteVozNuevoActivity::class.java)
        startActivity(intent)
        finish()
    }
}