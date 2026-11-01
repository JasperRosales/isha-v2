package dev.jasper.ishav2.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jasper.ishav2.R
import dev.jasper.ishav2.ui.theme.IshaAccent
import dev.jasper.ishav2.ui.theme.IshaAccentSoft
import dev.jasper.ishav2.ui.theme.IshaCream
import dev.jasper.ishav2.ui.theme.IshaInk
import dev.jasper.ishav2.ui.theme.IshaMuted
import kotlinx.coroutines.launch

/**
 * The greeting after the system splash: isha pops in, bobs, and says hi while
 * the app finishes loading underneath. Shown once per cold start.
 */
@Composable
fun IshaSplash() {
    val pop = remember { Animatable(0.6f) }
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 260f))
        }
        launch {
            textIn.animateTo(1f, tween(520, delayMillis = 180, easing = FastOutSlowInEasing))
        }
    }
    val loop = rememberInfiniteTransition(label = "splash")
    val bob by loop.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val halo by loop.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(IshaCream, IshaAccentSoft.copy(alpha = 0.55f), IshaCream))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(176.dp)
                        .scale(halo)
                        .clip(CircleShape)
                        .background(IshaAccent.copy(alpha = 0.14f))
                )
                Image(
                    painter = painterResource(R.drawable.isha_avatar),
                    contentDescription = "isha",
                    modifier = Modifier
                        .size(132.dp)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            translationY = bob * density
                        }
                )
            }
            Spacer(Modifier.height(26.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textIn.value)
                    .graphicsLayer { translationY = (1f - textIn.value) * 16f * density }
            ) {
                Text(
                    text = "isha",
                    color = IshaInk,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "your personal assistant",
                    color = IshaMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(22.dp))
                LoadingDots()
            }
        }
    }
}

@Composable
private fun LoadingDots() {
    val loop = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index ->
            val lift by loop.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(480, delayMillis = index * 140, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(lift)
                    .clip(CircleShape)
                    .background(IshaAccent)
            )
        }
    }
}
