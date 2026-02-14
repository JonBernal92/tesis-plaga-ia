package com.tesis.plagasia

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "plagas.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE historial (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plaga TEXT,
                confianza INTEGER,
                fecha TEXT
            )
        """.trimIndent()

        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS historial")
        onCreate(db)
    }

    fun insertDetection(plaga: String, confianza: Int, fecha: String) {
        val db = writableDatabase
        val values = ContentValues()

        values.put("plaga", plaga)
        values.put("confianza", confianza)
        values.put("fecha", fecha)

        db.insert("historial", null, values)
        db.close()
    }

    fun obtenerHistorial(): List<Historial> {
        val lista = mutableListOf<Historial>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM historial ORDER BY id DESC", null)

        if (cursor.moveToFirst()) {
            do {
                val historial = Historial(
                    id = cursor.getInt(0),
                    plaga = cursor.getString(1),
                    confianza = cursor.getInt(2),
                    fecha = cursor.getString(3)
                )
                lista.add(historial)
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()
        return lista
    }

    // --- Función para vaciar la base de datos ---
    fun borrarHistorial() {
        val db = writableDatabase
        db.execSQL("DELETE FROM historial")
        db.close()
    }
}