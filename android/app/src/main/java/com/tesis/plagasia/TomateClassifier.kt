package com.tesis.plagasia

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Scanner

/**
 * Clasificador inteligente para modelos de Teachable Machine.
 * * Funcionalidad Clave:
 * 1. Carga dinámica de etiquetas desde 'labels.txt'.
 * 2. Limpieza automática de prefijos numéricos (ej: "0 Sano" -> "Sano").
 * 3. Ejecución del modelo 'model_unquant.tflite'.
 */
class TomateClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: MutableList<String> = mutableListOf()

    // Configuración estándar de Teachable Machine
    private val INPUT_SIZE = 224
    private val MODEL_FILE = "model_unquant.tflite"
    private val LABEL_FILE = "labels.txt"

    init {
        try {
            val model = loadModelFile(MODEL_FILE)
            interpreter = Interpreter(model)
            loadLabels(LABEL_FILE)
            Log.d("PlagaIA", "Modelo cargado correctamente. Etiquetas: $labels")
        } catch (e: Exception) {
            Log.e("PlagaIA", "Error crítico inicializando IA", e)
            throw RuntimeException("No se pudo cargar el modelo o las etiquetas")
        }
    }

    /**
     * Carga y limpia las etiquetas del archivo de texto.
     * Convierte "0 Sano" en "Sano" para que se vea bien en pantalla.
     */
    private fun loadLabels(filename: String) {
        try {
            val scanner = Scanner(context.assets.open(filename))
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                // Usamos Regex para quitar los números del principio si existen
                // Ej: "0 Sano" se convierte en "Sano"
                val cleanLabel = line.replace(Regex("^\\d+\\s+"), "").trim()
                if (cleanLabel.isNotEmpty()) {
                    labels.add(cleanLabel)
                }
            }
            scanner.close()
        } catch (e: IOException) {
            Log.e("PlagaIA", "No se encontró labels.txt", e)
            // Etiquetas de emergencia por si falla la lectura
            labels.addAll(listOf("Clase 1", "Clase 2", "Clase 3", "Clase 4", "Clase 5"))
        }
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Clasifica la imagen y devuelve el resultado formateado.
     * Retorna: "NombrePlaga \n (Porcentaje%)"
     */
    fun classify(bitmap: Bitmap): String {
        if (interpreter == null) return "Error: IA no iniciada"

        // 1. Preprocesar imagen
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

        // 2. Preparar salida
        // El tamaño del array de salida debe coincidir con el número de etiquetas
        val output = Array(1) { FloatArray(labels.size) }

        // 3. Ejecutar inferencia
        interpreter?.run(byteBuffer, output)

        // 4. Interpretar resultados
        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        // Umbral de confianza (50%)
        if (maxIndex != -1 && probabilities[maxIndex] > 0.5f) {
            val confidence = (probabilities[maxIndex] * 100).toInt()
            // Obtenemos el nombre limpio de la lista cargada
            val labelName = if (maxIndex < labels.size) labels[maxIndex] else "Desconocido"

            return "$labelName\n($confidence%)"
        } else {
            return "Analizando..."
        }
    }

    /**
     * Método para galería: Muestra todas las probabilidades ordenadas.
     */
    fun classifyWithAllResults(bitmap: Bitmap): String {
        if (interpreter == null) return "Error: IA no iniciada"

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val output = Array(1) { FloatArray(labels.size) }

        interpreter?.run(byteBuffer, output)

        val probabilities = output[0]

        // Crear lista de pares (Indice, Probabilidad) y ordenar
        val sortedResults = probabilities.indices
            .map { index -> index to probabilities[index] }
            .sortedByDescending { it.second }

        // Solo retornamos el mejor resultado en formato simple para que MainActivity lo pueda guardar
        // Si quieres ver todo el detalle, cambia esta lógica, pero para guardar en DB es mejor uno solo.
        val topResult = sortedResults[0]
        val label = if (topResult.first < labels.size) labels[topResult.first] else "Desconocido"
        val confidence = (topResult.second * 100).toInt()

        return "$label\n($confidence%)"
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]

                // Normalización estándar de Teachable Machine [-1 a 1]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return byteBuffer
    }

    fun close() {
        interpreter?.close()
    }
}