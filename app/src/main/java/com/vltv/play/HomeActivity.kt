package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.vltv.play.databinding.ActivityHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.random.Random

// --- MODELO DE DADOS PARA O HISTÓRICO ---
data class HistoryItem(
    val id: Int,
    val title: String,
    val image: String,
    val type: String, // "movie" ou "series"
    val position: Long,
    val duration: Long
)

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"
    private lateinit var historyAdapter: ContinueWatchingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        DownloadHelper.registerReceiver(this)

        setupClicks()
        setupContinueWatchingRecycler()
        
        // Aplica o espaçamento extra para TV
        if (isTelevisionDevice()) {
            ajustarLayoutTV()
        }
    }

    override fun onResume() {
        super.onResume()
        carregarBannerAlternado()
        carregarHistorico() // Atualiza a lista de continuar assistindo

        try {
            binding.etSearch.setText("")
            binding.etSearch.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
            binding.cardBanner.requestFocus()
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- CONFIGURAÇÃO DA LISTA "CONTINUAR ASSISTINDO" ---
    private fun setupContinueWatchingRecycler() {
        historyAdapter = ContinueWatchingAdapter { item ->
            // Ao clicar no item do histórico, abre direto
            abrirItemHistorico(item)
        }
        
        binding.rvContinueWatching.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = historyAdapter
            setHasFixedSize(true)
            // Importante para o foco não ficar preso
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
    }

    private fun carregarHistorico() {
        // Carrega do "Banco" (SharedPreferences)
        val lista = HistoryRepository.getHistory(this)
        
        if (lista.isNotEmpty()) {
            binding.tvContinueTitle.visibility = View.VISIBLE
            binding.rvContinueWatching.visibility = View.VISIBLE
            historyAdapter.submitList(lista)
            
            // Ajusta o foco do Banner para descer para a lista
            binding.cardBanner.nextFocusDownId = R.id.rvContinueWatching
        } else {
            // Se vazio, esconde tudo para não poluir
            binding.tvContinueTitle.visibility = View.GONE
            binding.rvContinueWatching.visibility = View.GONE
            
            // Ajusta o foco do Banner para pular direto para os botões principais
            binding.cardBanner.nextFocusDownId = R.id.cardMovies
        }
    }

    private fun abrirItemHistorico(item: HistoryItem) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", item.id)
        intent.putExtra("stream_type", item.type)
        intent.putExtra("channel_name", item.title)
        intent.putExtra("start_position_ms", item.position) // Já começa de onde parou
        startActivity(intent)
    }

    // --- CORREÇÃO DE LAYOUT PARA TV (BOTÕES MENORES E SEPARADOS) ---
    private fun ajustarLayoutTV() {
        val scaleFactor = 0.9f 
        
        // Reduz tamanho visual
        binding.cardLiveTv.scaleX = scaleFactor
        binding.cardLiveTv.scaleY = scaleFactor
        binding.cardMovies.scaleX = scaleFactor
        binding.cardMovies.scaleY = scaleFactor
        binding.cardSeries.scaleX = scaleFactor
        binding.cardSeries.scaleY = scaleFactor

        // Tenta adicionar margens extras via código para garantir separação
        try {
            val marginPx = 24 
            if (binding.cardMovies.layoutParams is ViewGroup.MarginLayoutParams) {
                binding.cardMovies.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    setMargins(marginPx, topMargin, marginPx, bottomMargin)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
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
            
            card.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start()
                    try {
                        view.setBackgroundResource(R.drawable.focus_border)
                    } catch (e: Exception) {
                        view.setBackgroundColor(Color.parseColor("#FFD700"))
                        view.setPadding(6,6,6,6)
                    }
                    view.elevation = 20f
                } else {
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

    // --- ADAPTER INTERNO PARA O HISTÓRICO ---
    inner class ContinueWatchingAdapter(private val onClick: (HistoryItem) -> Unit) : 
        RecyclerView.Adapter<ContinueWatchingAdapter.VH>() {
        
        private var items = listOf<HistoryItem>()

        fun submitList(newItems: List<HistoryItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val imgPoster: android.widget.ImageView = v.findViewById(R.id.imgPoster)
            val progressBar: android.widget.ProgressBar = v.findViewById(R.id.progressBar) // Opcional se tiver no layout
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // Usa o mesmo layout de VOD pois é compatível (capa vertical)
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vod, parent, false)
            // Ajusta tamanho para ficar menor e elegante (horizontal strip)
            v.layoutParams = ViewGroup.MarginLayoutParams(240, 360).apply {
                setMargins(0, 0, 24, 0)
            }
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            
            // Tira o texto do layout reaproveitado, deixa só a imagem
            holder.itemView.findViewById<android.widget.TextView>(R.id.tvName)?.visibility = View.GONE
            
            Glide.with(holder.itemView.context)
                .load(item.image)
                .override(240, 360)
                .centerCrop()
                .into(holder.imgPoster)

            holder.itemView.isFocusable = true
            holder.itemView.isClickable = true
            
            // Foco: Borda Amarela + Zoom
            holder.itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    try {
                        view.setBackgroundResource(R.drawable.focus_border)
                    } catch (e: Exception) {
                        view.setBackgroundColor(Color.parseColor("#FFD700"))
                        view.setPadding(4,4,4,4)
                    }
                    view.elevation = 10f
                } else {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    view.setBackgroundResource(0)
                    view.setPadding(0,0,0,0)
                    view.elevation = 0f
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}

// --- REPOSITÓRIO ESTÁTICO DE HISTÓRICO ---
// Use HistoryRepository.add(context, item) em PlayerActivity/DetailsActivity para salvar
object HistoryRepository {
    private const val PREF_NAME = "vltv_history"
    private const val KEY_HISTORY = "watch_history_list"

    fun getHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<HistoryItem>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(HistoryItem(
                    id = obj.getInt("id"),
                    title = obj.getString("title"),
                    image = obj.getString("image"),
                    type = obj.getString("type"),
                    position = obj.getLong("pos"),
                    duration = obj.optLong("dur", 0)
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list.reversed() // Mostra os mais recentes primeiro
    }

    fun add(context: Context, item: HistoryItem) {
        val list = getHistory(context).toMutableList()
        // Remove duplicado se já existir (para atualizar posição e mover pro topo)
        list.removeAll { it.id == item.id && it.type == item.type }
        list.add(item)
        
        // Limita a 20 itens
        if (list.size > 20) list.removeAt(0)

        val jsonArray = JSONArray()
        for (h in list) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("title", h.title)
            obj.put("image", h.image)
            obj.put("type", h.type)
            obj.put("pos", h.position)
            obj.put("dur", h.duration)
            jsonArray.put(obj)
        }
        
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
}
