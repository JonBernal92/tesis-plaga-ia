package com.tesis.plagasia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistorialAdapter(
    private val lista: List<Historial>
) : RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val plaga: TextView = view.findViewById(R.id.txtPlaga)
        val confianza: TextView = view.findViewById(R.id.txtConfianza)
        val fecha: TextView = view.findViewById(R.id.txtFecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_historial, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.plaga.text = item.plaga
        holder.confianza.text = "Confianza: ${item.confianza}%"
        holder.fecha.text = item.fecha
    }

    override fun getItemCount(): Int = lista.size
}
