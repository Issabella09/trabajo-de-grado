package com.trabajogrado.asistente

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import android.widget.TextView

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: AppCompatButton
    private lateinit var btnIrARegistro: AppCompatButton
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnIrARegistro = findViewById(R.id.btnIrARegistro)
        progress = findViewById(R.id.progressLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            mostrarCarga(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    mostrarCarga(false)
                    irAMain()
                }
                .addOnFailureListener { e ->
                    mostrarCarga(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        btnIrARegistro.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        val btnOlvidePassword = findViewById<TextView>(R.id.btnOlvidePassword)
        btnOlvidePassword.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Escribe tu correo primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Correo de recuperación enviado", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun mostrarCarga(mostrar: Boolean) {
        progress.visibility = if (mostrar) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !mostrar
    }

    private fun irAMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}