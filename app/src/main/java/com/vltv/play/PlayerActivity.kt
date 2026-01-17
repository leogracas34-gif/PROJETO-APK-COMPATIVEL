package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.ArrayList

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loading: View
    private lateinit var tvChannelName: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var btnAspect: ImageButton
    private lateinit var topBar: View

    private lateinit var nextEpisodeContainer: View
    private lateinit var tvNextEpisodeTitle: TextView
    private lateinit var btnPlayNextEpisode: Button

    private var player: ExoPlayer? = null

    private var streamId = 0
    private var streamExtension = "ts"
    private var streamType = "live"
    private var nextStreamId: Int = 0
    private var nextChannelName: String? = null
    private var startPositionMs: Long = 0L

    private var offlineUri: String? = null

    // MOCHILA DE EPISÓDIOS
    private var episodeList = ArrayList<Int>()

    // Lista de Backup (Se o DNS do login falhar, usa esses)
    private val serverBackupList = listOf(
        "http://tvblack.shop",
        "http://firewallnaousardns.xyz:80",
        "http://fibercdn.sbs"
    )

    // Lista Ativa (DNS do Login + Backups)
    private val activeServerList = mutableListOf<String>()

    private var serverIndex = 0
    private val extensoesTentativa = mutableListOf<String>()
    private var extIndex = 0

    private val USER_AGENT = "IPTVSmartersPro"

    private val handler = Handler(Looper.getMainLooper())
    
    // Verificador de tempo para botão "Próximo Episódio"
    private val nextChecker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (streamType == "series" && nextStreamId != 0) {
                val dur = p.duration
                val pos = p.currentPosition
                if (dur > 0) {
                    val remaining = dur - pos
                    // Aparece nos últimos 60 segundos
                    if (remaining in 1..60_000) {
                        val seconds = (remaining / 1000L).toInt()
                        tvNextEpisodeTitle.text = "Próximo episódio em ${seconds}s"
                        
                        if (nextEpisodeContainer.visibility != View.VISIBLE) {
                            nextEpisodeContainer.visibility = View.VISIBLE
                            // Esconde a barra e foca no botão para facilitar na TV
                            playerView.hideController()
                            btnPlayNextEpisode.requestFocus()
                        }
                        
                        if (remaining <= 1000L) {
                            nextEpisodeContainer.visibility = View.GONE
                        }
                    } else if (remaining > 60_000) {
                        nextEpisodeContainer.visibility = View.GONE
                    }
                }
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Configuração de Tela Cheia Imersiva
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        // Vincular Views do XML
        playerView = findViewById(R.id.playerView)
        loading = findViewById(R.id.loading)
        tvChannelName = findViewById(R.id.tvChannelName)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        btnAspect = findViewById(R.id.btnAspect)
        topBar = findViewById(R.id.topBar)

        nextEpisodeContainer = findViewById(R.id.nextEpisodeContainer)
        tvNextEpisodeTitle = findViewById(R.id.tvNextEpisodeTitle)
        btnPlayNextEpisode = findViewById(R.id.btnPlayNextEpisode)

        setupFocusVisuals()

        // Pegar dados da Intent
        streamId = intent.getIntExtra("stream_id", 0)
        streamExtension = intent.getStringExtra("stream_ext") ?: "ts"
        streamType = intent.getStringExtra("stream_type") ?: "live"
        startPositionMs = intent.getLongExtra("start_position_ms", 0L)
        nextStreamId = intent.getIntExtra("next_stream_id", 0)
        nextChannelName = intent.getStringExtra("next_channel_name")

        // CORREÇÃO CRÍTICA: Se for filme/série e veio como "ts" por engano, força mp4
        if ((streamType == "movie" || streamType == "series") && streamExtension == "ts") {
            streamExtension = "mp4"
        }

        // Pega a lista de episódios
        val listaExtra = intent.getIntegerArrayListExtra("episode_list")
        if (listaExtra != null) {
            episodeList = listaExtra
        }

        calcularProximoEpisodioAutomaticamente()

        offlineUri = intent.getStringExtra("offline_uri")

        val channelName = intent.getStringExtra("channel_name") ?: ""
        tvChannelName.text = if (channelName.isNotBlank()) channelName else "Canal"
        tvNowPlaying.text = if (streamType == "live") "Carregando programação..." else ""

        // Botão de Aspecto (Zoom)
        btnAspect.setOnClickListener {
            val current = playerView.resizeMode
            val next = when (current) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                    Toast.makeText(this, "Modo: Preencher", Toast.LENGTH_SHORT).show()
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                    Toast.makeText(this, "Modo: Zoom", Toast.LENGTH_SHORT).show()
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                else -> {
                    Toast.makeText(this, "Modo: Padrão", Toast.LENGTH_SHORT).show()
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
            playerView.resizeMode = next
        }

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                topBar.visibility = visibility
            }
        )

        btnPlayNextEpisode.setOnClickListener {
            if (nextStreamId != 0) {
                abrirProximoEpisodio()
            } else {
                Toast.makeText(this, "Sem próximo episódio", Toast.LENGTH_SHORT).show()
            }
        }

        // Configura as extensões para tentativa de erro
        if (streamType == "movie" || streamType == "series") {
            extensoesTentativa.clear()
            extensoesTentativa.add(streamExtension) // A que veio na intent (ex: mkv)
            if (streamExtension != "mp4") extensoesTentativa.add("mp4")
            if (streamExtension != "mkv") extensoesTentativa.add("mkv")
        } else {
            // Live TV
            extensoesTentativa.clear()
            extensoesTentativa.add("ts")
            extensoesTentativa.add("m3u8")
        }

        setupServerList()
        iniciarPlayer()

        if (streamType == "live" && streamId != 0) {
            carregarEpg()
        }

        if (streamType == "series" && nextStreamId != 0) {
            handler.post(nextChecker)
        }
    }

    private fun setupFocusVisuals() {
        // Botão Próximo Episódio: Amarelo Ouro e Zoom
        btnPlayNextEpisode.isFocusable = true
        btnPlayNextEpisode.isFocusableInTouchMode = true
        btnPlayNextEpisode.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                btnPlayNextEpisode.setTextColor(Color.parseColor("#FFD700"))
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                btnPlayNextEpisode.setTextColor(Color.WHITE)
            }
        }

        // Botão Aspecto
        btnAspect.isFocusable = true
        btnAspect.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start()
                v.setBackgroundColor(Color.parseColor("#33FFFFFF"))
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                v.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    private fun setupServerList() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val savedDns = prefs.getString("dns", "") ?: ""
        
        activeServerList.clear()
        
        // 1. Prioridade: DNS do Login
        if (savedDns.isNotEmpty()) {
            var cleanDns = savedDns
            if (cleanDns.endsWith("/")) cleanDns = cleanDns.dropLast(1)
            activeServerList.add(cleanDns)
        }
        
        // 2. Backup: Adiciona os outros se não forem iguais ao do login
        for (server in serverBackupList) {
            var cleanServer = server
            if (cleanServer.endsWith("/")) cleanServer = cleanServer.dropLast(1)
            // Evita duplicata
            if (cleanServer != savedDns && !savedDns.contains(cleanServer)) {
                activeServerList.add(cleanServer)
            }
        }
    }

    private fun calcularProximoEpisodioAutomaticamente() {
        if (nextStreamId != 0) return 

        if (episodeList.isNotEmpty() && streamType == "series") {
            val indexAtual = episodeList.indexOf(streamId)
            if (indexAtual != -1 && indexAtual < episodeList.size - 1) {
                nextStreamId = episodeList[indexAtual + 1]
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    @OptIn(UnstableApi::class)
    private fun iniciarPlayer() {
        if (streamType == "vod_offline") {
            val uriStr = offlineUri
            if (uriStr.isNullOrBlank()) {
                Toast.makeText(this, "Arquivo offline inválido.", Toast.LENGTH_LONG).show()
                loading.visibility = View.GONE
                return
            }
            player?.release()
            player = ExoPlayer.Builder(this).build()
            playerView.player = player
            val mediaItem = MediaItem.fromUri(Uri.parse(uriStr))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> loading.visibility = View.GONE
                        Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Erro offline.", Toast.LENGTH_LONG).show()
                }
            })
            return
        }

        if (activeServerList.isEmpty()) {
            Toast.makeText(this, "Erro: Nenhum servidor configurado.", Toast.LENGTH_LONG).show()
            loading.visibility = View.GONE
            return
        }

        // Lógica de rotação de tentativas (Servidor -> Extensão)
        if (extIndex >= extensoesTentativa.size) {
            serverIndex++
            extIndex = 0
            if (serverIndex >= activeServerList.size) {
                // Fim das tentativas
                Toast.makeText(this, "Falha na reprodução. Servidor indisponível.", Toast.LENGTH_LONG).show()
                finish() // Fecha somente aqui, após tentar tudo
                return
            } else {
                Toast.makeText(this, "Tentando servidor ${serverIndex + 1}...", Toast.LENGTH_SHORT).show()
            }
        }

        val currentServer = activeServerList[serverIndex]
        val currentExt = extensoesTentativa[extIndex]

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        val url = montarUrlStream(
            server = currentServer,
            streamType = streamType,
            user = user,
            pass = pass,
            id = streamId,
            ext = currentExt
        )

        player?.release()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Buffer Otimizado para 4G/WiFi
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 30000, 1500, 3000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            
        playerView.player = player

        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player?.setMediaItem(mediaItem)
            player?.prepare()

            if (startPositionMs > 0L && (streamType == "movie" || streamType == "series")) {
                player?.seekTo(startPositionMs)
            }

            player?.playWhenReady = true
        } catch (e: Exception) {
            tentarProximo()
            return
        }

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> loading.visibility = View.GONE
                    Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                    Player.STATE_ENDED -> {
                        if (streamType == "movie") {
                            clearMovieResume(streamId)
                        } else if (streamType == "series") {
                            clearSeriesResume(streamId)
                            if (nextStreamId != 0) abrirProximoEpisodio()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loading.visibility = View.VISIBLE
                // Delay pequeno antes de tentar o próximo para não travar a UI
                handler.postDelayed({ tentarProximo() }, 500L)
            }
        })
    }

    private fun tentarProximo() {
        extIndex++
        iniciarPlayer()
    }

    private fun abrirProximoEpisodio() {
        if (nextStreamId == 0) return

        var novoTitulo = nextChannelName
        val tituloAtual = tvChannelName.text.toString()

        if (novoTitulo == null || novoTitulo.equals("Próximo Episódio", ignoreCase = true) || novoTitulo == tituloAtual) {
            val regex = Regex("(?i)(E|Episódio|Episodio|Episode)\\s*0*(\\d+)")
            val match = regex.find(tituloAtual)
            if (match != null) {
                try {
                    val textoCompleto = match.groupValues[0] 
                    val prefixo = match.groupValues[1] 
                    val numeroStr = match.groupValues[2] 
                    val novoNumero = numeroStr.toInt() + 1
                    val novoNumeroStr = if (numeroStr.length > 1 && novoNumero < 10) "0$novoNumero" else novoNumero.toString()
                    novoTitulo = tituloAtual.replace(textoCompleto, "$prefixo$novoNumeroStr")
                } catch (e: Exception) {
                    novoTitulo = tituloAtual 
                }
            } else {
                novoTitulo = tituloAtual 
            }
        }

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", nextStreamId)
        intent.putExtra("stream_ext", "mp4") // Força MP4 para séries
        intent.putExtra("stream_type", "series")
        intent.putExtra("channel_name", novoTitulo) 
        
        if (episodeList.isNotEmpty()) {
            intent.putIntegerArrayListExtra("episode_list", episodeList)
        }
        
        startActivity(intent)
        finish()
    }

    private fun getMovieKey(id: Int) = "movie_resume_$id"
    private fun saveMovieResume(id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val percent = positionMs.toDouble() / durationMs.toDouble()
        if (positionMs < 30_000L || percent > 0.95) {
            clearMovieResume(id)
            return
        }
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("${getMovieKey(id)}_pos", positionMs).putLong("${getMovieKey(id)}_dur", durationMs).apply()
    }
    private fun clearMovieResume(id: Int) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("${getMovieKey(id)}_pos").remove("${getMovieKey(id)}_dur").apply()
    }

    private fun getSeriesKey(episodeStreamId: Int) = "series_resume_$episodeStreamId"
    private fun saveSeriesResume(id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val percent = positionMs.toDouble() / durationMs.toDouble()
        if (positionMs < 30_000L || percent > 0.95) {
            clearSeriesResume(id)
            return
        }
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("${getSeriesKey(id)}_pos", positionMs).putLong("${getSeriesKey(id)}_dur", durationMs).apply()
    }
    private fun clearSeriesResume(id: Int) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("${getSeriesKey(id)}_pos").remove("${getSeriesKey(id)}_dur").apply()
    }

    private fun decodeBase64(text: String?): String {
        return try {
            if (text.isNullOrEmpty()) "" else String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) { text ?: "" }
    }

    private fun carregarEpg() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        if (user.isBlank() || pass.isBlank()) {
            tvNowPlaying.text = ""
            return
        }

        XtreamApi.service.getShortEpg(user, pass, streamId.toString(), 1)
            .enqueue(object : Callback<EpgWrapper> {
            override fun onResponse(call: Call<EpgWrapper>, response: Response<EpgWrapper>) {
                if (response.isSuccessful) {
                    val list = response.body()?.epg_listings
                    if (!list.isNullOrEmpty()) {
                        val epg = list[0]
                        val titulo = decodeBase64(epg.title)
                        val hora = if (epg.start != null) " (${epg.start} - ${epg.end})" else ""
                        tvNowPlaying.text = "$titulo$hora"
                    }
                }
            }
            override fun onFailure(call: Call<EpgWrapper>, t: Throwable) {}
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (nextEpisodeContainer.visibility == View.VISIBLE && 
           (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
             abrirProximoEpisodio()
             return true
        }

        if (playerView.isControllerFullyVisible) {
            return super.onKeyDown(keyCode, event)
        }

        val p = player ?: return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val newPos = (p.currentPosition - 10_000L).coerceAtLeast(0L)
                p.seekTo(newPos)
                Toast.makeText(this, "-10s", Toast.LENGTH_SHORT).show()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val newPos = (p.currentPosition + 10_000L).coerceAtLeast(0L)
                p.seekTo(newPos)
                Toast.makeText(this, "+10s", Toast.LENGTH_SHORT).show()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        val p = player ?: return
        p.playWhenReady = false 
        if (streamType == "movie") saveMovieResume(streamId, p.currentPosition, p.duration)
        else if (streamType == "series") saveSeriesResume(streamId, p.currentPosition, p.duration)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(nextChecker)
        val p = player
        if (p != null) {
            if (streamType == "movie") saveMovieResume(streamId, p.currentPosition, p.duration)
            else if (streamType == "series") saveSeriesResume(streamId, p.currentPosition, p.duration)
        }
        player?.release()
        player = null
    }

    private fun montarUrlStream(server: String, streamType: String, user: String, pass: String, id: Int, ext: String): String {
        val base = if (server.endsWith("/")) server.dropLast(1) else server
        return "$base/$streamType/$user/$pass/$id.$ext"
    }
}
