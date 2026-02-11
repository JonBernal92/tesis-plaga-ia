package com.tesis.plagasia

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
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
        setContent { MainScreen() }
    }
}

/* ─────────────────────────────
   PANTALLA PRINCIPAL
   ───────────────────────────── */
@Composable
fun MainScreen() {
    Column(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2E7D32))
                .padding(16.dp)
        ) {
            Text(
                text = "Plagas IA",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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
            is PermissionStatus.Granted -> PestDetectionScreen()
            is PermissionStatus.Denied -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Se necesita permiso de cámara")
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

    var detectionResult by remember { mutableStateOf("Esperando imagen...") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val classifier = remember {
        try {
            TomateClassifier(context)
        } catch (e: Exception) {
            errorMessage = e.message
            null
        }
    }

    /* ───── GALERÍA ───── */
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }

                detectionResult = classifier?.classify(bitmap) ?: "Error IA"

            } catch (e: Exception) {
                detectionResult = "Error al procesar imagen"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (errorMessage != null) {
            Text(
                text = "ERROR:\n$errorMessage",
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red)
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()

                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                try {
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
                                        bitmapBuffer,
                                        0, 0,
                                        bitmapBuffer.width,
                                        bitmapBuffer.height,
                                        matrix,
                                        true
                                    )

                                    detectionResult =
                                        classifier?.classify(rotatedBitmap) ?: "Error IA"

                                } catch (e: Exception) {
                                    Log.e("Camera", "Error", e)
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )

                        } catch (e: Exception) {
                            Log.e("Camera", "Error cámara", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = detectionResult,
                    color = if (detectionResult.contains("Sano")) Color.Green else Color.Yellow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(onClick = {
                    galleryLauncher.launch("image/*")
                }) {
                    Text("📁 Analizar imagen de galería")
                }
            }
        }
    }
}
