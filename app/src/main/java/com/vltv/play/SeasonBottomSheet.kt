package com.vltv.play

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Importante: R do seu pacote
import com.vltv.play.R 

class SeasonBottomSheet(
    private val seasons: List<String>,
    private val onSeasonSelected: (String) -> Unit
) : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Usa um tema translúcido para garantir que não haja bordas ou títulos
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla o nosso layout que tem o fundo escurecido transparente
        val view = inflater.inflate(R.layout.layout_season_sheet, container, false)
        
        // Remove qualquer fundo padrão da janela do Dialog
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        
        return view
    }

    // --- CORREÇÃO PRINCIPAL AQUI ---
    override fun onStart() {
        super.onStart()
        // Força a janela a ocupar a tela inteira e ser transparente
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            // Remove o "Dim" padrão do Android para usar apenas o nosso do XML
            val params = window.attributes
            params.dimAmount = 0f
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
        
        // Fecha se clicar na parte escura (fundo)
        view.setOnClickListener { dismiss() }
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

            // Lógica de Foco Dourado (Estilo Disney)
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    holder.card.setCardBackgroundColor(Color.parseColor("#FFD700")) // Amarelo
                    holder.tv.setTextColor(Color.BLACK) // Texto Preto
                    holder.card.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                    holder.card.cardElevation = 10f
                } else {
                    holder.card.setCardBackgroundColor(Color.parseColor("#333333")) // Cinza do Botão
                    holder.tv.setTextColor(Color.WHITE) // Texto Branco
                    holder.card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    holder.card.cardElevation = 4f
                }
            }
        }

        override fun getItemCount() = list.size
    }
}
