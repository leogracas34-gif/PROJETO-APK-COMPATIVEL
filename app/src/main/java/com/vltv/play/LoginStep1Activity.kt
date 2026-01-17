package com.vltv.play

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vltv.play.databinding.ActivityLoginStep1Binding

class LoginStep1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginStep1Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginStep1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fullscreen
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())

        setupDpadFocus()

        binding.btnNextStep.setOnClickListener {
            val user = binding.edtUserLogin.text.toString().trim()
            if (user.isNotEmpty()) {
                val intent = Intent(this, LoginStep2Activity::class.java)
                intent.putExtra("USER_LOGIN", user)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Por favor, digite seu login", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDpadFocus() {
        // Listener de animação igual ao seu código original (Zoom Suave)
        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start()
                v.isSelected = true
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                v.isSelected = false
            }
        }

        // Aplicando nos elementos
        binding.edtUserLogin.onFocusChangeListener = focusListener
        binding.btnNextStep.onFocusChangeListener = focusListener

        // Forçar foco inicial no campo de texto para facilitar no controle remoto
        binding.edtUserLogin.requestFocus()

        // Facilitar navegação: Apertar ENTER no teclado da TV vai para o botão
        binding.edtUserLogin.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                binding.btnNextStep.requestFocus()
                true
            } else false
        }
    }
}
