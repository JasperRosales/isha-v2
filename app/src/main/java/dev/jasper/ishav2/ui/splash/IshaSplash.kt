package dev.jasper.ishav2.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How long the logo intro runs. MainActivity holds the splash a little past this. */
const val SPLASH_INTRO_MS = 2_600

private val SplashText = Color(0xFFF7EEDD)
private val SplashMuted = Color(0xFF9C948A)
private val SplashGlow = Color(0xFF1A1310)

/**
 * The loading screen after the system splash: the animated logo inks itself in
 * while the app finishes loading underneath. Shown once per cold start.
 */
@Composable
fun IshaSplash() {
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // The name arrives as the sparkle lands.
        textIn.animateTo(1f, tween(520, delayMillis = (SPLASH_INTRO_MS * 0.7f).toInt(), easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LogoInk)
            .background(Brush.radialGradient(listOf(SplashGlow, LogoInk))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedLogo(size = 240.dp, durationMillis = SPLASH_INTRO_MS)
            Spacer(Modifier.height(8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textIn.value)
                    .graphicsLayer { translationY = (1f - textIn.value) * 16f * density }
            ) {
                Text(
                    text = "isha",
                    color = SplashText,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "your personal assistant",
                    color = SplashMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
