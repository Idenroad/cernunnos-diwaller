package com.cernunnos.authenticator.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Image crop screen with an adjustable rectangle overlay.
 *
 * The user can:
 * - Drag the crop rectangle to move it
 * - Drag the corners to resize it
 * - Pinch to zoom/pan the image
 * - Rotate the image 90°
 *
 * On confirm, the cropped bitmap is returned via [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Crop rect in normalized coordinates (0..1)
    var cropLeft by remember { mutableFloatStateOf(0.1f) }
    var cropTop by remember { mutableFloatStateOf(0.1f) }
    var cropRight by remember { mutableFloatStateOf(0.9f) }
    var cropBottom by remember { mutableFloatStateOf(0.9f) }

    // Which handle is being dragged
    var dragHandle by remember { mutableStateOf<DragHandle?>(null) }

    // Track the actual view size for accurate crop mapping
    var viewWidth by remember { mutableFloatStateOf(1f) }
    var viewHeight by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.cernunnos.authenticator.R.string.doc_crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(com.cernunnos.authenticator.R.string.doc_crop_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(Icons.Default.RotateRight, contentDescription = stringResource(com.cernunnos.authenticator.R.string.doc_crop_rotate))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val cropped = cropBitmap(bitmap, rotation, zoom, panX, panY, cropLeft, cropTop, cropRight, cropBottom, viewWidth, viewHeight)
                    onConfirm(cropped)
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(com.cernunnos.authenticator.R.string.doc_crop_confirm))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.5f, 5f)
                        panX += pan.x
                        panY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Image with zoom/pan/rotation
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image to crop",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = panX,
                        translationY = panY,
                        rotationZ = rotation,
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.5f, 5f)
                            panX += pan.x
                            panY += pan.y
                        }
                    },
                contentScale = ContentScale.Fit,
            )

            // Crop overlay
            val handleColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        viewWidth = size.width.toFloat()
                        viewHeight = size.height.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragHandle = findHandle(
                                    offset, cropLeft, cropTop, cropRight, cropBottom,
                                    size.width.toFloat(), size.height.toFloat(),
                                )
                            },
                            onDragEnd = { dragHandle = null },
                            onDrag = { change, drag ->
                                change.consume()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val dx = drag.x / w
                                val dy = drag.y / h
                                when (dragHandle) {
                                    DragHandle.LEFT -> cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                    DragHandle.RIGHT -> cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                    DragHandle.TOP -> cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                    DragHandle.BOTTOM -> cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                    DragHandle.TOP_LEFT -> {
                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                        cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                    }
                                    DragHandle.TOP_RIGHT -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                        cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.05f)
                                    }
                                    DragHandle.BOTTOM_LEFT -> {
                                        cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.05f)
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                    }
                                    DragHandle.BOTTOM_RIGHT -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + 0.05f, 1f)
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.05f, 1f)
                                    }
                                    DragHandle.CENTER -> {
                                        val w2 = cropRight - cropLeft
                                        val h2 = cropBottom - cropTop
                                        cropLeft = (cropLeft + dx).coerceIn(0f, 1f - w2)
                                        cropRight = cropLeft + w2
                                        cropTop = (cropTop + dy).coerceIn(0f, 1f - h2)
                                        cropBottom = cropTop + h2
                                    }
                                    null -> {}
                                }
                            },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                val cropRect = Rect(
                    left = cropLeft * w,
                    top = cropTop * h,
                    right = cropRight * w,
                    bottom = cropBottom * h,
                )

                // Dark overlay outside crop area
                val outsidePath = Path().apply {
                    addRect(Rect(0f, 0f, w, h))
                    addRect(cropRect)
                }
                drawPath(outsidePath, Color.Black.copy(alpha = 0.6f))

                // Crop border
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cropRect.left, cropRect.top),
                    size = Size(cropRect.width, cropRect.height),
                    style = Stroke(width = 3f),
                )

                // Corner handles
                val handleSize = 30f
                val handleColor = handleColor // captured from composable scope
                listOf(
                    Offset(cropRect.left, cropRect.top),
                    Offset(cropRect.right, cropRect.top),
                    Offset(cropRect.left, cropRect.bottom),
                    Offset(cropRect.right, cropRect.bottom),
                ).forEach { corner ->
                    drawRect(
                        color = handleColor,
                        topLeft = Offset(corner.x - handleSize / 2, corner.y - handleSize / 2),
                        size = Size(handleSize, handleSize),
                    )
                }

                // Edge midpoints (for edge dragging)
                listOf(
                    Offset(cropRect.center.x, cropRect.top),
                    Offset(cropRect.center.x, cropRect.bottom),
                    Offset(cropRect.left, cropRect.center.y),
                    Offset(cropRect.right, cropRect.center.y),
                ).forEach { mid ->
                    drawRect(
                        color = handleColor.copy(alpha = 0.7f),
                        topLeft = Offset(mid.x - handleSize / 2, mid.y - handleSize / 2),
                        size = Size(handleSize, handleSize),
                    )
                }
            }
        }
    }
}

private enum class DragHandle {
    LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

private fun findHandle(
    pos: Offset,
    cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
    w: Float, h: Float,
): DragHandle {
    val x = pos.x
    val y = pos.y
    val left = cropLeft * w
    val right = cropRight * w
    val top = cropTop * h
    val bottom = cropBottom * h
    val tolerance = 40f

    // Corners first
    if (kotlin.math.abs(x - left) < tolerance && kotlin.math.abs(y - top) < tolerance) return DragHandle.TOP_LEFT
    if (kotlin.math.abs(x - right) < tolerance && kotlin.math.abs(y - top) < tolerance) return DragHandle.TOP_RIGHT
    if (kotlin.math.abs(x - left) < tolerance && kotlin.math.abs(y - bottom) < tolerance) return DragHandle.BOTTOM_LEFT
    if (kotlin.math.abs(x - right) < tolerance && kotlin.math.abs(y - bottom) < tolerance) return DragHandle.BOTTOM_RIGHT

    // Edges
    if (kotlin.math.abs(x - left) < tolerance && y > top && y < bottom) return DragHandle.LEFT
    if (kotlin.math.abs(x - right) < tolerance && y > top && y < bottom) return DragHandle.RIGHT
    if (kotlin.math.abs(y - top) < tolerance && x > left && x < right) return DragHandle.TOP
    if (kotlin.math.abs(y - bottom) < tolerance && x > left && x < right) return DragHandle.BOTTOM

    // Center
    if (x > left && x < right && y > top && y < bottom) return DragHandle.CENTER

    return DragHandle.CENTER
}

private fun cropBitmap(
    bitmap: Bitmap,
    rotation: Float,
    zoom: Float,
    panX: Float,
    panY: Float,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    viewWidth: Float,
    viewHeight: Float,
): Bitmap {
    // The image is displayed with ContentScale.Fit, then graphicsLayer applies
    // zoom, pan, and rotation around the view center. The crop rectangle is in
    // normalized view coordinates (0..1). To crop correctly, we must:
    // 1. Invert the graphicsLayer transform (un-translate, un-rotate, un-scale)
    // 2. Map from pre-transform view space to bitmap space via ContentScale.Fit
    // 3. Crop from the original bitmap
    // 4. Rotate the cropped result to match the visual orientation
    val bw = bitmap.width
    val bh = bitmap.height
    val vw = viewWidth
    val vh = viewHeight

    // ContentScale.Fit on the ORIGINAL bitmap (before graphicsLayer)
    val fitScaleX = vw / bw
    val fitScaleY = vh / bh
    val fitScale = minOf(fitScaleX, fitScaleY)
    val displayedW = bw * fitScale
    val displayedH = bh * fitScale
    val displayOffsetX = (vw - displayedW) / 2f
    val displayOffsetY = (vh - displayedH) / 2f

    // Crop corners in view pixel space
    val cropViewLeft = cropLeft * vw
    val cropViewTop = cropTop * vh
    val cropViewRight = cropRight * vw
    val cropViewBottom = cropBottom * vh

    // Invert graphicsLayer transform: view coords → pre-graphicsLayer view coords
    // Compose graphicsLayer applies: scale → rotate → translate (around center)
    // Inverse: un-translate → un-rotate → un-scale
    val cx = vw / 2f
    val cy = vh / 2f
    val rad = Math.toRadians(-rotation.toDouble())
    val cosRad = Math.cos(rad).toFloat()
    val sinRad = Math.sin(rad).toFloat()

    fun invertTransform(px: Float, py: Float): Pair<Float, Float> {
        // 1. Un-translate
        var x = px - panX
        var y = py - panY
        // 2. Un-rotate around view center
        val dx = x - cx
        val dy = y - cy
        x = cx + dx * cosRad - dy * sinRad
        y = cy + dx * sinRad + dy * cosRad
        // 3. Un-scale around view center
        x = cx + (x - cx) / zoom
        y = cy + (y - cy) / zoom
        return x to y
    }

    // Map all 4 corners to handle rotated rectangles correctly.
    // For 90° increments, the un-rotated rectangle is still axis-aligned
    // (just with swapped dimensions), so the bounding box is exact.
    val (x1, y1) = invertTransform(cropViewLeft, cropViewTop)
    val (x2, y2) = invertTransform(cropViewRight, cropViewTop)
    val (x3, y3) = invertTransform(cropViewLeft, cropViewBottom)
    val (x4, y4) = invertTransform(cropViewRight, cropViewBottom)

    val minX = minOf(x1, x2, x3, x4)
    val minY = minOf(y1, y2, y3, y4)
    val maxX = maxOf(x1, x2, x3, x4)
    val maxY = maxOf(y1, y2, y3, y4)

    // Map from pre-graphicsLayer view space to bitmap space
    val bmpLeft = ((minX - displayOffsetX) / fitScale).toInt().coerceIn(0, bw - 1)
    val bmpTop = ((minY - displayOffsetY) / fitScale).toInt().coerceIn(0, bh - 1)
    val bmpRight = ((maxX - displayOffsetX) / fitScale).toInt().coerceIn(bmpLeft + 1, bw)
    val bmpBottom = ((maxY - displayOffsetY) / fitScale).toInt().coerceIn(bmpTop + 1, bh)

    // Crop from the original bitmap (no pre-rotation)
    val cropped = Bitmap.createBitmap(bitmap, bmpLeft, bmpTop, bmpRight - bmpLeft, bmpBottom - bmpTop)

    // Apply rotation to the cropped result to match the visual orientation
    if (rotation != 0f) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation)
        val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        if (rotated != cropped) cropped.recycle()
        return rotated
    }

    return cropped
}
