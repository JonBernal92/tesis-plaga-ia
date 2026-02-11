package com.tesis.plagasia

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraScreen()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize()) {
        when (cameraPermissionState.status) {
            is PermissionStatus.Granted -> {
                PestDetectionScreen()
            }
            is PermissionStatus.Denied -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Se necesitan permisos de cámara para detectar plagas.", modifier = Modifier.padding(16.dp))
                    LaunchedEffect(Unit) {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            }
        }
    }
}

@Composable
fun PestDetectionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var detectionResult by remember { mutableStateOf("Inicializando IA...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Inicializamos el clasificador
    // Si el nombre del archivo está mal o el modelo es incompatible, saltará al 'catch'
    val classifier = remember {
        try {
            TomateClassifier(context)
        } catch (e: Exception) {
            errorMessage = "Error cargando modelo: ${e.message}"
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (errorMessage != null) {
            // PANTALLA ROJA DE ERROR (Solo sale si falla la carga)
            Text(
                text = "ERROR FATAL:\n$errorMessage",
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        } else {
            // PANTALLA DE CÁMARA (Correcto)
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            // 1. Vista previa (Preview)
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            // 2. Analizador de imágenes (ImageAnalysis)
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                try {
                                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                                    // Convertir ImageProxy a Bitmap
                                    val bitmapBuffer = Bitmap.createBitmap(
                                        imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
                                    )
                                    imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

                                    // Rotar el bitmap para que coincida con la orientación del modelo
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    val rotatedBitmap = Bitmap.createBitmap(
                                        bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
                                    )

                                    // CLASIFICAR
                                    val result = classifier?.classify(rotatedBitmap) ?: "Error IA"

                                    // Actualizar texto en pantalla
                                    previewView.post { detectionResult = result }

                                } catch (e: Exception) {
                                    Log.e("Camera", "Error analizando imagen", e)
                                }
                            }

                            // 3. Vincular todo al ciclo de vida
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )

                        } catch (e: Exception) {
                            Log.e("Camera", "Error iniciando cámara", e)
                        }
                    }, executor)
                    previewView
                }
            )

            // CAJA DE TEXTO CON EL RESULTADO
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(24.dp)
            ) {
                Text(
                    text = detectionResult,
                    color = if (detectionResult.contains("Sano")) Color.Green else Color.Yellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}