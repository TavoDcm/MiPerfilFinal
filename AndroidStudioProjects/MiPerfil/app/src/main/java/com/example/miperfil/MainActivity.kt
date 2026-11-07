package com.example.miperfil

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    // CAMBIAR por la IPv4 de tu PC si pruebas con un celular real.
    // Emulador Android: http://10.0.2.2:8000
    private val servidor = "http://192.168.1.11:8000"
    private var token = ""
    private lateinit var fotoPerfil: ImageView

    private val camara = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { foto: Bitmap? ->
        if (foto != null) fotoPerfil.setImageBitmap(foto)
    }

    private val galeria = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) fotoPerfil.setImageURI(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fotoPerfil = findViewById(R.id.fotoPerfil)

        findViewById<Button>(R.id.irRegistro).setOnClickListener { mostrar("registro") }
        findViewById<Button>(R.id.irLogin).setOnClickListener { mostrar("login") }
        findViewById<Button>(R.id.botonRegistrar).setOnClickListener { registrar() }
        findViewById<Button>(R.id.botonEntrar).setOnClickListener { entrar() }
        findViewById<Button>(R.id.botonFoto).setOnClickListener { camara.launch(null) }
        findViewById<Button>(R.id.botonGaleria).setOnClickListener {
            galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<Button>(R.id.botonSalir).setOnClickListener {
            if (token.isNotBlank()) peticion("/logout", "POST", JSONObject(), token) { _, _ -> }
            token = ""
            fotoPerfil.setImageDrawable(null)
            mostrar("login")
        }
        mostrar("login")
    }

    private fun mostrar(pantalla: String) {
        findViewById<View>(R.id.panelLogin).visibility = if (pantalla == "login") View.VISIBLE else View.GONE
        findViewById<View>(R.id.panelRegistro).visibility = if (pantalla == "registro") View.VISIBLE else View.GONE
        findViewById<View>(R.id.panelPerfil).visibility = if (pantalla == "perfil") View.VISIBLE else View.GONE
    }

    private fun valor(id: Int): String = findViewById<EditText>(id).text.toString().trim()

    private fun registrar() {
        val nombre = valor(R.id.registroNombre)
        val correo = valor(R.id.registroCorreo)
        val clave = valor(R.id.registroClave)
        if (nombre.length < 2 || !correo.contains("@") || clave.length < 8) {
            aviso("Revisa el nombre, correo y contraseña (mínimo 8 caracteres)")
            return
        }
        val datos = JSONObject().put("nombre", nombre).put("correo", correo).put("contrasena", clave)
        peticion("/registro", "POST", datos) { correcto, respuesta ->
            if (correcto) {
                aviso("Cuenta creada. Ahora inicia sesión")
                mostrar("login")
            } else aviso(respuesta.optString("detail", "No se pudo registrar"))
        }
    }

    private fun entrar() {
        val correo = valor(R.id.loginCorreo)
        val clave = valor(R.id.loginClave)
        if (correo.isBlank() || clave.isBlank()) {
            aviso("Escribe tu correo y contraseña")
            return
        }
        val datos = JSONObject().put("correo", correo).put("contrasena", clave)
        peticion("/login", "POST", datos) { correcto, respuesta ->
            if (correcto) {
                token = respuesta.optString("token")
                cargarPerfil()
            } else aviso(respuesta.optString("detail", "No se pudo iniciar sesión"))
        }
    }

    private fun cargarPerfil() {
        peticion("/perfil", "GET", null, token) { correcto, respuesta ->
            if (correcto) {
                findViewById<TextView>(R.id.textoNombre).text = respuesta.optString("nombre")
                findViewById<TextView>(R.id.textoCorreo).text = respuesta.optString("correo")
                fotoPerfil.setImageDrawable(null)
                mostrar("perfil")
            } else aviso(respuesta.optString("detail", "No se pudo cargar el perfil"))
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    private fun peticion(
        ruta: String,
        metodo: String,
        datos: JSONObject?,
        sesion: String = "",
        terminado: (Boolean, JSONObject) -> Unit
    ) {
        thread {
            var conexion: HttpURLConnection? = null
            try {
                conexion = URL(servidor + ruta).openConnection() as HttpURLConnection
                conexion.requestMethod = metodo
                conexion.connectTimeout = 7000
                conexion.readTimeout = 7000
                conexion.setRequestProperty("Accept", "application/json")
                if (sesion.isNotBlank()) conexion.setRequestProperty("Authorization", "Bearer $sesion")
                if (datos != null) {
                    conexion.doOutput = true
                    conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conexion.outputStream.use { it.write(datos.toString().toByteArray(Charsets.UTF_8)) }
                }
                val codigo = conexion.responseCode
                val stream = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
                val texto = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                val respuesta = JSONObject(texto)
                runOnUiThread { terminado(codigo in 200..299, respuesta) }
            } catch (e: Exception) {
                runOnUiThread { aviso("Sin conexión con el servidor. Revisa la IP y el Wi-Fi") }
            } finally {
                conexion?.disconnect()
            }
        }
    }
}
