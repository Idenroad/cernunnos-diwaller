package com.cernunnos.authenticator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.totp.TotpGenerator

/**
 * Canvas-drawn TOTP code display.
 * Draws digits directly on Canvas (no TextView/Text) to prevent
 * accessibility services from reading the code.
 */
@Composable
fun TotpCanvasView(
    code: String,
    remainingSeconds: Int,
    period: Int,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 48.sp,
    digitColor: Color = MaterialTheme.colorScheme.onSurface,
    countdownColor: Color = if (remainingSeconds <= 5)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val textPx = with(density) { textSize.toPx() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Draw code on canvas — no Text composable for the digits
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { textSize.toDp() + 16.dp }),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = digitColor.toArgb()
                        this.textSize = textPx
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.MONOSPACE,
                            android.graphics.Typeface.BOLD,
                        )
                    }
                    val metrics = paint.fontMetrics
                    val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
                    val digitWidth = paint.measureText("0")
                    val spacing = digitWidth * 0.3f
                    val totalWidth = code.length * digitWidth + (code.length - 1) * spacing
                    var x = (size.width - totalWidth) / 2f + digitWidth / 2f
                    for (ch in code) {
                        canvas.nativeCanvas.drawText(ch.toString(), x, baseline, paint)
                        x += digitWidth + spacing
                    }
                }
            }
        }

        // Circular countdown indicator
        CountdownRing(
            remaining = remainingSeconds,
            total = period,
            color = countdownColor,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(48.dp),
        )
    }
}

@Composable
fun CountdownRing(
    remaining: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        // Guard against total=0 (corrupted entry) to prevent division by zero
        val sweep = if (total > 0) 360f * remaining / total else 0f
        val strokePx = strokeWidth.toPx()
        val diameter = minOf(size.width, size.height) - strokePx
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx),
        )
    }
}

/**
 * Composable that computes and displays the current TOTP code for an entry.
 */
@Composable
fun TotpDisplay(
    entry: TotpEntry,
    tick: Long,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 36.sp,
) {
    val code = try {
        TotpGenerator.generate(
            secret = entry.secret,
            step = entry.period,
            digits = entry.digits,
            algorithm = entry.algorithm,
        )
    } catch (e: Exception) {
        "------"
    }
    val remaining = try {
        TotpGenerator.remainingSeconds(entry.period)
    } catch (e: Exception) {
        0
    }
    TotpCanvasView(
        code = code,
        remainingSeconds = remaining,
        period = entry.period,
        modifier = modifier,
        textSize = textSize,
    )
}

/**
 * Compact inline TOTP code display for list rows.
 * Draws the code on canvas (no Text) + a small countdown ring
 * with remaining seconds in gray in the center.
 * Ring starts violet and turns red in the last 5 seconds.
 */
@Composable
fun TotpCodeCompact(
    code: String,
    remainingSeconds: Int,
    period: Int,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 22.sp,
    isHotp: Boolean = false,
    onIncrementHotp: () -> Unit = {},
    masked: Boolean = false,
) {
    val density = LocalDensity.current
    val textPx = with(density) { textSize.toPx() }
    val digitColor = MaterialTheme.colorScheme.onSurface
    val ringColor = if (remainingSeconds <= 5)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val numColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        // Code drawn on canvas
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(with(density) { textSize.toDp() + 4.dp }),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = digitColor.toArgb()
                        this.textSize = textPx
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.RIGHT
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.MONOSPACE,
                            android.graphics.Typeface.BOLD,
                        )
                    }
                    val metrics = paint.fontMetrics
                    val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
                    val digitWidth = paint.measureText("0")
                    val spacing = digitWidth * 0.2f
                    // When masked, draw bullets instead of real digits
                    val displayCode = if (masked) "•".repeat(code.length) else code
                    val totalWidth = displayCode.length * digitWidth + (displayCode.length - 1) * spacing
                    var x = size.width - totalWidth + digitWidth / 2f
                    for (ch in displayCode) {
                        canvas.nativeCanvas.drawText(ch.toString(), x, baseline, paint)
                        x += digitWidth + spacing
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (isHotp) {
            // HOTP: show counter badge instead of countdown ring
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "#${remainingSeconds}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        color = numColor,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                )
            }
        } else {
            // TOTP: countdown ring with seconds in center
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = with(density) { 3.dp.toPx() }
                val diameter = minOf(size.width, size.height) - strokePx
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f,
                )
                val arcSize = Size(diameter, diameter)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                // Progress (violet → red)
                // Guard against period=0 (corrupted entry) to prevent ArithmeticException
                val sweep = if (period > 0) 360f * remainingSeconds / period else 0f
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                // Seconds number in center
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = numColor.toArgb()
                        this.textSize = with(density) { 11.sp.toPx() }
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.DEFAULT,
                            android.graphics.Typeface.BOLD,
                        )
                    }
                    val metrics = paint.fontMetrics
                    val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
                    canvas.nativeCanvas.drawText(
                        remainingSeconds.toString(),
                        size.width / 2f,
                        baseline,
                        paint,
                    )
                }
            }
        }
        } // end else (TOTP)
    }
}
