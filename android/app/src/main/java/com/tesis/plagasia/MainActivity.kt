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
        // Punto de entrada de Jetpack Compose
        setContent { MainScreen() }
    }
}

/**
 * Estructura visual principal de la pantalla.
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
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize()) {
        when (cameraPermissionState.status) {
            is PermissionStatus.Granted -> PestDetectionScreen()
            is PermissionStatus.Denied -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Se necesita permiso de cámara para detectar plagas")
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

    // --- VARIABLES DE ESTADO ---
    var detectionResult by remember { mutableStateOf("Apunte a una hoja...") }
    var sugerenciaTratamiento by remember { mutableStateOf("") } // NUEVO: Guarda el consejo

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGalleryImage by remember { mutableStateOf(false) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val databaseHelper = remember { DatabaseHelper(context) }

    val classifier = remember {
        try {
            TomateClassifier(context)
        } catch (e: Exception) {
            errorMessage = "Error cargando modelo IA: ${e.message}"
            null
        }
    }

    // --- NUEVO: Función para actualizar resultado y sugerencia a la vez ---
    fun actualizarDiagnostico(resultado: String) {
        detectionResult = resultado
        sugerenciaTratamiento = obtenerSugerencia(resultado)
    }

    fun saveDetection(resultText: String) {
        try {
            val lineas = resultText.split("\n")
            if (lineas.isNotEmpty() && !resultText.contains("Analizando") && !resultText.contains("Apunte")) {
                val nombre = lineas[0]
                val confianza = Regex("\\d+").find(resultText)?.value?.toInt() ?: 0
                val fechaActual = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                databaseHelper.insertDetection(nombre, confianza, fechaActual)
            }
        } catch (e: Exception) {
            Log.e("DB", "Error guardando detección", e)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                actualizarDiagnostico("Procesando imagen...")

                val originalBitmap = if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }

                val safeBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val resizedBitmap = Bitmap.createScaledBitmap(safeBitmap, 224, 224, true)

                selectedBitmap = safeBitmap
                showGalleryImage = true

                val result = classifier?.classify(resizedBitmap) ?: "Error IA"

                // Actualizamos resultado y consejo
                actualizarDiagnostico(result)

                if (!result.contains("Error") && !result.contains("Analizando")) {
                    saveDetection(result)
                }

            } catch (e: Exception) {
                actualizarDiagnostico("Error al procesar: ${e.message}")
                Log.e("Gallery", "Error procesando imagen", e)
            }
        }
    }

    // --- INTERFAZ GRÁFICA ---
    Box(modifier = Modifier.fillMaxSize()) {

        if (errorMessage != null) {
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
            if (showGalleryImage && selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Imagen analizada",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                val bitmapBuffer = Bitmap.createBitmap(
                                    imageProxy.width,
                                    imageProxy.height,
                                    Bitmap.Config.ARGB_8888
                                )

                                imageProxy.use {
                                    bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer)
                                }

                                val matrix = Matrix().apply {
                                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                                }

                                val rotatedBitmap = Bitmap.createBitmap(
                                    bitmapBuffer, 0, 0,
                                    bitmapBuffer.width, bitmapBuffer.height,
                                    matrix, true
                                )

                                val result = classifier?.classify(rotatedBitmap) ?: "Error IA"

                                previewView.post {
                                    // Actualizamos resultado y consejo en tiempo real
                                    actualizarDiagnostico(result)
                                }
                            }

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
                    .background(Color.Black.copy(alpha = 0.85f)) // Fondo un poco más oscuro
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 1. Texto del Diagnóstico
                Text(
                    text = detectionResult.replace("\n", " "),
                    color = if (detectionResult.contains("Sano")) Color.Green else Color(0xFFFFEB3B),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. NUEVO: Caja de Sugerencia de Tratamiento
                if (sugerenciaTratamiento.isNotEmpty() && !detectionResult.contains("Analizando") && !detectionResult.contains("Apunte")) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sugerenciaTratamiento,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botones de Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Espacio un poco más pequeño para que quepan
                ) {
                    // Botón 1: Galería
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📁 Galería")
                    }

                    // Botón 2: Cámara (Solo visible en modo galería)
                    if (showGalleryImage) {
                        Button(
                            onClick = {
                                showGalleryImage = false
                                selectedBitmap = null
                                actualizarDiagnostico("Apunte a una hoja...")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📷 Cámara")
                        }
                    }

                    // Botón 3: NUEVO BOTÓN COMPARTIR (Solo visible si hay diagnóstico)
                    if (sugerenciaTratamiento.isNotEmpty() && !detectionResult.contains("Analizando") && !detectionResult.contains("Apunte")) {
                        Button(
                            onClick = {
                                // Armamos el mensaje para WhatsApp/Correo
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    val textoMensaje = """
                                        🌱 *Diagnóstico PlagaIA*
                                        
                                        🚨 *Detección:* ${detectionResult.replace("\n", " ")}
                                        
                                        💡 *Sugerencia:* $sugerenciaTratamiento
                                    """.trimIndent()

                                    putExtra(Intent.EXTRA_TEXT, textoMensaje)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Compartir diagnóstico por...")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.weight(1f),
                            // Le damos un color verde estilo WhatsApp
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E))
                        ) {
                            Text("📤 Enviar")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diccionario de tratamientos según la plaga detectada.
 * Puedes personalizar estos textos para tu tesis.
 */
fun obtenerSugerencia(textoResultado: String): String {
    return when {
        textoResultado.contains("Sano") ->
            "✅ Estado óptimo. Continúa con el riego regular y monitoreo preventivo."

        textoResultado.contains("Tizón Temprano") ->
            "💊 Tratamiento: Aplica fungicidas (Clorotalonil o Cobre). Poda las hojas inferiores afectadas para mejorar la ventilación y evita mojar el follaje al regar."

        textoResultado.contains("Hoja Rizada") ->
            "🦟 Control: Enfermedad viral transmitida por mosca blanca. Usa mallas anti-insectos, trampas amarillas y elimina las plantas muy infectadas para evitar propagación."

        textoResultado.contains("Mancha Septoria") ->
            "🍂 Tratamiento: Elimina residuos de cultivos anteriores. Aplica fungicidas a base de cobre o Mancozeb a los primeros síntomas. Rota los cultivos."

        textoResultado.contains("Marchitez Verticillium") ->
            "⚠️ Cuidado: Hongo de suelo difícil de curar. Solariza el suelo antes de plantar, retira plantas muertas desde la raíz y usa variedades resistentes."

        else -> "" // Si está "Analizando..." o es un error, no muestra nada
    }
}