package com.tesis.plagasia

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

class TomateClassifier(private val context: Context) {

    private val interpreter: Interpreter
    private val imageProcessor: ImageProcessor

    private val labels = listOf(
        "healthy",
        "leaf blight",
        "leaf curl",
        "septoria leaf spot",
        "verticulium wilt"
    )

    init {
        // Cargar modelo TFLite desde assets
        interpreter = Interpreter(loadModelFile("modelo_tomate_plagas.tflite"))

        // Preprocesamiento de imagen (resize únicamente)
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
            .build()
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }

    fun classify(bitmap: Bitmap): String {
        // Convertir Bitmap a TensorImage
        val tensorImage = TensorImage.fromBitmap(bitmap)

        // Aplicar preprocesamiento
        val processedImage = imageProcessor.process(tensorImage)

        // Salida del modelo
        val output = Array(1) { FloatArray(labels.size) }

        // Ejecutar inferencia
        interpreter.run(processedImage.buffer, output)

        // Obtener índice con mayor probabilidad
        val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1

        return labels[maxIndex]
    }
}
