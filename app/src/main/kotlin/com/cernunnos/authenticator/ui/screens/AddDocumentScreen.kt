package com.cernunnos.authenticator.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.model.DocumentType
import com.cernunnos.authenticator.ui.components.ImageCropScreen
import com.cernunnos.authenticator.ui.viewmodel.DocumentViewModel
import java.util.Calendar

enum class CropTarget { RECTO, VERSO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentScreen(
    vm: DocumentViewModel,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()

    var rectoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var versoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hasVerso by remember { mutableStateOf(false) }

    // Camera permission — must be requested before launching the camera intent
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingCameraLaunch by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            pendingCameraLaunch = true
        }
    }

    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DocumentType.OTHER) }
    var notes by remember { mutableStateOf("") }
    var hasExpiration by remember { mutableStateOf(false) }
    var expirationYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR) + 5) }
    var expirationMonth by remember { mutableStateOf(0) }

    // Crop state
    var cropTarget by remember { mutableStateOf<CropTarget?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Recycle all bitmaps when leaving the screen to avoid memory leaks.
    // Single DisposableEffect to avoid double-recycle crashes.
    DisposableEffect(Unit) {
        onDispose {
            rectoBitmap?.recycle()
            versoBitmap?.recycle()
            pendingCropBitmap?.recycle()
        }
    }

    // Gallery picker — stores result then launches crop
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = decodeBitmapWithExifOrientation(context.contentResolver, uri, 2560)
            if (bitmap != null) {
                pendingCropBitmap = bitmap
                cropTarget = if (rectoBitmap == null) CropTarget.RECTO else CropTarget.VERSO
            }
        }
    }

    // Camera picker — uses TakePicture (full resolution) instead of TakePicturePreview (thumbnail)
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            val uri = cameraPhotoUri
            if (uri != null) {
                val bitmap = decodeBitmapWithExifOrientation(context.contentResolver, uri, 2560)
                // Clean up only the specific temp camera file that was created
                try {
                    val photoPath = uri.path
                    if (photoPath != null) {
                        java.io.File(photoPath).takeIf { it.exists() && it.name.startsWith("doc_photo_") }?.delete()
                    }
                } catch (_: Exception) {}
                if (bitmap != null) {
                    pendingCropBitmap = bitmap
                    cropTarget = if (rectoBitmap == null) CropTarget.RECTO else CropTarget.VERSO
                }
            }
        }
    }

    fun launchCamera() {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        try {
            val sharedDir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val photoFile = java.io.File(sharedDir, "doc_photo_${System.currentTimeMillis()}.jpg")
            cameraPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            val uri = cameraPhotoUri
            if (uri != null) {
                cameraLauncher.launch(uri)
            }
        } catch (e: Exception) {
            android.util.Log.e("AddDocumentScreen", "Failed to launch camera", e)
        }
    }

    // Launch camera after permission was granted
    LaunchedEffect(pendingCameraLaunch) {
        if (pendingCameraLaunch) {
            pendingCameraLaunch = false
            launchCamera()
        }
    }

    fun pickImage() {
        if (cropTarget == CropTarget.VERSO || (rectoBitmap != null && hasVerso)) {
            cropTarget = CropTarget.VERSO
        } else {
            cropTarget = CropTarget.RECTO
        }
        // We'll show a chooser dialog instead
    }

    // Navigate away on success — check for the added message prefix in both languages
    LaunchedEffect(state.message) {
        val msg = state.message
        if (msg != null && (msg.startsWith("Document added") || msg.startsWith("Document ajouté"))) {
            vm.clearMessage()
            onDone()
        }
    }

    // Show crop screen if needed
    val cropBitmap = pendingCropBitmap
    if (cropBitmap != null && cropTarget != null) {
        ImageCropScreen(
            bitmap = cropBitmap,
            onConfirm = { cropped ->
                when (cropTarget) {
                    CropTarget.RECTO -> {
                        // Recycle the old bitmap before replacing it
                        rectoBitmap?.recycle()
                        rectoBitmap = cropped
                    }
                    CropTarget.VERSO -> {
                        versoBitmap?.recycle()
                        versoBitmap = cropped
                    }
                    null -> {}
                }
                // Recycle the original full-size bitmap — only the cropped version is kept
                pendingCropBitmap?.recycle()
                pendingCropBitmap = null
                cropTarget = null
            },
            onCancel = {
                pendingCropBitmap?.recycle()
                pendingCropBitmap = null
                cropTarget = null
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.doc_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.doc_cancel))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Recto ──
            Text(stringResource(R.string.doc_recto), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            val recto = rectoBitmap
            if (recto != null) {
                ImagePreview(
                    bitmap = recto,
                    onReplace = {
                        cropTarget = CropTarget.RECTO
                        galleryLauncher.launch("image/*")
                    },
                    onRemove = { rectoBitmap?.recycle(); rectoBitmap = null },
                )
            } else {
                ImagePickerButtons(
                    onCamera = {
                        cropTarget = CropTarget.RECTO
                        launchCamera()
                    },
                    onGallery = {
                        cropTarget = CropTarget.RECTO
                        galleryLauncher.launch("image/*")
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Recto/Verso toggle ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = hasVerso, onCheckedChange = {
                    hasVerso = it
                    if (!it) { versoBitmap?.recycle(); versoBitmap = null }
                })
                Text(stringResource(R.string.doc_recto_verso_toggle))
            }

            // ── Verso ──
            if (hasVerso) {
                Text(stringResource(R.string.doc_verso), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                val verso = versoBitmap
                if (verso != null) {
                    ImagePreview(
                        bitmap = verso,
                        onReplace = {
                            cropTarget = CropTarget.VERSO
                            galleryLauncher.launch("image/*")
                        },
                        onRemove = { versoBitmap?.recycle(); versoBitmap = null },
                    )
                } else {
                    ImagePickerButtons(
                        onCamera = {
                            cropTarget = CropTarget.VERSO
                            launchCamera()
                        },
                        onGallery = {
                            cropTarget = CropTarget.VERSO
                            galleryLauncher.launch("image/*")
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Metadata ──
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.doc_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.doc_title_placeholder)) },
            )

            Text(stringResource(R.string.doc_type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            DocumentTypeDropdown(selected = selectedType, onSelect = { selectedType = it })

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = hasExpiration, onCheckedChange = { hasExpiration = it })
                Text(stringResource(R.string.doc_has_expiration))
            }
            if (hasExpiration) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = expirationMonth.let { context.resources.getStringArray(R.array.month_names).toList()[it] },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.doc_month)) },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                context.resources.getStringArray(R.array.month_names).toList().forEachIndexed { idx, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { expirationMonth = idx; expanded = false },
                                    )
                                }
                            }
                        },
                    )
                    OutlinedTextField(
                        value = expirationYear.toString(),
                        onValueChange = { it.toIntOrNull()?.let { y -> expirationYear = y } },
                        label = { Text(stringResource(R.string.doc_year)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.doc_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val recto = rectoBitmap ?: return@Button
                    val expiration = if (hasExpiration) {
                        val cal = Calendar.getInstance()
                        cal.clear()
                        cal.set(Calendar.YEAR, expirationYear)
                        cal.set(Calendar.MONTH, expirationMonth)
                        cal.set(Calendar.DAY_OF_MONTH, 15)
                        cal.timeInMillis
                    } else null
                    vm.addDocument(recto, if (hasVerso) versoBitmap else null, selectedType, title.ifBlank { "Untitled" }, expiration, notes)
                },
                enabled = rectoBitmap != null && title.isNotBlank() && (!hasVerso || versoBitmap != null),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.doc_save))
            }
        }
    }
}

@Composable
private fun ImagePreview(bitmap: Bitmap, onReplace: () -> Unit, onRemove: () -> Unit) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Document preview",
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Fit,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onReplace) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.doc_replace))
        }
        TextButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.doc_remove))
        }
    }
}

@Composable
private fun ImagePickerButtons(onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.doc_camera))
        }
        OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.doc_gallery))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentTypeDropdown(selected: DocumentType, onSelect: (DocumentType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val types = DocumentType.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = documentTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.doc_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(documentTypeLabel(type)) },
                    onClick = { onSelect(type); expanded = false },
                )
            }
        }
    }
}

/**
 * Calculate inSampleSize for BitmapFactory to downsample large images
 * while keeping quality high enough for documents.
 */
private fun calculateSampleSize(width: Int, height: Int, maxDim: Int): Int {
    var sampleSize = 1
    val largestDim = maxOf(width, height)
    while (largestDim / sampleSize > maxDim) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Decode a bitmap from a content URI, capping the max dimension to avoid OOM,
 * and apply the EXIF orientation so the image is displayed in the correct direction.
 *
 * Android cameras store the rotation in EXIF metadata, not in the pixel data.
 * BitmapFactory.decodeStream ignores EXIF, so we must read and apply it manually.
 */
private fun decodeBitmapWithExifOrientation(
    resolver: android.content.ContentResolver,
    uri: Uri,
    maxDim: Int,
): Bitmap? {
    // Step 1: Get image bounds
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    // Step 2: Calculate sample size
    val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDim)

    // Step 3: Decode the bitmap
    val bitmap = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        })
    } ?: return null

    // Step 4: Read EXIF orientation and apply rotation
    val exifOrientation = resolver.openInputStream(uri)?.use { stream ->
        try {
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    } ?: ExifInterface.ORIENTATION_NORMAL

    return applyExifRotation(bitmap, exifOrientation)
}

private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        else -> return bitmap // no rotation needed
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}
