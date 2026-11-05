package dev.jasper.ishav2.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

// The launcher icon is generated from the same path data by art/gen_logo_icon.py.
// Change a shape here, change it there too, then rerun the script.

/* ========================================================================= */
/*  Palette                                                                  */
/* ========================================================================= */

internal val LogoInk = Color(0xFF0A0A0B)
private val Ink = LogoInk
private val TileTop = Color(0xFF1D1D21)
private val TileBottom = Color(0xFF060607)
private val OrangeLight = Color(0xFFFFB866)
private val Orange = Color(0xFFFF8A33)
private val OrangeDeep = Color(0xFFF2561D)
private val Ember = Color(0xFFFFE3C2)
private val Skin = Color(0xFFFDE8DC)
private val SkinShade = Color(0xFFF6D3C2)

/** Path data is authored on the source icon's 1254 grid; we draw on a 1500 grid so glow has room. */
private const val ICON = 1254f
private const val CANVAS = 1500f
private const val INSET = (CANVAS - ICON) / 2f

/* ========================================================================= */
/*  Path data (traced from the logo)                                         */
/* ========================================================================= */

private object D {
    const val HAIR_OUTLINE = "M255,585 C270,420 330,300 450,235 C540,185 650,180 715,222 C730,212 745,210 760,214 C860,240 905,340 920,440 C935,520 945,570 950,620"
    const val LEFT_STRAND = "M355,548 C290,620 170,650 125,705 C95,745 110,785 145,805"
    const val BIG_SWIRL = "M248,688 C200,800 100,860 100,935 C100,1010 230,1050 555,1022"
    const val INNER_LOCK = "M405,752 C400,870 280,900 255,970 C245,1000 260,1020 275,1025"
    const val RIGHT_LOCK = "M885,575 C940,630 950,700 890,762"
    const val ORBIT = "M735,865 C950,760 1180,620 1195,470 C1205,390 1120,355 1022,358"
    const val COLLAR = "M440,912 C520,905 590,975 652,995 C700,960 745,925 790,915"
    const val SHOULDER = "M815,912 C900,905 950,960 962,1080"
    const val BANGS = "M400,588 C500,560 610,500 636,415"

    const val FACE = "M435,605 C470,585 560,560 620,520 C680,470 720,420 740,365 C760,420 800,480 835,552 L820,575 L805,690 C770,730 730,760 695,788 C620,780 540,750 490,690 C470,665 450,640 435,605 Z"
    const val EAR = "M440,603 C400,592 383,630 395,660 C405,685 445,692 490,690 Z"
    const val NECK = "M552,755 L548,820 C545,850 520,880 485,905 L650,990 L790,915 C730,905 690,880 685,860 L680,795 Z"
    const val CHEEK_STRAND = "M436,602 C450,650 490,690 532,692 C495,660 475,620 488,582 Z"

    const val EYE_LEFT = "M540,618 C560,595 612,580 637,610"
    const val LASH_LEFT = "M510,597 L545,604"
    const val EYE_RIGHT = "M736,604 C755,578 800,566 824,582"
    const val BROW_RIGHT = "M748,507 C770,488 792,490 802,498"

    const val SPARKLE = "M1040,425 C1045,475 1060,495 1110,500 C1060,505 1045,525 1040,575 C1035,525 1020,505 968,500 C1020,495 1035,475 1040,425 Z"
    val SPARKLE_C = Offset(1040f, 500f)
    val MINI_STARS = listOf(Offset(1150f, 760f), Offset(190f, 330f))
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

/* ========================================================================= */
/*  Tapered brush stroke                                                     */
/* ========================================================================= */

/**
 * Turns a centre-line into a calligraphic ribbon whose width tapers at both ends
 * (like the swooshes in the original logo). The ribbon can be revealed progressively
 * and any sub-range can be drawn (used for the orbit shimmer).
 */
private class BrushStroke(
    data: String,
    maxWidth: Float,
    startTaper: Float = 0.14f,
    endTaper: Float = 0.38f,
    minWidth: Float = 0.10f,
) {
    private val n = 120
    private val px = FloatArray(n + 1)
    private val py = FloatArray(n + 1)
    private val nx = FloatArray(n + 1)
    private val ny = FloatArray(n + 1)
    private val hw = FloatArray(n + 1) // half width
    private val ribbon = Path()

    init {
        val measure = PathMeasure().apply { setPath(data.toPath(), false) }
        val length = measure.length
        for (i in 0..n) {
            val u = i / n.toFloat()
            val d = length * u
            val p = measure.getPosition(d)
            val t = measure.getTangent(d)
            val l = hypot(t.x, t.y).coerceAtLeast(1e-4f)
            px[i] = p.x; py[i] = p.y
            nx[i] = -t.y / l; ny[i] = t.x / l
            val taper = easeSine(u / startTaper) * easeSine((1f - u) / endTaper)
            hw[i] = maxWidth * (minWidth + (1f - minWidth) * taper) / 2f
        }
    }

    private fun easeSine(x: Float) = if (x >= 1f) 1f else sin(x.coerceAtLeast(0f) * PI.toFloat() / 2f)

    private inline fun sample(f: Float, out: (x: Float, y: Float, nx: Float, ny: Float, w: Float) -> Unit) {
        val e = f.coerceIn(0f, 1f) * n
        val i0 = floor(e).toInt().coerceAtMost(n)
        val i1 = (i0 + 1).coerceAtMost(n)
        val a = e - i0
        out(
            lerp(px[i0], px[i1], a), lerp(py[i0], py[i1], a),
            lerp(nx[i0], nx[i1], a), lerp(ny[i0], ny[i1], a),
            lerp(hw[i0], hw[i1], a),
        )
    }

    fun headOf(f: Float): Offset {
        var o = Offset.Zero
        sample(f) { x, y, _, _, _ -> o = Offset(x, y) }
        return o
    }

    /** Draws the ribbon between [from] and [to] (0..1 along the stroke). */
    fun DrawScope.drawRange(from: Float, to: Float, brush: Brush, widthScale: Float = 1f, alpha: Float = 1f) {
        val a = from.coerceIn(0f, 1f)
        val b = to.coerceIn(0f, 1f)
        if (b - a <= 0.0005f) return
        val iA = floor(a * n).toInt() + 1
        val iB = floor(b * n).toInt()
        ribbon.reset()
        sample(a) { x, y, mx, my, w -> ribbon.moveTo(x + mx * w * widthScale, y + my * w * widthScale) }
        for (i in iA..iB) ribbon.lineTo(px[i] + nx[i] * hw[i] * widthScale, py[i] + ny[i] * hw[i] * widthScale)
        sample(b) { x, y, mx, my, w ->
            ribbon.lineTo(x + mx * w * widthScale, y + my * w * widthScale)
            ribbon.lineTo(x - mx * w * widthScale, y - my * w * widthScale)
        }
        for (i in iB downTo iA) ribbon.lineTo(px[i] - nx[i] * hw[i] * widthScale, py[i] - ny[i] * hw[i] * widthScale)
        sample(a) { x, y, mx, my, w -> ribbon.lineTo(x - mx * w * widthScale, y - my * w * widthScale) }
        ribbon.close()
        drawPath(ribbon, brush, alpha = alpha)
        // rounded nib at the moving head
        if (b < 1f) sample(b) { x, y, _, _, w -> drawCircle(brush, w * widthScale, Offset(x, y), alpha = alpha) }
    }

    /** Progressive reveal with a glowing "ink head" while drawing. */
    fun DrawScope.reveal(f: Float, brush: Brush, glowRadius: Float = 0f) {
        if (f <= 0f) return
        drawRange(0f, f, brush)
        if (glowRadius > 0f && f < 1f) {
            val head = headOf(f)
            val fade = sin(f * PI.toFloat()) // strongest mid-stroke
            drawCircle(
                Brush.radialGradient(listOf(Ember.copy(alpha = 0.9f * fade), Color.Transparent), head, glowRadius),
                glowRadius, head,
            )
        }
    }
}

private class LogoParts {
    val hairOutline = BrushStroke(D.HAIR_OUTLINE, 20f, 0.06f, 0.30f)
    val leftStrand = BrushStroke(D.LEFT_STRAND, 22f, 0.10f, 0.45f)
    val bigSwirl = BrushStroke(D.BIG_SWIRL, 44f, 0.12f, 0.55f)
    val innerLock = BrushStroke(D.INNER_LOCK, 22f, 0.10f, 0.40f)
    val rightLock = BrushStroke(D.RIGHT_LOCK, 22f, 0.25f, 0.45f)
    val orbit = BrushStroke(D.ORBIT, 34f, 0.30f, 0.40f)
    val collar = BrushStroke(D.COLLAR, 16f, 0.20f, 0.20f, 0.25f)
    val shoulder = BrushStroke(D.SHOULDER, 18f, 0.15f, 0.40f)
    val bangs = BrushStroke(D.BANGS, 16f, 0.10f, 0.35f)

    val eyeLeft = BrushStroke(D.EYE_LEFT, 14f, 0.25f, 0.30f, 0.3f)
    val lashLeft = BrushStroke(D.LASH_LEFT, 11f, 0.10f, 0.50f, 0.4f)
    val eyeRight = BrushStroke(D.EYE_RIGHT, 14f, 0.25f, 0.30f, 0.3f)
    val browRight = BrushStroke(D.BROW_RIGHT, 11f, 0.30f, 0.30f, 0.3f)

    val face = D.FACE.toPath()
    val ear = D.EAR.toPath()
    val neck = D.NECK.toPath()
    val cheekStrand = D.CHEEK_STRAND.toPath()
    val sparkle = D.SPARKLE.toPath()
    val revealClip = Path()

    val orangeBrush = Brush.linearGradient(
        0f to OrangeLight, 0.45f to Orange, 1f to OrangeDeep,
        start = Offset(150f, 150f), end = Offset(1150f, 1150f),
    )
    val inkBrush = SolidColor(Ink)
    val skinBrush = Brush.verticalGradient(listOf(Skin, SkinShade), startY = 380f, endY = 1000f)
    val tileBrush = Brush.radialGradient(listOf(TileTop, TileBottom), center = Offset(470f, 330f), radius = 1150f)
    val rimBrush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f), Color.Transparent),
        startY = 0f, endY = ICON,
    )
}

/* ========================================================================= */
/*  Motion curves                                                            */
/* ========================================================================= */

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val EaseOutExpo = Easing { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10f * t) }

/** Damped spring curve: overshoots and settles, like spring(dampingRatio≈0.5). */
private val SpringEasing = Easing { t -> if (t >= 1f) 1f else 1f - exp(-6.5f * t) * cos(11f * t) }

private fun phase(p: Float, start: Float, end: Float, easing: Easing = EaseInOutCubic): Float =
    easing.transform(((p - start) / (end - start)).coerceIn(0f, 1f))

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun wave(t: Float, period: Float, offset: Float = 0f) = sin(2f * PI.toFloat() * (t / period + offset))

/** A short pulse 0→1→0 that fires once every [period] seconds and lasts [length] seconds. */
private fun pulse(t: Float, period: Float, length: Float, offset: Float = 0f): Float {
    val local = ((t + offset) % period)
    return if (local < length) sin(PI.toFloat() * local / length) else 0f
}

/* ========================================================================= */
/*  Composable                                                               */
/* ========================================================================= */

/**
 * isha's animated logo.
 *
 * Intro (~3.2 s): the tile springs in with a 3D tilt and warm glow → hair is inked with
 * tapered brush strokes, each led by a glowing nib → the face is revealed by a soft circular
 * wipe → eyes, collar and hair clip land → the orbit sweeps around → the sparkle bursts in.
 *
 * Idle loop: floating + subtle parallax tilt, swaying hair, a shimmer gliding along the orbit,
 * twinkling sparkles and the occasional happy eye-squeeze.
 */
@Composable
fun AnimatedLogo(
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    durationMillis: Int = 3200,
    idleLoop: Boolean = true,
    replayKey: Any = Unit,
    onFinished: () -> Unit = {},
) {
    val parts = remember { LogoParts() }
    val progress = remember { Animatable(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(replayKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        finished()
    }
    // Continuous clock for idle motion; only invalidates drawing, never recomposes.
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val p = progress.value
                val idle = if (idleLoop) phase(p, 0.88f, 1f) else 0f
                val tiltIn = 1f - phase(p, 0f, 0.55f, EaseOutQuint)
                rotationY = -34f * tiltIn + 4f * wave(time, 6f) * idle
                rotationX = 14f * tiltIn + 3f * wave(time, 6f, 0.25f) * idle
                cameraDistance = 14f * density
            },
    ) {
        val p = progress.value
        val idle = if (idleLoop) phase(p, 0.88f, 1f) else 0f
        val s = this.size.minDimension / CANVAS
        withTransform({
            translate((this@Canvas.size.width - CANVAS * s) / 2f, (this@Canvas.size.height - CANVAS * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
            translate(INSET, INSET)
        }) {
            drawLogo(parts, p, idle, time)
        }
    }
}

private fun DrawScope.drawLogo(parts: LogoParts, p: Float, idle: Float, t: Float) = with(parts) {
    val center = Offset(ICON / 2f, ICON / 2f)

    /* --- Ambient glow behind the tile ------------------------------------ */
    val glow = phase(p, 0.10f, 0.60f, EaseOutQuint) * (0.85f + 0.15f * wave(t, 4f) * idle)
    if (glow > 0f) {
        val r = 820f
        drawCircle(
            Brush.radialGradient(listOf(Orange.copy(alpha = 0.32f * glow), Color.Transparent), Offset(640f, 700f), r),
            r, Offset(640f, 700f),
        )
    }

    /* --- Tile: springs in ------------------------------------------------- */
    val tile = phase(p, 0f, 0.30f, SpringEasing)
    if (tile <= 0f) return@with
    scale(lerp(0.55f, 1f, tile), pivot = center) {
        val a = phase(p, 0f, 0.08f, LinearEasing)
        drawRoundRect(tileBrush, Offset.Zero, Size(ICON, ICON), CornerRadius(230f), alpha = a)
        drawRoundRect(rimBrush, Offset(2f, 2f), Size(ICON - 4f, ICON - 4f), CornerRadius(228f), alpha = a, style = Stroke(4f))
    }

    val float = wave(t, 3.4f) * 9f * idle
    val sway = wave(t, 3.0f) * 1.8f * idle

    translate(top = float) {
        /* --- Face: soft circular wipe ------------------------------------- */
        val faceReveal = phase(p, 0.26f, 0.54f, EaseOutExpo)
        if (faceReveal > 0f) {
            revealClip.reset()
            val r = 430f * faceReveal
            revealClip.addOval(Rect(Offset(630f, 690f), r))
            clipPath(revealClip) {
                drawPath(ear, skinBrush)
                drawPath(face, skinBrush)
                drawPath(neck, skinBrush)
                drawPath(cheekStrand, Ink)
            }
        }

        /* --- Hair, inked with glowing nibs -------------------------------- */
        hairOutline.run { reveal(phase(p, 0.06f, 0.42f), orangeBrush, 60f) }
        rotate(sway, pivot = Offset(380f, 600f)) {
            leftStrand.run { reveal(phase(p, 0.13f, 0.44f), orangeBrush, 55f) }
            bigSwirl.run { reveal(phase(p, 0.17f, 0.54f), orangeBrush, 80f) }
            innerLock.run { reveal(phase(p, 0.23f, 0.55f), orangeBrush, 55f) }
        }
        rotate(-sway * 0.8f, pivot = Offset(885f, 575f)) {
            rightLock.run { reveal(phase(p, 0.28f, 0.50f), orangeBrush, 50f) }
        }
        bangs.run { reveal(phase(p, 0.38f, 0.56f), orangeBrush, 45f) }
        collar.run { reveal(phase(p, 0.42f, 0.62f), orangeBrush, 40f) }
        shoulder.run { reveal(phase(p, 0.46f, 0.66f), orangeBrush, 45f) }

        /* --- Eyes: inked, then the occasional happy squeeze ---------------- */
        val squeeze = pulse(t, 4.6f, 0.32f, 2.2f) * idle
        scale(1f, 1f - 0.4f * squeeze, pivot = Offset(580f, 600f)) {
            lashLeft.run { reveal(phase(p, 0.54f, 0.62f), inkBrush) }
            eyeLeft.run { reveal(phase(p, 0.56f, 0.70f), inkBrush) }
        }
        scale(1f, 1f - 0.4f * squeeze, pivot = Offset(780f, 585f)) {
            eyeRight.run { reveal(phase(p, 0.60f, 0.74f), inkBrush) }
        }
        browRight.run { reveal(phase(p, 0.58f, 0.68f), inkBrush) }

        /* --- Hair clip pops on with a spring ------------------------------ */
        clipBar(phase(p, 0.62f, 0.86f, SpringEasing), Offset(392f, 384f), Offset(468f, 414f))
        clipBar(phase(p, 0.66f, 0.90f, SpringEasing), Offset(382f, 428f), Offset(448f, 460f))
    }

    /* --- Orbit sweep + idle shimmer --------------------------------------- */
    rotate(wave(t, 5f) * 1.5f * idle, pivot = Offset(900f, 620f)) {
        orbit.run {
            reveal(phase(p, 0.52f, 0.86f), orangeBrush, 85f)
            if (idle > 0f) {
                val cycle = (t % 3.8f) / 1.6f
                if (cycle <= 1f) {
                    val head = lerp(-0.1f, 1.25f, EaseInOutCubic.transform(cycle))
                    drawRange(head - 0.22f, head, SolidColor(Ember), widthScale = 0.45f, alpha = 0.85f * idle)
                }
            }
        }
    }

    /* --- Sparkle burst ---------------------------------------------------- */
    val starIn = phase(p, 0.74f, 0.98f, SpringEasing)
    val burst = phase(p, 0.76f, 1.0f, EaseOutExpo)
    val c = D.SPARKLE_C
    translate(top = -float * 0.6f) {
        if (burst in 0.001f..0.999f) {
            for (i in 0 until 8) {
                val ang = (i * 45f + 22.5f) * PI.toFloat() / 180f
                val dist = 50f + 170f * burst
                drawCircle(
                    OrangeLight.copy(alpha = 1f - burst),
                    radius = 11f * (1f - burst) + 1f,
                    center = Offset(c.x + cos(ang) * dist, c.y + sin(ang) * dist),
                )
            }
        }
        if (starIn > 0f) {
            val tw = (wave(t, 2.2f) * 0.5f + 0.5f) * idle
            val g = 150f * starIn * (0.85f + 0.3f * tw)
            drawCircle(Brush.radialGradient(listOf(Orange.copy(alpha = 0.45f), Color.Transparent), c, g), g, c)
            rotate(lerp(-180f, 0f, starIn) + 20f * tw, pivot = c) {
                scale(starIn * (1f - 0.18f * tw), pivot = c) { drawPath(sparkle, orangeBrush) }
            }
        }
        // tiny companion sparkles: the main sparkle, shrunk, twinkling out of phase
        D.MINI_STARS.forEachIndexed { i, pos ->
            val appear = phase(p, 0.86f + i * 0.04f, 1f, SpringEasing)
            if (appear > 0f) {
                val twinkle = 1f - 0.35f * (wave(t, 1.7f + i * 0.6f, i * 0.37f) * 0.5f + 0.5f) * idle
                withTransform({
                    translate(pos.x - c.x, pos.y - c.y)
                    scale(MINI_STAR_SCALE * appear * twinkle, pivot = c)
                }) {
                    drawPath(sparkle, orangeBrush, alpha = 0.9f)
                }
            }
        }
    }
}

private const val MINI_STAR_SCALE = 0.32f

/** A rounded bar of the hair clip, popping in around its middle. */
private fun DrawScope.clipBar(amount: Float, from: Offset, to: Offset) {
    if (amount <= 0f) return
    val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
    scale(amount, pivot = mid) {
        drawLine(OrangeLight, from, to, strokeWidth = 22f, cap = StrokeCap.Round)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun AnimatedLogoPreview() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        AnimatedLogo()
    }
}
