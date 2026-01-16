package com.vltv.play

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class SearchResultAdapter(
    private val onClick: (SearchResultItem) -> Unit
) : ListAdapter<SearchResultItem, SearchResultAdapter.VH>(SearchDiffCallback()) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvName.text = item.title

        // 1. GLIDE OTIMIZADO
        Glide.with(holder.itemView.context)
            .load(item.iconUrl)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .override(200, 300) // Imagem leve
            .centerCrop()
            .into(holder.imgPoster)

        // 2. CORREÇÃO DE FOCO COMPLETA (Zoom + Texto + Borda)
        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true
        
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Zoom
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                
                // --- APLICA A BORDA AMARELA ---
                try {
                    view.setBackgroundResource(R.drawable.focus_border)
                } catch (e: Exception) {
                    // Fallback se não tiver o XML: Borda sólida via código
                    view.setBackgroundColor(Color.parseColor("#FFD700"))
                    view.setPadding(4,4,4,4)
                }
                
                holder.tvName.setTextColor(Color.parseColor("#FFD700"))
                holder.tvName.setBackgroundColor(Color.parseColor("#CC000000"))
                view.elevation = 10f
            } else {
                // Volta ao normal
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                
                view.setBackgroundResource(0) // Remove borda
                view.setPadding(0,0,0,0)
                
                holder.tvName.setTextColor(Color.WHITE)
                holder.tvName.setBackgroundColor(Color.TRANSPARENT)
                view.elevation = 0f
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }
}

class SearchDiffCallback : DiffUtil.ItemCallback<SearchResultItem>() {
    override fun areItemsTheSame(old: SearchResultItem, new: SearchResultItem) = 
        old.id == new.id && old.type == new.type
        
    override fun areContentsTheSame(old: SearchResultItem, new: SearchResultItem) = 
        old == new
}
