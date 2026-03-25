package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {

    private val PREFS_TEMA = "tema_preferencia"
    private val KEY_TEMA = "modo_oscuro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Aplicar tema guardado ANTES de setContentView
        val prefs = getSharedPreferences(PREFS_TEMA, MODE_PRIVATE)
        val modoOscuro = prefs.getBoolean(KEY_TEMA, true) // oscuro por defecto
        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuro) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        // Nombre del usuario
        val txtNombre = findViewById<TextView>(R.id.txtNombreUsuario)
        val uid = auth.currentUser!!.uid
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                val nombre = doc.getString("nombre") ?: auth.currentUser?.email ?: "Usuario"
                txtNombre.text = nombre.split(" ")[0] // solo el primer nombre
            }

        // Toggle tema
        // Toggle tema
        val btnToggle = findViewById<ImageButton>(R.id.btnToggleTema)
        btnToggle.setImageResource(
            if (modoOscuro) R.drawable.ic_sun
            else R.drawable.ic_moon
        )
        btnToggle.setOnClickListener {
            val nuevoModo = !modoOscuro
            prefs.edit().putBoolean(KEY_TEMA, nuevoModo).apply()
            btnToggle.setImageResource(
                if (nuevoModo) R.drawable.ic_sun
                else R.drawable.ic_moon
            )
            AppCompatDelegate.setDefaultNightMode(
                if (nuevoModo) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        // Navegación
        findViewById<CardView>(R.id.cardAsistenteVoz).setOnClickListener {
            startActivity(Intent(this, AsistenteVozNuevoActivity::class.java))
        }

        findViewById<CardView>(R.id.cardLectorPantalla).setOnClickListener {
            startActivity(Intent(this, ControlActivity::class.java))
        }

        findViewById<CardView>(R.id.cardLectorTexto).setOnClickListener {
            try {
                startActivity(Intent(this, OCRActivity::class.java))
            } catch (e: Exception) {
                Log.e("MainActivity", "Error: ${e.message}")
            }
        }

        findViewById<CardView>(R.id.cardNotificaciones).setOnClickListener {
            startActivity(Intent(this, NotificacionesActivity::class.java))
        }

        findViewById<CardView>(R.id.cardConfiguracion).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<TextView>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
    }
}