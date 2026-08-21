package com.cernunnos.authenticator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs

/**
 * Generates a service icon from the issuer name.
 * Uses exact brand colors for ~150 popular services (BrandColors database).
 * Falls back to deterministic hash-based color for unknown services.
 *
 * Icon resolution priority:
 * 1. [customIconUri] — user-picked image from gallery (rendered with Coil)
 * 2. [iconName] — explicitly assigned Material icon
 * 3. Auto-mapping from issuer name via [AutoIconMapper]
 * 4. Initials with brand-colored background (fallback)
 *
 * Shape: rounded square (more modern than circle, matches Aegis/2FAS style).
 */
@Composable
fun ServiceIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textSize: TextUnit = 16.sp,
    iconName: String? = null,
    customIconUri: String? = null,
) {
    val displayName = name.ifBlank { "?" }
    val bgColor = colorForName(displayName)
    val cornerRadius = size / 4
    // Use explicit iconName if set, otherwise try automatic mapping from issuer name
    val effectiveIconName = iconName ?: AutoIconMapper.getIconName(displayName)
    val iconVector = effectiveIconName?.let { IconRegistry.getIcon(it) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Custom image from internal storage or URI
            !customIconUri.isNullOrBlank() -> {
                val context = LocalContext.current
                // Support both file paths (internal storage) and content URIs
                val modelData: Any = if (customIconUri.startsWith("/")) {
                    java.io.File(customIconUri)
                } else {
                    customIconUri.toUri()
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(modelData)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
                )
            }
            // Material icon (explicit or auto-mapped)
            iconVector != null -> {
                Icon(
                    imageVector = iconVector,
                    contentDescription = displayName,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
            // Fallback: initials
            else -> {
                val initials = getInitials(displayName)
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Extract 1-2 letter initials from a service name.
 * "Amazon" → "A", "Google LLC" → "G", "Proton Mail" → "PM"
 */
private fun getInitials(name: String): String {
    val clean = name.trim().replace(Regex("[^a-zA-Z0-9 ]"), "")
    if (clean.isEmpty()) return "?"

    val words = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size == 1 -> {
            val w = words[0]
            if (w.length <= 3) w.uppercase()
            else w.take(1).uppercase()
        }
        words.size >= 2 -> {
            (words[0].take(1) + words[1].take(1)).uppercase()
        }
        else -> "?"
    }
}

/**
 * Get the color for a service name.
 * Priority: exact brand color > hash-based fallback.
 */
private fun colorForName(name: String): Color {
    // Try brand database first
    BrandColors.getColor(name)?.let { return Color(it) }

    // Fallback: deterministic hash-based color
    val hash = abs(name.lowercase().hashCode())
    val palette = listOf(
        Color(0xFF2D9CDB), // blue
        Color(0xFFEB5757), // red
        Color(0xFF6FCF97), // green
        Color(0xFFF2C94C), // yellow
        Color(0xFFBB6BD9), // purple
        Color(0xFF56CCF2), // cyan
        Color(0xFFFF6B6B), // coral
        Color(0xFF4ECDC4), // teal
        Color(0xFF95E1D3), // mint
        Color(0xFFA8E6CF), // light green
        Color(0xFFFF8A65), // orange
        Color(0xFF7986CB), // indigo
        Color(0xFF4DB6AC), // teal dark
        Color(0xFFFFB74D), // amber
        Color(0xFFA1887F), // brown
        Color(0xFF90A4AE), // blue grey
    )
    return palette[hash % palette.size]
}
