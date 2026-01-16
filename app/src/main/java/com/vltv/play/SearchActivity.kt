package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

// Modelo de dados simples para a busca
data class SearchResultItem(
    val id: Int,
    val title: String,
    val type: String, // "movie", "series", "live"
    val extraInfo: String?,
    val iconUrl: String?
)

class SearchActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var etQuery: EditText
    private lateinit var btnDoSearch: ImageButton
    private lateinit var rvResults: RecyclerView
    private lateinit var adapter: SearchResultAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    
    private val supervisor = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisor

    private var catalogoCompleto: List<SearchResultItem> = emptyList()
    private var isCarregandoDados = false
    private var jobBuscaInstantanea: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        initViews()
        setupRecyclerView()
        setupSearchLogic()
        
        carregarDadosIniciais()
    }

    private fun initViews() {
        etQuery = findViewById(R.id.etQuery)
        btnDoSearch = findViewById(R.id.btnDoSearch)
        rvResults = findViewById(R.id.rvResults)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        // --- CORREÇÃO DE FOCO NO CAMPO DE BUSCA ---
        etQuery.isFocusable = true
        etQuery.isFocusableInTouchMode = true
        
        etQuery.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Zoom
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                // Texto Amarelo para indicar foco
                etQuery.setTextColor(Color.parseColor("#FFD700"))
                etQuery.setHintTextColor(Color.parseColor("#CCFFD700")) // Amarelo translúcido no hint
                
                // --- FORÇA O TECLADO A SUBIR ---
                showKeyboard(etQuery)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                etQuery.setTextColor(Color.WHITE)
                etQuery.setHintTextColor(Color.LTGRAY)
            }
        }

        // Clique no OK abre o teclado
        etQuery.setOnClickListener { showKeyboard(etQuery) }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupRecyclerView() {
        adapter = SearchResultAdapter { item ->
            abrirDetalhes(item)
        }

        rvResults.layoutManager = GridLayoutManager(this, 5)
        rvResults.adapter = adapter
        rvResults.isFocusable = true
        rvResults.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun setupSearchLogic() {
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val texto = s.toString().trim()
                if (isCarregandoDados) return 
                jobBuscaInstantanea?.cancel()
                jobBuscaInstantanea = launch {
                    delay(100) 
                    filtrarNaMemoria(texto)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnDoSearch.setOnClickListener { 
            filtrarNaMemoria(etQuery.text.toString().trim()) 
            showKeyboard(etQuery) // Garante teclado se clicar no botão
        }

        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                filtrarNaMemoria(etQuery.text.toString().trim())
                // Esconde teclado ao confirmar
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etQuery.windowToken, 0)
                true
            } else false
        }
    }

    private fun carregarDadosIniciais() {
        isCarregandoDados = true
        progressBar.visibility = View.VISIBLE
        tvEmpty.text = "Carregando catálogo completo..."
        tvEmpty.visibility = View.VISIBLE
        
        // NÃO DESABILITE O EDITTEXT, APENAS O TRAVE VISUALMENTE SE NECESSÁRIO
        // Desabilitar impede o foco de chegar nele na TV

        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        launch {
            try {
                val resultados = withContext(Dispatchers.IO) {
                    val deferredFilmes = async { buscarFilmes(username, password) }
                    val deferredSeries = async { buscarSeries(username, password) }
                    val deferredCanais = async { buscarCanais(username, password) }

                    val lista1 = deferredFilmes.await()
                    val lista2 = deferredSeries.await()
                    val lista3 = deferredCanais.await()
                    
                    lista1 + lista2 + lista3
                }

                catalogoCompleto = resultados
                isCarregandoDados = false
                
                progressBar.visibility = View.GONE
                tvEmpty.visibility = View.GONE
                
                // Foco inicial na busca para digitar logo
                etQuery.requestFocus()
                
                val initial = intent.getStringExtra("initial_query")
                if (!initial.isNullOrBlank()) {
                    etQuery.setText(initial)
                    filtrarNaMemoria(initial)
                } else {
                    tvEmpty.text = "Digite para buscar..."
                    tvEmpty.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                isCarregandoDados = false
                progressBar.visibility = View.GONE
                tvEmpty.text = "Erro ao carregar dados. Tente novamente."
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun filtrarNaMemoria(query: String) {
        if (catalogoCompleto.isEmpty()) return

        if (query.length < 2) {
            adapter.submitList(emptyList())
            tvEmpty.text = "Digite para buscar..."
            tvEmpty.visibility = View.VISIBLE
            return
        }

        val qNorm = query.lowercase().trim()

        val resultadosFiltrados = catalogoCompleto.filter { item ->
            item.title.lowercase().contains(qNorm)
        }.take(100) 

        adapter.submitList(resultadosFiltrados)
        
        if (resultadosFiltrados.isEmpty()) {
            tvEmpty.text = "Nenhum resultado encontrado."
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    // --- FUNÇÕES DE API ---
    private fun buscarFilmes(u: String, p: String): List<SearchResultItem> {
        return try {
            val response = XtreamApi.service.getAllVodStreams(u, p).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.map {
                    SearchResultItem(it.id, it.name ?: "Sem Título", "movie", it.rating, it.icon)
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun buscarSeries(u: String, p: String): List<SearchResultItem> {
        return try {
            val response = XtreamApi.service.getAllSeries(u, p).execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.map {
                    SearchResultItem(it.id, it.name ?: "Sem Título", "series", it.rating, it.icon)
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun buscarCanais(u: String, p: String): List<SearchResultItem> {
        return try {
            val response = XtreamApi.service.getLiveStreams(u, p, categoryId = "0").execute()
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.map {
                    SearchResultItem(it.id, it.name ?: "Sem Nome", "live", null, it.icon)
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private fun abrirDetalhes(item: SearchResultItem) {
        when (item.type) {
            "movie" -> {
                val i = Intent(this, DetailsActivity::class.java)
                i.putExtra("stream_id", item.id)
                i.putExtra("name", item.title)
                i.putExtra("icon", item.iconUrl ?: "")
                i.putExtra("rating", item.extraInfo ?: "0.0")
                startActivity(i)
            }
            "series" -> {
                val i = Intent(this, SeriesDetailsActivity::class.java)
                i.putExtra("series_id", item.id)
                i.putExtra("name", item.title)
                i.putExtra("icon", item.iconUrl ?: "")
                i.putExtra("rating", item.extraInfo ?: "0.0")
                startActivity(i)
            }
            "live" -> {
                val i = Intent(this, PlayerActivity::class.java)
                i.putExtra("stream_id", item.id)
                i.putExtra("stream_type", "live")
                i.putExtra("channel_name", item.title)
                startActivity(i)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        supervisor.cancel()
    }

    // --- ADAPTER INTERNO (COM BORDA AMARELA) ---
    class SearchResultAdapter(private val onClick: (SearchResultItem) -> Unit) : 
        ListAdapter<SearchResultItem, SearchResultAdapter.VH>(DiffCallback) {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName) // Precisa existir no layout item_vod ou item_search
            val imgPoster: ImageView = v.findViewById(R.id.imgPoster)

            fun bind(item: SearchResultItem, onClick: (SearchResultItem) -> Unit) {
                tvName.text = item.title
                
                // Glide Leve
                Glide.with(itemView.context)
                    .load(item.iconUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(200, 300)
                    .centerCrop()
                    .placeholder(R.drawable.bg_logo_placeholder)
                    .error(R.drawable.bg_logo_placeholder)
                    .into(imgPoster)

                itemView.isFocusable = true
                itemView.isClickable = true
                
                // Lógica de Foco Amarelo
                itemView.setOnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        
                        try {
                            view.setBackgroundResource(R.drawable.focus_border)
                        } catch (e: Exception) {
                            view.setBackgroundColor(Color.parseColor("#FFD700"))
                            view.setPadding(4,4,4,4)
                        }
                        
                        tvName.setTextColor(Color.parseColor("#FFD700"))
                        tvName.setBackgroundColor(Color.parseColor("#CC000000"))
                        view.elevation = 10f
                    } else {
                        view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                        
                        view.setBackgroundResource(0)
                        view.setPadding(0,0,0,0)
                        
                        tvName.setTextColor(Color.WHITE)
                        tvName.setBackgroundColor(Color.TRANSPARENT)
                        view.elevation = 0f
                    }
                }

                itemView.setOnClickListener { onClick(item) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // Reutilizando layout de VOD (item_vod) pois é compatível
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vod, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position), onClick)
        }

        object DiffCallback : DiffUtil.ItemCallback<SearchResultItem>() {
            override fun areItemsTheSame(o: SearchResultItem, n: SearchResultItem) = o.id == n.id
            override fun areContentsTheSame(o: SearchResultItem, n: SearchResultItem) = o == n
        }
    }
}
