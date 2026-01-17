package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vltv.play.databinding.ActivityLoginStep2Binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class LoginStep2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginStep2Binding
    private var userLogin: String = ""

    // SEUS 6 SERVIDORES XTREAM MANTIDOS
    private val SERVERS = listOf(
        "http://tvblack.shop",
        "http://redeinternadestiny.top",
        "http://fibercdn.sbs",
        "http://vupro.shop",
        "http://blackdns.shop",
        "http://blackdeluxe.shop"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginStep2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuração de Tela Cheia
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())

        // Recupera o login digitado na tela anterior
        userLogin = intent.getStringExtra("USER_LOGIN") ?: ""
        binding.tvDisplayUser.text = "Entre no VLTV+ usando seu login $userLogin "

        setupDpadFocus()

        // Botão (editar) para voltar e corrigir o usuário
        binding.tvEditLogin.setOnClickListener { finish() }

        // Botão de Login Final
        binding.btnLoginFinal.setOnClickListener {
            val pass = binding.edtUserPassword.text.toString().trim()
            if (pass.isEmpty()) {
                Toast.makeText(this, "Digite sua senha", Toast.LENGTH_SHORT).show()
            } else {
                realizarLoginMultiServidor(userLogin, pass)
            }
        }
    }

    private fun realizarLoginMultiServidor(user: String, pass: String) {
        // Você precisará adicionar um ProgressBar no layout activity_login_step2 
        // ou remover estas linhas se não quiser usar o carregamento agora
        binding.btnLoginFinal.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            var lastError: String? = null

            for (server in SERVERS) {
                val base = if (server.endsWith("/")) server.dropLast(1) else server
                val urlString = "$base/player_api.php?username=$user&password=$pass"

                try {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    if (connection.responseCode == 200) {
                        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("dns", base)
                            putString("username", user)
                            putString("password", pass)
                            apply()
                        }
                        XtreamApi.setBaseUrl("$base/")
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    lastError = e.message
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    val intent = Intent(this@LoginStep2Activity, HomeActivity::class.java)
                    startActivity(intent)
                    finishAffinity() // Fecha todas as telas de login
                } else {
                    Toast.makeText(applicationContext, "Erro: $lastError", Toast.LENGTH_LONG).show()
                    binding.btnLoginFinal.isEnabled = true
                }
            }
        }
    }

    private fun setupDpadFocus() {
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
        }

        binding.tvEditLogin.onFocusChangeListener = focusListener
        binding.edtUserPassword.onFocusChangeListener = focusListener
        binding.btnLoginFinal.onFocusChangeListener = focusListener

        binding.edtUserPassword.requestFocus()

        binding.edtUserPassword.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                binding.btnLoginFinal.requestFocus()
                true
            } else false
        }
    }
}
