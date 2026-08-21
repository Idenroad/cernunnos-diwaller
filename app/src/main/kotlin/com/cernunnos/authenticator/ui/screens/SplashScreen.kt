package com.cernunnos.authenticator.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.ui.theme.CernunnosPrimaryLight
import kotlinx.coroutines.delay

/**
 * Splash screen with frame-by-frame animation + Cernunnos logo fading in.
 *
 * Black background. The animation plays centered (ContentScale.Fit).
 * Below the animation, bas_image.jpg continues the violet halo into the black
 * background so there's no visible rectangle edge.
 * The Cernunnos logo + title fade in over 5 seconds at the top of the screen.
 *
 * The animation pauses when the app goes to background (saves battery + RAM)
 * and resumes where it left off when the app returns to foreground.
 */
@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLogo by remember { mutableStateOf(false) }
    var animationFinished by remember { mutableStateOf(false) }
    var currentFrame by remember { mutableStateOf(0) }
    // Track whether the app is in the foreground — animation only runs when visible.
    var isResumed by remember { mutableStateOf(true) }

    val totalFrames = 120  // 120 frames @ 24fps = 5 seconds
    val fps = 24
    val frameDelayMs = 1000L / fps

    // Pause/resume animation based on lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                Lifecycle.Event.ON_RESUME -> isResumed = true
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Stream frames one at a time instead of preloading all 241 into RAM.
    // Preloading = 847MB (241 × 3.5MB ARGB_8888), streaming = ~0.9MB peak.
    // The previous frame's bitmap is recycled when currentFrame changes to
    // prevent accumulation of 241 bitmaps in memory.
    var previousBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val currentBitmap = remember(currentFrame) {
        // Recycle the previous frame's bitmap before loading the next one
        previousBitmap?.recycle()
        val path = "lottie/images/${currentFrame + 1}.webp"
        val bmp = context.assets.open(path).use { stream ->
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2 // 1232x748 → 616x374, ~0.9MB per frame
            }
            BitmapFactory.decodeStream(stream, null, opts)
        }
        previousBitmap = bmp
        bmp?.asImageBitmap()
    }

    // Preload bas_image (continuation of the animation into the background)
    val bottomImage = remember {
        context.assets.open("lottie/bas_image.jpg").use { stream ->
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
        }
    }

    // Recycle all bitmaps when the splash screen is disposed
    DisposableEffect(Unit) {
        onDispose {
            previousBitmap?.recycle()
            previousBitmap = null
            // bottomImage is a single ~0.9MB bitmap; rely on GC for it.
        }
    }

    // Animation loop — pauses when app is backgrounded, resumes on foreground.
    // Uses a while loop with isResumed check instead of a for loop so it can
    // suspend (not advance frames) while in background.
    LaunchedEffect(Unit) {
        while (currentFrame < totalFrames - 1) {
            if (isResumed) {
                currentFrame++
                delay(frameDelayMs)
            } else {
                // App is in background — wait without advancing frames
                delay(100)
            }
        }
        animationFinished = true
    }

    LaunchedEffect(Unit) {
        showLogo = true
    }

    LaunchedEffect(animationFinished) {
        if (animationFinished) {
            delay(300)
            onFinish()
        }
    }

    // Safety timeout: if animation gets stuck (e.g. app backgrounded for too long),
    // finish after 15s total instead of 5s.
    LaunchedEffect(Unit) {
        delay(15_000)
        if (!animationFinished) onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // 1. Animation frame (centered, Fit = garde le ratio 1232x748)
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // 2. bas_image.jpg sous l'animation — continue le halo violet vers le noir
        // L'animation est 1232x748 (ratio ~1.65:1). Sur un écran portrait 1080x2400,
        // ContentScale.Fit la centre horizontalement. Le bas de l'animation est
        // à ~40% de la hauteur. On place bas_image juste en dessous.
        if (bottomImage != null) {
            Image(
                bitmap = bottomImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                contentScale = ContentScale.FillWidth,
            )
        }

        // 3. Dégradé en haut : noir opaque → transparent (pour fondre le haut)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.15f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // 4. Logo Cernunnos + titre en haut, fondu de 5 secondes
        AnimatedVisibility(
            visible = showLogo,
            enter = fadeIn(animationSpec = tween(5000)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Spacer(Modifier.height(40.dp))
                Image(
                    painter = painterResource(R.drawable.cernunnos_logo),
                    contentDescription = "Cernunnos Diwaller",
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CernunnosPrimaryLight,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
