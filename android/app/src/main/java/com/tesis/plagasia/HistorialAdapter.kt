package com.tesis.plagasia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistorialAdapter(
    private var lista: List<Historial>
) : RecyclerView.Adapter<HistorialAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val plaga: TextView = view.findViewById(R.id.txtPlaga)
        val confianza: TextView = view.findViewById(R.id.txtConfianza)
        val fecha: TextView = view.findViewById(R.id.txtFecha)
        // NUEVO: El texto para la sugerencia (lo haremos opcional por si aún no has tocado el XML)
        val sugerencia: TextView? = view.findViewById(R.id.txtSugerencia)
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

        // NUEVO: Llamamos a la función global de MainActivity para obtener el consejo
        val consejo = obtenerSugerencia(item.plaga)
        if (holder.sugerencia != null) {
            if (consejo.isNotEmpty() && !item.plaga.contains("Sano")) {
                holder.sugerencia.text = consejo
                holder.sugerencia.visibility = View.VISIBLE
            } else {
                holder.sugerencia.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = lista.size

    //  Función para actualizar la lista cuando borremos los datos
    fun actualizarDatos(nuevaLista: List<Historial>) {
        this.lista = nuevaLista
        notifyDataSetChanged()
    }
}