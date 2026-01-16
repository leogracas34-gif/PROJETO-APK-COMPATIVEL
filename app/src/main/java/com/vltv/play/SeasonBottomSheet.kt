package com.vltv.play

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SeasonBottomSheet(
    private val seasons: List<String>,
    private val onSeasonSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_season_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Remove o fundo branco padrão para garantir a transparência
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val rv = view.findViewById<RecyclerView>(R.id.rvSeasons)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = SeasonAdapter(seasons) { selected ->
            onSeasonSelected(selected)
            dismiss() // Fecha o menu ao clicar
        }
    }

    // Adaptador Interno simples para a lista
    inner class SeasonAdapter(
        private val list: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SeasonAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tv: TextView = v.findViewById(R.id.tvSeasonName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_season, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val season = list[position]
            holder.tv.text = "Temporada $season"
            
            holder.itemView.setOnClickListener { onClick(season) }
            
            // Foco para TV Box (Amarelo ao focar)
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                    holder.tv.setTextColor(Color.parseColor("#FFD700"))
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT)
                    holder.tv.setTextColor(Color.WHITE)
                }
            }
        }

        override fun getItemCount() = list.size
    }
}
