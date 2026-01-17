package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vltv.play.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializa o View Binding
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Deixa a tela em Fullscreen (igual ao seu LoginActivity)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())

        // VERIFICAÇÃO DE AUTO-LOGIN (Sua lógica original)
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)

        if (!savedUser.isNullOrBlank() && !savedPass.isNullOrBlank()) {
            startHomeActivity()
            return
        }

        // Configuração do Botão "COMEÇAR"
        binding.btnStart.setOnClickListener {
            val intent = Intent(this, LoginStep1Activity::class.java)
            startActivity(intent)
        }
        
        // Efeito de Foco para TV no botão
        binding.btnStart.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
            }
        }
    }

    private fun startHomeActivity() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val savedDns = prefs.getString("dns", null)
        if (!savedDns.isNullOrBlank()) {
            XtreamApi.setBaseUrl(if (savedDns.endsWith("/")) savedDns else "$savedDns/")
        }
        
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
