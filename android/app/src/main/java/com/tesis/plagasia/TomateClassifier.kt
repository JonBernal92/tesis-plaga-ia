package com.tesis.plagasia

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Clasificador de enfermedades en plantas de tomate mediante Deep Learning.
 *
 * Esta clase implementa un sistema de inferencia basado en TensorFlow Lite
 * para la detección automática de cinco categorías de estados fitosanitarios
 * en cultivos de tomate mediante análisis de imágenes en tiempo real.
 *
 * Arquitectura: MobileNetV2 (optimizada para dispositivos móviles)
 * Entrada: Imágenes RGB de 224x224 píxeles
 * Salida: Vector de probabilidades para 5 clases
 *
 * @property context Contexto de la aplicación Android para acceso a assets
 */
class TomateClassifier(private val context: Context) {

    // Intérprete del modelo TensorFlow Lite (motor de inferencia)
    private var interpreter: Interpreter? = null

    // Dimensiones de entrada requeridas por la arquitectura MobileNetV2
    // El modelo fue entrenado con imágenes cuadradas de 224x224 píxeles
    private val INPUT_SIZE = 224

    /**
     * Catálogo de clases de salida del modelo.
     *
     * El orden de estas etiquetas debe coincidir exactamente con el orden
     * utilizado durante el entrenamiento del modelo (índice 0 = primera clase, etc.)
     *
     * Clases reconocidas:
     * - Índice 0: Planta sana (sin patologías)
     * - Índice 1: Tizón temprano (Alternaria solani)
     * - Índice 2: Hoja rizada (Virus del enrollamiento)
     * - Índice 3: Mancha septoria (Septoria lycopersici)
     * - Índice 4: Marchitez por Verticillium (Verticillium spp.)
     */
    private val labels = listOf(
        "Sano",
        "Tizón Temprano",
        "Hoja Rizada",
        "Mancha Septoria",
        "Marchitez Verticillium"
    )

    init {
        // Inicialización del modelo durante la construcción del objeto
        // El modelo se carga desde la carpeta 'assets' de la aplicación
        val model = loadModelFile("tomate_final_compatible.tflite")
        interpreter = Interpreter(model)
    }

    /**
     * Carga el archivo del modelo TensorFlow Lite desde assets.
     *
     * Utiliza mapeo de memoria (memory mapping) para acceso eficiente
     * sin cargar todo el archivo en RAM, optimizando el uso de recursos.
     *
     * @param modelName Nombre del archivo .tflite en la carpeta assets
     * @return ByteBuffer con el modelo mapeado en memoria
     * @throws IOException si el archivo no existe o no se puede leer
     */
    private fun loadModelFile(modelName: String): ByteBuffer {
        // Obtener descriptor del archivo desde assets
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelName)

        // Crear flujo de entrada desde el descriptor
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel

        // Parámetros de ubicación del archivo dentro del APK
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        // Mapear archivo directamente en memoria (eficiente para archivos grandes)
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Clasifica una imagen de planta de tomate.
     *
     * Proceso completo de inferencia:
     * 1. Redimensionamiento de imagen a 224x224
     * 2. Normalización de píxeles al rango esperado por el modelo
     * 3. Ejecución de la red neuronal
     * 4. Interpretación de probabilidades de salida
     *
     * @param bitmap Imagen capturada por la cámara del dispositivo
     * @return String con la clase predicha y porcentaje de confianza
     */
    fun classify(bitmap: Bitmap): String {
        // Validación de disponibilidad del intérprete
        if (interpreter == null) return "Error: Modelo no cargado"

        // PASO 1: PREPROCESAMIENTO DE IMAGEN
        // Redimensionamiento bilineal a las dimensiones requeridas (224x224)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // PASO 2: CONVERSIÓN A FORMATO TENSOR
        // Transformación de Bitmap Android a ByteBuffer para TensorFlow Lite
        // Formato esperado: [1, 224, 224, 3] con valores float32 normalizados
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)

        // PASO 3: PREPARACIÓN DEL CONTENEDOR DE SALIDA
        // Array bidimensional: [1 batch][5 clases]
        // Cada posición almacenará la probabilidad (0.0 a 1.0) de cada clase
        val output = Array(1) { FloatArray(labels.size) }

        // PASO 4: EJECUCIÓN DE INFERENCIA
        // El intérprete ejecuta el modelo neuronal con la imagen preprocesada
        interpreter?.run(byteBuffer, output)

        // PASO 5: POST-PROCESAMIENTO DE RESULTADOS
        val probabilities = output[0]

        // Búsqueda de la clase con mayor probabilidad
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        // Umbral de confianza: solo se reporta si la probabilidad supera 50%
        // Esto reduce falsos positivos en condiciones de iluminación deficiente
        if (maxIndex != -1 && probabilities[maxIndex] > 0.5f) {
            val confidence = (probabilities[maxIndex] * 100).toInt()
            return "${labels[maxIndex]}\n($confidence%)"
        } else {
            // Confianza insuficiente: se requiere mejor encuadre o iluminación
            return "Analizando..."
        }
    }

    /**
     * Convierte un Bitmap de Android a ByteBuffer para TensorFlow Lite.
     *
     * Realiza normalización de píxeles según el preprocesamiento de MobileNetV2:
     * - Rango original: [0, 255] (valores RGB de 8 bits)
     * - Rango normalizado: [-1.0, 1.0] (esperado por el modelo)
     *
     * Fórmula de normalización: (pixel - 127.5) / 127.5
     *
     * Orden de canales: RGB (Rojo, Verde, Azul)
     *
     * @param bitmap Imagen redimensionada a 224x224
     * @return ByteBuffer con tensor de entrada de 4 dimensiones
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Cálculo de tamaño del buffer:
        // 4 bytes (float32) × 224 píxeles × 224 píxeles × 3 canales RGB
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)

        // Configuración del orden de bytes según la arquitectura del dispositivo
        // (Little-endian en ARM, que es el estándar en Android)
        byteBuffer.order(ByteOrder.nativeOrder())

        // Extracción de valores de píxeles del bitmap
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Iteración por cada píxel de la imagen
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]

                // EXTRACCIÓN Y NORMALIZACIÓN DE CANALES RGB
                // Android almacena colores en formato ARGB (32 bits)
                // Se extraen los 8 bits de cada canal mediante operaciones bit a bit

                // Canal Rojo: bits 16-23
                byteBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f)

                // Canal Verde: bits 8-15
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)

                // Canal Azul: bits 0-7
                byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)
            }
        }

        return byteBuffer
    }

    /**
     * Libera los recursos del intérprete TensorFlow Lite.
     *
     * Debe invocarse cuando el clasificador ya no sea necesario
     * para evitar fugas de memoria nativa (memoria no gestionada por el GC de Java).
     */
    fun close() {
        interpreter?.close()
    }
}