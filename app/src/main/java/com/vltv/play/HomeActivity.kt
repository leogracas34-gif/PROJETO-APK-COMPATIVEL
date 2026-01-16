package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updateMargins
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vltv.play.databinding.ActivityHomeBinding
import com.vltv.play.DownloadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.random.Random

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        DownloadHelper.registerReceiver(this)

        setupClicks()
        
        // Aplica o espaçamento extra para TV
        if (isTelevisionDevice()) {
            ajustarLayoutTV()
        }
    }

    override fun onResume() {
        super.onResume()
        carregarBannerAlternado()

        try {
            binding.etSearch.setText("")
            binding.etSearch.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            binding.cardBanner.requestFocus()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- CORREÇÃO DE LAYOUT PARA TV ---
    private fun ajustarLayoutTV() {
        // Reduz um pouco o tamanho dos cards para não ficarem gigantes na TV 55"
        val scaleFactor = 0.9f 
        binding.cardLiveTv.scaleX = scaleFactor
        binding.cardLiveTv.scaleY = scaleFactor
        binding.cardMovies.scaleX = scaleFactor
        binding.cardMovies.scaleY = scaleFactor
        binding.cardSeries.scaleX = scaleFactor
        binding.cardSeries.scaleY = scaleFactor

        // Adiciona margem entre os cartões para não colarem
        // Tenta ajustar layout params (depende do container pai ser LinearLayout ou ConstraintLayout)
        try {
            val marginPx = 24 // Espaço generoso entre os botões
            
            // Verifica o tipo de LayoutParams antes de castar
            if (binding.cardMovies.layoutParams is ViewGroup.MarginLayoutParams) {
                binding.cardMovies.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(marginPx, topMargin, marginPx, bottomMargin)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isTelevisionDevice(): Boolean {
        return packageManager.hasSystemFeature("android.hardware.type.television") ||
               packageManager.hasSystemFeature("android.software.leanback") ||
               (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun setupClicks() {
        binding.etSearch.isFocusable = true
        binding.etSearch.isFocusableInTouchMode = true 
        
        binding.etSearch.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                binding.etSearch.setHintTextColor(Color.parseColor("#FFD700"))
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                binding.etSearch.setHintTextColor(Color.WHITE)
            }
        }

        binding.btnSettings.isFocusable = true
        binding.btnSettings.isFocusableInTouchMode = true
        binding.btnSettings.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                binding.btnSettings.setColorFilter(Color.parseColor("#FFD700"))
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                binding.btnSettings.clearColorFilter()
            }
        }

        val cards = listOf(binding.cardLiveTv, binding.cardMovies, binding.cardSeries, binding.cardBanner)
        
        cards.forEach { card ->
            card.isFocusable = true
            card.isClickable = true
            
            // EFEITO DE FOCO ESPECIAL (Zoom + Borda Amarela)
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    // Zoom mais forte para destacar bem
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                    
                    // Borda Amarela
                    try {
                        view.setBackgroundResource(R.drawable.focus_border)
                    } catch (e: Exception) {
                        view.setBackgroundColor(Color.parseColor("#FFD700"))
                        view.setPadding(6,6,6,6)
                    }
                    view.elevation = 20f
                } else {
                    // Volta ao tamanho reduzido padrão da TV (0.9f) ou normal (1.0f)
                    val normalScale = if (isTelevisionDevice()) 0.9f else 1.0f
                    view.animate().scaleX(normalScale).scaleY(normalScale).setDuration(200).start()
                    
                    view.setBackgroundResource(0)
                    view.setPadding(0,0,0,0)
                    view.elevation = 0f
                }
            }
            
            card.setOnClickListener {
                when (card.id) {
                    R.id.cardLiveTv -> startActivity(Intent(this, LiveTvActivity::class.java))
                    R.id.cardMovies -> startActivity(Intent(this, VodActivity::class.java))
                    R.id.cardSeries -> startActivity(Intent(this, SeriesActivity::class.java))
                    R.id.cardBanner -> { }
                }
            }
        }
        
        if (isTelevisionDevice()) {
            binding.cardLiveTv.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardMovies.requestFocus()
                    true
                } else false
            }
            
            binding.cardMovies.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardLiveTv.requestFocus()
                    true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardSeries.requestFocus()
                    true
                } else false
            }
            
            binding.cardSeries.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardMovies.requestFocus()
                    true
                } else false
            }
        }
        
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val texto = v.text.toString().trim()
                if (texto.isNotEmpty()) {
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.putExtra("initial_query", texto)
                    startActivity(intent)
                }
                true
            } else false
        }

        binding.btnSettings.setOnClickListener {
            val itens = arrayOf("Meus downloads", "Configurações", "Sair")
            AlertDialog.Builder(this)
                .setTitle("Opções")
                .setItems(itens) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, DownloadsActivity::class.java))
                        1 -> startActivity(Intent(this, SettingsActivity::class.java))
                        2 -> mostrarDialogoSair()
                    }
                }
                .show()
        }

        binding.cardBanner.requestFocus()
    }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair e desconectar?")
            .setPositiveButton("Sim") { _, _ ->
                val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun carregarBannerAlternado() {
        val prefs = getSharedPreferences("vltv_home_prefs", Context.MODE_PRIVATE)
        val ultimoTipo = prefs.getString("ultimo_tipo_banner", "tv") ?: "tv"
        val tipoAtual = if (ultimoTipo == "tv") "movie" else "tv"
        prefs.edit().putString("ultimo_tipo_banner", tipoAtual).apply()

        val urlString = "https://api.themoviedb.org/3/trending/$tipoAtual/day?api_key=$TMDB_API_KEY&language=pt-BR"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTxt = URL(urlString).readText()
                val json = JSONObject(jsonTxt)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val randomIndex = Random.nextInt(results.length())
                    val item = results.getJSONObject(randomIndex)

                    val titulo = if (item.has("title")) item.getString("title") else if (item.has("name")) item.getString("name") else "Destaque"
                    val overview = if (item.has("overview")) item.getString("overview") else ""
                    val backdropPath = item.getString("backdrop_path")
                    val prefixo = if (tipoAtual == "movie") "Filme em Alta: " else "Série em Alta: "

                    if (backdropPath != "null" && backdropPath.isNotBlank()) {
                        val imageUrl = "https://image.tmdb.org/t/p/w1280$backdropPath"

                        withContext(Dispatchers.Main) {
                            binding.tvBannerTitle.text = "$prefixo$titulo"
                            binding.tvBannerOverview.text = overview

                            Glide.with(this@HomeActivity)
                                .load(imageUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .override(1280, 720)
                                .centerCrop()
                                .placeholder(android.R.color.black)
                                .into(binding.imgBanner)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mostrarDialogoSair()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
