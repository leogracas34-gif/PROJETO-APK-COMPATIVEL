package com.vltv.play

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vltv.play.R 

class SeasonBottomSheet(
    private val seasons: List<String>,
    private val onSeasonSelected: (String) -> Unit
) : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema transparente para não criar molduras cinzas
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_season_sheet, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val params = window.attributes
            params.dimAmount = 0f // Remove o escurecimento padrão do sistema
            window.attributes = params
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvSeasons)
        rv?.layoutManager = LinearLayoutManager(context)
        rv?.adapter = SeasonAdapter(seasons) { selected ->
            onSeasonSelected(selected)
            dismiss()
        }
        
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)
        btnClose?.setOnClickListener { dismiss() }
        
        btnClose?.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
    }

    inner class SeasonAdapter(
        private val list: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SeasonAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvSeasonName)
            val card: CardView = v as CardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_season, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val season = list[position]
            holder.tv.text = "Temporada $season"
            
            holder.itemView.setOnClickListener { onClick(season) }

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Estilo Disney: Fundo branco e Texto preto no foco
                    holder.card.setCardBackgroundColor(Color.WHITE) 
                    holder.tv.setTextColor(Color.BLACK)
                    holder.card.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
                } else {
                    // Estilo Disney: Transparente e Texto branco quando fora de foco
                    holder.card.setCardBackgroundColor(Color.TRANSPARENT)
                    holder.tv.setTextColor(Color.WHITE)
                    holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
        }

        override fun getItemCount() = list.size
    }
}
