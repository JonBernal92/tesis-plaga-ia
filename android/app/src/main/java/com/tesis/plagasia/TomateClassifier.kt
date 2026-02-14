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
 */
class TomateClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: MutableList<String> = mutableListOf()

    private val INPUT_SIZE = 224
    private val MODEL_FILE = "model_unquant.tflite"
    private val LABEL_FILE = "labels.txt"

    init {
        try {
            val model = loadModelFile(MODEL_FILE)
            interpreter = Interpreter(model)
            loadLabels(LABEL_FILE)
            Log.d("PlagaIA", "Modelo cargado. Etiquetas: $labels")
        } catch (e: Exception) {
            Log.e("PlagaIA", "Error inicializando IA", e)
        }
    }

    private fun loadLabels(filename: String) {
        try {
            val scanner = Scanner(context.assets.open(filename))
            while (scanner.hasNextLine()) {
                val line = scanner.nextLine()
                val cleanLabel = line.replace(Regex("^\\d+\\s+"), "").trim()
                if (cleanLabel.isNotEmpty()) {
                    labels.add(cleanLabel)
                }
            }
            scanner.close()
        } catch (e: IOException) {
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

    // --- NUEVO: FILTRO BOTÁNICO ---
    // Verifica si la imagen tiene colores de planta antes de molestar a la IA
    private fun esUnaPlanta(bitmap: Bitmap): Boolean {
        val intValues = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixelesVerdes = 0
        val totalPixeles = intValues.size

        for (pixel in intValues) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Condición: El canal Verde (G) debe ser mayor al Rojo (R) y al Azul (B)
            // Agregamos un margen (+15) para evitar detectar blancos o grises como verde
            if (g > r + 15 && g > b + 15) {
                pixelesVerdes++
            }
        }

        // Calculamos qué porcentaje de la foto es verde
        val porcentajeVerde = pixelesVerdes.toFloat() / totalPixeles

        // Si al menos el 3% de la imagen es verde, asumimos que hay una planta
        return porcentajeVerde > 0.03f
    }

    fun classify(bitmap: Bitmap): String {
        if (interpreter == null) return "Error: IA no iniciada"

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // 1. REVISAMOS SI HAY UNA PLANTA
        if (!esUnaPlanta(scaledBitmap)) {
            return "Apunte a una planta..."
        }

        // 2. SI ES PLANTA, CONTINUAMOS CON LA IA
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val output = Array(1) { FloatArray(labels.size) }

        interpreter?.run(byteBuffer, output)

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        if (maxIndex != -1 && probabilities[maxIndex] > 0.85f) {
            val confidence = (probabilities[maxIndex] * 100).toInt()
            val labelName = if (maxIndex < labels.size) labels[maxIndex] else "Desconocido"
            return "$labelName\n($confidence%)"
        } else {
            return "Analizando..."
        }
    }

    fun classifyWithAllResults(bitmap: Bitmap): String {
        if (interpreter == null) return "Error: IA no iniciada"

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // Filtro botánico también para la galería
        if (!esUnaPlanta(scaledBitmap)) {
            return "Imagen no válida\n(0%)"
        }

        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val output = Array(1) { FloatArray(labels.size) }

        interpreter?.run(byteBuffer, output)

        val probabilities = output[0]

        val sortedResults = probabilities.indices
            .map { index -> index to probabilities[index] }
            .sortedByDescending { it.second }

        val topResult = sortedResults[0]
        val confidence = (topResult.second * 100).toInt()

        if (topResult.second > 0.85f) {
            val label = if (topResult.first < labels.size) labels[topResult.first] else "Desconocido"
            return "$label\n($confidence%)"
        } else {
            return "Plaga no reconocida\n($confidence%)"
        }
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