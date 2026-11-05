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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** How long the logo intro runs. */
const val SPLASH_INTRO_MS = 2_600

/** After the intro, the logo idles this long while the app composes underneath. */
private const val SPLASH_HOLD_MS = 550L

private val SplashText = Color(0xFFF7EEDD)
private val SplashMuted = Color(0xFF9C948A)
private val SplashGlow = Color(0xFF1A1310)

/**
 * The loading screen after the system splash (which is just the same dark colour, so
 * the handoff is invisible). The animated logo plays in full; only once it has finished
 * is the app composed underneath ([onIntroDone]), so the intro never competes with app
 * start-up for frames. After a short idle hold, [onFinished] lets the caller fade it out.
 * Shown once per cold start.
 */
@Composable
fun IshaSplash(
    onIntroDone: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val textIn = remember { Animatable(0f) }
    val glowIn = remember { Animatable(0f) }
    val introDone by rememberUpdatedState(onIntroDone)
    val finished by rememberUpdatedState(onFinished)
    var logoDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // The name arrives as the sparkle lands.
        textIn.animateTo(1f, tween(520, delayMillis = (SPLASH_INTRO_MS * 0.7f).toInt(), easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        // Fade the warm centre in so it doesn't pop over the flat system splash.
        glowIn.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(logoDone) {
        if (!logoDone) return@LaunchedEffect
        introDone()
        delay(SPLASH_HOLD_MS)
        finished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LogoInk)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(SplashGlow, LogoInk)), alpha = glowIn.value)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedLogo(
                size = 240.dp,
                durationMillis = SPLASH_INTRO_MS,
                onFinished = { logoDone = true }
            )
            Spacer(Modifier.height(8.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    // Read inside the layer block so the fade only redraws, never recomposes.
                    .graphicsLayer {
                        alpha = textIn.value
                        translationY = (1f - textIn.value) * 16f * density
                    }
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
