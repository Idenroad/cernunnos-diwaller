package com.cernunnos.authenticator.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cernunnos.authenticator.R
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Full-screen QR code scanner using CameraX + ML Kit.
 *
 * @param onResult Called when a QR code is successfully scanned.
 * @param onDismiss Called when the user closes the scanner without scanning.
 */
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            CameraPreviewWithScanner(
                onResult = onResult,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Camera permission required to scan QR codes",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.qr_grant_permission))
                }
            }
        }

        // Close button (top-right)
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.5f)),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close scanner",
                tint = Color.White,
            )
        }

        // Hint text (bottom)
        if (hasCameraPermission) {
            Text(
                "Point camera at the QR code",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/** Camera state for loading/error/success feedback. */
private enum class CameraState { LOADING, READY, ERROR, SCANNED }

@Composable
private fun CameraPreviewWithScanner(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // ML Kit scanner — may fail on GrapheneOS without Google Play Services.
    // If initialization fails, we fall back to ZXing (pure Java, no GMS needed).
    val scanner = remember {
        try {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
        } catch (e: Exception) {
            null // Fallback to ZXing will be used
        }
    }
    // ZXing reader as fallback (pure Java, works everywhere)
    val zxingReader = remember { MultiFormatReader() }

    var cameraState by remember { mutableStateOf(CameraState.LOADING) }
    var retryKey by remember { mutableStateOf(0) }

    // Hold a reference to the camera provider so we can unbind synchronously
    // the instant a QR code is detected — this prevents the black-screen
    // flash that happens when the SurfaceView is torn down while the camera
    // is still streaming frames.
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val scanHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    // Atomic flag to prevent multiple QR detections from racing on the analyzer thread.
    // Compose state (cameraState) must NOT be read from a background thread.
    val resultDelivered = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    LaunchedEffect(retryKey) {
        resultDelivered.set(false)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                cameraProviderRef = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { image ->
                            processImage(image, scanner, zxingReader) { value ->
                                // The analyzer runs on a background thread.
                                // Use an atomic flag to ensure only the first detection
                                // proceeds — subsequent frames are ignored.
                                if (resultDelivered.compareAndSet(false, true)) {
                                    // Stop the camera immediately (unbindAll is thread-safe),
                                    // then post the state change + callback to the main thread.
                                    cameraProvider.unbindAll()
                                    ContextCompat.getMainExecutor(context).execute {
                                        cameraState = CameraState.SCANNED
                                        // Brief delay so the user sees the green
                                        // checkmark confirmation before the screen
                                        // transitions away.
                                        scanHandler.postDelayed({
                                            onResult(value)
                                        }, 400)
                                    }
                                }
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                cameraState = CameraState.READY
            } catch (e: Exception) {
                cameraState = CameraState.ERROR
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Cleanup when the composable leaves the tree.
    DisposableEffect(Unit) {
        onDispose {
            scanHandler.removeCallbacksAndMessages(null)
            cameraProviderRef?.unbindAll()
            cameraExecutor.shutdown()
            scanner?.close()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // Loading overlay
        if (cameraState == CameraState.LOADING) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Starting camera…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Success overlay — shown briefly after a QR is detected
        if (cameraState == CameraState.SCANNED) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "QR code detected!",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        // Error overlay with retry
        if (cameraState == CameraState.ERROR) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Camera failed to start",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Another app may be using the camera. Close it and try again.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    cameraState = CameraState.LOADING
                    retryKey++
                }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

private fun processImage(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner?,
    zxingReader: MultiFormatReader,
    onResult: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        // Try ML Kit first (faster, more accurate). If null (GrapheneOS without GMS)
        // or if ML Kit fails, fall back to ZXing (pure Java, no GMS needed).
        if (scanner != null) {
            try {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { onResult(it) }
                        }
                    }
                    .addOnFailureListener {
                        // ML Kit failed — try ZXing fallback
                        try {
                            tryZxingScan(mediaImage, imageProxy.imageInfo.rotationDegrees, zxingReader, onResult)
                        } catch (e: Exception) {
                            // ZXing also failed — skip frame
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
                return
            } catch (e: Exception) {
                // ML Kit initialization failed — fall through to ZXing
            }
        }
        // ZXing fallback
        try {
            tryZxingScan(mediaImage, imageProxy.imageInfo.rotationDegrees, zxingReader, onResult)
        } catch (e: Exception) {
            // ZXing failed — skip frame
        }
        imageProxy.close()
    } else {
        imageProxy.close()
    }
}

/**
 * Scan a QR code using ZXing (pure Java, no Google Play Services needed).
 * Works on GrapheneOS, CalyxOS, and any AOSP-based ROM.
 */
private fun tryZxingScan(
    mediaImage: android.media.Image,
    rotationDegrees: Int,
    reader: MultiFormatReader,
    onResult: (String) -> Unit,
) {
    try {
        // Convert YUV_420_888 to ZXing luminance source
        val yBuffer = mediaImage.planes[0].buffer
        val uBuffer = mediaImage.planes[1].buffer
        val vBuffer = mediaImage.planes[2].buffer
        val width = mediaImage.width
        val height = mediaImage.height
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        if (ySize <= 0 || uSize <= 0 || vSize <= 0) return
        val nv21 = ByteArray(ySize + uSize + vSize)
        // Copy buffers safely — some devices use direct ByteBuffer that may
        // throw BufferUnderflowException if the plane has padding.
        try {
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
        } catch (e: java.nio.BufferUnderflowException) {
            return
        }
        // Swap UV for NV21 format
        val uvStart = ySize
        val uvEnd = ySize + uSize + vSize
        var i = uvStart
        while (i < uvEnd - 1) {
            val tmp = nv21[i]
            nv21[i] = nv21[i + 1]
            nv21[i + 1] = tmp
            i += 2
        }
        // Rotate if needed
        val data = if (rotationDegrees == 0) {
            nv21
        } else {
            rotateNV21(nv21, width, height, rotationDegrees)
        }
        val (rotatedWidth, rotatedHeight) = when (rotationDegrees) {
            90, 270 -> height to width
            else -> width to height
        }
        val source = PlanarYUVLuminanceSource(data, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false)
        val binary = BinaryBitmap(HybridBinarizer(source))
        reader.setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)))
        val result = reader.decodeWithState(binary)
        onResult(result.text)
    } catch (e: NotFoundException) {
        // No QR code found in this frame — normal, just skip
    } catch (e: Exception) {
        // Other error — skip frame
    } finally {
        reader.reset()
    }
}

/** Rotate NV21 byte array by the given degrees. */
private fun rotateNV21(data: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
    if (rotation == 0) return data
    val output = ByteArray(data.size)
    when (rotation) {
        90 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    output[x * height + (height - 1 - y)] = data[y * width + x]
                }
            }
        }
        270 -> {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    output[(width - 1 - x) * height + y] = data[y * width + x]
                }
            }
        }
        180 -> {
            for (i in data.indices) {
                output[data.size - 1 - i] = data[i]
            }
        }
    }
    return output
}
