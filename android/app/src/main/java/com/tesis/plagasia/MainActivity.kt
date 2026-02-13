package com.tesis.plagasia

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Actividad principal de la aplicación PlagaIA.
 * Se encarga de gestionar la interfaz de usuario, los permisos de cámara,
 * la visualización en tiempo real y la selección de imágenes desde la galería.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Punto de entrada de Jetpack Compose (la nueva forma de hacer UI en Android)
        setContent { MainScreen() }
    }
}

/**
 * Estructura visual principal de la pantalla.
 * Contiene la barra superior (Header) y el contenedor de la cámara.
 */
@Composable
fun MainScreen() {

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {

        // --- BARRA SUPERIOR (HEADER) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2E7D32)) // Verde oscuro (estilo agrícola)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Plagas IA",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                // Botón para navegar a la actividad de Historial (Base de Datos)
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, HistorialActivity::class.java)
                        )
                    }
                ) {
                    Text("📜 Historial")
                }
            }
        }

        // --- CONTENEDOR DE LA CÁMARA ---
        // Ocupa todo el espacio restante de la pantalla
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CameraScreen()
        }
    }
}

/**
 * Pantalla intermedia que gestiona los permisos de la cámara.
 * Si el permiso no está concedido, lo solicita. Si ya lo tiene, muestra la detección.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {

    // Estado del permiso de cámara (librería Accompanist)
    val cameraPermissionState =
        rememberPermissionState(permission = Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize()) {
        when (cameraPermissionState.status) {

            // CASO 1: Permiso concedido -> Mostramos la pantalla de IA
            is PermissionStatus.Granted -> PestDetectionScreen()

            // CASO 2: Permiso denegado -> Mostramos botón para solicitarlo
            is PermissionStatus.Denied -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Se necesita permiso de cámara para detectar plagas")

                    // Lanza la solicitud de permiso automáticamente al iniciar
                    LaunchedEffect(Unit) {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            }
        }
    }
}

/**
 * Núcleo de la aplicación: Lógica de IA, Cámara y Galería.
 */
@Composable
fun PestDetectionScreen() {

    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // --- VARIABLES DE ESTADO (La memoria de la pantalla) ---
    var detectionResult by remember { mutableStateOf("Apunte a una hoja...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGalleryImage by remember { mutableStateOf(false) } // ¿Estamos viendo una foto de galería?
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) } // La imagen seleccionada

    // Instancia de la base de datos para guardar resultados
    val databaseHelper = remember { DatabaseHelper(context) }

    // Inicialización del clasificador (Modelo TensorFlow Lite)
    // Se usa 'remember' para no recargarlo cada vez que la pantalla parpadea
    val classifier = remember {
        try {
            TomateClassifier(context)
        } catch (e: Exception) {
            errorMessage = "Error cargando modelo IA: ${e.message}"
            null
        }
    }

    /**
     * Función auxiliar para guardar el diagnóstico en SQLite.
     * Parsea el texto del resultado para extraer nombre y porcentaje.
     */
    fun saveDetection(resultText: String) {
        try {
            // El resultado viene como "NombrePlaga \n (90%)"
            val lineas = resultText.split("\n")

            // Verificamos que haya texto válido antes de guardar
            if (lineas.isNotEmpty() && !resultText.contains("Analizando")) {
                val nombre = lineas[0]

                // Extraemos solo los números del texto (ej: "(98%)" -> 98)
                val confianza = Regex("\\d+")
                    .find(resultText)
                    ?.value
                    ?.toInt() ?: 0

                val fechaActual = SimpleDateFormat(
                    "dd/MM/yyyy HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                // Insertamos en la base de datos
                databaseHelper.insertDetection(nombre, confianza, fechaActual)
            }

        } catch (e: Exception) {
            Log.e("DB", "Error guardando detección", e)
        }
    }

    /**
     * Lanzador para abrir la galería del teléfono.
     */
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->

        uri?.let {
            try {
                detectionResult = "Procesando imagen..."

                // 1. CARGA DE IMAGEN SEGURA
                // Android moderno usa bitmaps de Hardware que TensorFlow NO puede leer.
                // Aquí forzamos una configuración compatible (ARGB_8888).
                val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true // Importante para poder editarla si es necesario
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }

                // 2. CORRECCIÓN CRÍTICA DE FORMATO
                // Hacemos una copia explícita en formato estándar de píxeles.
                // Sin esto, la app se cierra en muchos teléfonos nuevos.
                val safeBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

                // 3. REDIMENSIONAR
                // El modelo espera exactamente 224x224 píxeles.
                val resizedBitmap = Bitmap.createScaledBitmap(
                    safeBitmap,
                    224,
                    224,
                    true
                )

                selectedBitmap = safeBitmap
                showGalleryImage = true

                // 4. CLASIFICACIÓN
                // Usamos 'classify' (igual que la cámara) para obtener un mensaje simple
                // sin emojis ni listas largas.
                val result = classifier?.classify(resizedBitmap) ?: "Error IA"

                detectionResult = result

                // Guardamos automáticamente si es un resultado válido
                if (!result.contains("Error") && !result.contains("Analizando")) {
                    saveDetection(result)
                }

            } catch (e: Exception) {
                detectionResult = "Error al procesar: ${e.message}"
                Log.e("Gallery", "Error procesando imagen", e)
            }
        }
    }

    // --- INTERFAZ GRÁFICA ---
    Box(modifier = Modifier.fillMaxSize()) {

        if (errorMessage != null) {
            // Muestra mensaje rojo si falla la carga del modelo .tflite
            Text(
                text = "ERROR CRÍTICO:\n$errorMessage",
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )

        } else {

            // DECISIÓN: ¿Mostramos foto de galería o vista previa de cámara?
            if (showGalleryImage && selectedBitmap != null) {

                // MODO GALERÍA: Muestra la foto estática seleccionada
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Imagen analizada",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

            } else {

                // MODO CÁMARA: Vista previa en tiempo real usando CameraX
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->

                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({

                            val cameraProvider = cameraProviderFuture.get()

                            // Configuración de la vista previa (lo que ve el usuario)
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            // Configuración del analizador de imágenes (lo que ve la IA)
                            val imageAnalysis = ImageAnalysis.Builder()
                                // Solo analiza la última imagen disponible para no saturar la memoria
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                // Formato RGBA compatible con la mayoría de operaciones de bitmap
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->

                                // Conversión de ImageProxy (Cámara) a Bitmap (Android)
                                val bitmapBuffer = Bitmap.createBitmap(
                                    imageProxy.width,
                                    imageProxy.height,
                                    Bitmap.Config.ARGB_8888
                                )

                                // Copia los píxeles del buffer de cámara al bitmap
                                imageProxy.use {
                                    bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer)
                                }

                                // Rotación de la imagen:
                                // La cámara suele capturar en horizontal, necesitamos rotarla
                                // para que coincida con la orientación del teléfono.
                                val matrix = Matrix().apply {
                                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                }

                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmapBuffer, 0, 0,
                                    bitmapBuffer.width, bitmapBuffer.height,
                                    matrix, true
                                )

                                // INFERENCIA: La IA analiza la imagen rotada
                                val result = classifier?.classify(rotatedBitmap) ?: "Error IA"

                                // Actualizamos la UI en el hilo principal
                                previewView.post {
                                    detectionResult = result
                                    // Nota: No guardamos automáticamente en modo cámara para
                                    // no llenar la base de datos con 30 detecciones por segundo.
                                }
                            }

                            // Vinculamos todo al ciclo de vida de la actividad
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                Log.e("CameraX", "Fallo al vincular cámara", exc)
                            }

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )
            }

            // --- PANEL INFERIOR DE RESULTADOS ---
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f)) // Fondo semitransparente
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Texto del Diagnóstico
                Text(
                    text = detectionResult.replace("\n", " "), // Mostramos en una sola línea
                    color = if (detectionResult.contains("Sano"))
                        Color.Green else Color(0xFFFFEB3B), // Verde si es sano, Amarillo si es plaga
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botones de Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // Botón abrir Galería
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📁 Abrir Galería")
                    }

                    // Botón volver a Cámara (solo visible si estamos en modo galería)
                    if (showGalleryImage) {
                        Button(
                            onClick = {
                                showGalleryImage = false
                                selectedBitmap = null
                                detectionResult = "Apunte a una hoja..."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📷 Usar Cámara")
                        }
                    }
                }
            }
        }
    }
}