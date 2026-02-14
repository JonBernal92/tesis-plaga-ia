package com.tesis.plagasia

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistorialActivity : AppCompatActivity() {

    private lateinit var adapter: HistorialAdapter
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        dbHelper = DatabaseHelper(this)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerHistorial)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Cargar datos iniciales
        val lista = dbHelper.obtenerHistorial()
        adapter = HistorialAdapter(lista)
        recyclerView.adapter = adapter

        // NUEVO: Configurar el botón de borrar
        val btnBorrar = findViewById<Button>(R.id.btnBorrarHistorial)
        btnBorrar?.setOnClickListener {
            // Borrar de la base de datos
            dbHelper.borrarHistorial()

            // Actualizar la pantalla (Lista vacía)
            adapter.actualizarDatos(emptyList())

            Toast.makeText(this, "Historial eliminado", Toast.LENGTH_SHORT).show()
        }
    }
}