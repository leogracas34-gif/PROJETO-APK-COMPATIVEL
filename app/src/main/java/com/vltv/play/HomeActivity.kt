package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Receiver de downloads
        DownloadHelper.registerReceiver(this)

        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        
        // Carrega o banner alternado (Filmes / Séries) otimizado para TV
        carregarBannerAlternado()

        // --- CORREÇÃO DO TECLADO E BUSCA ---
        try {
            // 1. Limpa o texto da busca
            binding.etSearch.setText("")
            
            // 2. Tira o foco da barra de busca
            binding.etSearch.clearFocus()

            // 3. Força o teclado a fechar
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)

            // 4. Joga o foco para o Banner (para não voltar para a busca sozinho)
            binding.cardBanner.requestFocus()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // -----------------------------------
    }

    private fun setupClicks() {
        fun isTelevisionDevice(): Boolean {
            return packageManager.hasSystemFeature("android.hardware.type.television") ||
                   packageManager.hasSystemFeature("android.software.leanback") ||
                   (resources.configuration.uiMode and
                   android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                   android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        }

        // --- 1. SETUP DO BOTÃO DE BUSCA (FOCO AMARELO + ZOOM) ---
        binding.etSearch.isFocusable = true
        binding.etSearch.isFocusableInTouchMode = true // Importante para TV Híbrida
        
        binding.etSearch.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Efeito Zoom
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                // Muda a cor do texto de dica para Amarelo Ouro
                binding.etSearch.setHintTextColor(Color.parseColor("#FFD700"))
            } else {
                // Volta ao normal
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                // Volta a cor original (Branco/Cinza Claro)
                binding.etSearch.setHintTextColor(Color.WHITE)
            }
        }

        // --- 2. SETUP DO BOTÃO SETTINGS (FOCO AMARELO + ZOOM) ---
        binding.btnSettings.isFocusable = true
        binding.btnSettings.isFocusableInTouchMode = true
        binding.btnSettings.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                binding.btnSettings.setColorFilter(Color.parseColor("#FFD700")) // Ícone Amarelo
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                binding.btnSettings.clearColorFilter() // Volta ao original
            }
        }

        // Lista de cards para setup comum
        val cards = listOf(binding.cardLiveTv, binding.cardMovies, binding.cardSeries, binding.cardBanner)
        
        cards.forEach { card ->
            card.isFocusable = true
            card.isClickable = true
            
            // Efeito visual de foco (TV)
            card.setOnFocusChangeListener { _, hasFocus ->
                card.scaleX = if (hasFocus) 1.05f else 1f
                card.scaleY = if (hasFocus) 1.05f else 1f
            }
            
            // Clique único (celular + TV ENTER)
            card.setOnClickListener {
                when (card.id) {
                    R.id.cardLiveTv -> startActivity(Intent(this, LiveTvActivity::class.java))
                    R.id.cardMovies -> startActivity(Intent(this, VodActivity::class.java))
                    R.id.cardSeries -> startActivity(Intent(this, SeriesActivity::class.java))
                    R.id.cardBanner -> { /* ação banner se quiser */ }
                }
            }
        }
        
        // D-PAD NAVEGAÇÃO (só ativa em TV)
        if (isTelevisionDevice()) {
            binding.cardLiveTv.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && 
                    event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardMovies.requestFocus()
                    true
                } else false
            }
            
            binding.cardMovies.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && 
                    event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardLiveTv.requestFocus()
                    true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && 
                           event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardSeries.requestFocus()
                    true
                } else false
            }
            
            binding.cardSeries.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && 
                    event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardMovies.requestFocus()
                    true
                } else false
            }
        }
        
        // Search + Settings
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

        // Foco inicial no banner
        binding.cardBanner.requestFocus()
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair e desconectar?")
            .setPositiveButton("Sim") { _, _ ->
                val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    // --- NOVA LÓGICA: Alterna entre Filmes e Séries e Ajusta para TV ---
    private fun carregarBannerAlternado() {
        val prefs = getSharedPreferences("vltv_home_prefs", Context.MODE_PRIVATE)
        
        // 1. Verifica o que mostrou na última vez (Padrão: começa com 'tv')
        val ultimoTipo = prefs.getString("ultimo_tipo_banner", "tv") ?: "tv"

        // 2. Inverte a escolha
        val tipoAtual = if (ultimoTipo == "tv") "movie" else "tv"

        // 3. Salva a escolha atual para a próxima vez
        prefs.edit().putString("ultimo_tipo_banner", tipoAtual).apply()

        // 4. Monta a URL da API
        val urlString =
            "https://api.themoviedb.org/3/trending/$tipoAtual/day?api_key=$TMDB_API_KEY&language=pt-BR"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTxt = URL(urlString).readText()
                val json = JSONObject(jsonTxt)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val randomIndex = Random.nextInt(results.length())
                    val item = results.getJSONObject(randomIndex)

                    // 5. Pega o título correto (filme usa 'title', série usa 'name')
                    val titulo = if (item.has("title"))
                        item.getString("title")
                    else if (item.has("name"))
                        item.getString("name")
                    else 
                        "Destaque"

                    val overview = if (item.has("overview"))
                        item.getString("overview")
                    else
                        ""

                    val backdropPath = item.getString("backdrop_path")
                    val prefixo = if (tipoAtual == "movie") "Filme em Alta: " else "Série em Alta: "

                    if (backdropPath != "null" && backdropPath.isNotBlank()) {
                        
                        // CORREÇÃO TV BOX: Usar 'w1280' em vez de 'original'
                        // Isso reduz drasticamente o consumo de memória, evitando fundo preto
                        val imageUrl = "https://image.tmdb.org/t/p/w1280$backdropPath"

                        withContext(Dispatchers.Main) {
                            binding.tvBannerTitle.text = "$prefixo$titulo"
                            binding.tvBannerOverview.text = overview

                            Glide.with(this@HomeActivity)
                                .load(imageUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL) // Salva em cache
                                .override(1280, 720) // Força resolução HD segura
                                .centerCrop()
                                .placeholder(android.R.color.black)
                                .into(binding.imgBanner)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
