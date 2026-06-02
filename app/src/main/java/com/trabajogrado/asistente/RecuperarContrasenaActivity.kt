package com.trabajogrado.asistente

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.util.Locale

class RecuperarContrasenaActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var btnEnviar: AppCompatButton
    private lateinit var progress: ProgressBar
    private lateinit var txtVolver: TextView
    private lateinit var txtMensajeExito: TextView
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recuperar_contrasena)

        auth = FirebaseAuth.getInstance()
        tts = TextToSpeech(this, this)

        etEmail = findViewById(R.id.etEmailRecuperar)
        btnEnviar = findViewById(R.id.btnEnviarRecuperar)
        progress = findViewById(R.id.progressRecuperar)
        txtVolver = findViewById(R.id.txtVolverLogin)
        txtMensajeExito = findViewById(R.id.txtMensajeExito)

        btnEnviar.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (validarEmail(email)) enviarCorreo(email)
        }

        txtVolver.setOnClickListener { finish() }
    }

    private fun validarEmail(email: String): Boolean {
        if (email.isEmpty()) {
            etEmail.error = "Escribe tu correo"
            etEmail.requestFocus()
            Toast.makeText(this, "Escribe tu correo electrónico", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Formato de correo no válido"
            etEmail.requestFocus()
            Toast.makeText(this, "El correo no tiene un formato válido", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun enviarCorreo(email: String) {
        mostrarCarga(true)
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                mostrarCarga(false)
                manejarExito(email)
            }
            .addOnFailureListener { e ->
                mostrarCarga(false)
                manejarError(e)
            }
    }

    private fun manejarExito(email: String) {
        btnEnviar.isEnabled = false
        btnEnviar.text = "Correo enviado"
        txtMensajeExito.visibility = View.VISIBLE

        val mensajeTTS = "Se envió el correo de recuperación a $email"
        hablar(mensajeTTS)
        Toast.makeText(this, "Revisa tu bandeja de entrada", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 3000)
    }

    private fun manejarError(e: Exception) {
        val mensaje = when (e) {
            is FirebaseAuthInvalidUserException ->
                "Este correo no está registrado en EVA"
            is FirebaseNetworkException ->
                "Sin conexión a internet. Revisa tu red e intenta de nuevo"
            else ->
                "No se pudo enviar el correo. Intenta de nuevo"
        }
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        hablar(mensaje)
    }

    private fun mostrarCarga(mostrar: Boolean) {
        progress.visibility = if (mostrar) View.VISIBLE else View.GONE
        btnEnviar.isEnabled = !mostrar
    }

    private fun hablar(texto: String) {
        if (ttsListo) {
            tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "recuperar_${System.currentTimeMillis()}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "CO")
            ttsListo = true
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
