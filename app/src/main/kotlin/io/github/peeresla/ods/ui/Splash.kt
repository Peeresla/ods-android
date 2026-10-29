package io.github.peeresla.ods.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Пути из app/index.html (поле 100×100)
private const val ROSE_LIGHT = "M50 50L50.00 6.00L47.13 43.07ZM50 50L69.80 30.20L52.87 43.07ZM50 50L94.00 50.00L56.93 47.13ZM50 50L69.80 69.80L56.93 52.87ZM50 50L50.00 94.00L52.87 56.93ZM50 50L30.20 69.80L47.13 56.93ZM50 50L6.00 50.00L43.07 52.87ZM50 50L30.20 30.20L43.07 47.13Z"
private const val ROSE_DARK = "M50 50L50.00 6.00L52.87 43.07ZM50 50L69.80 30.20L56.93 47.13ZM50 50L94.00 50.00L56.93 52.87ZM50 50L69.80 69.80L52.87 56.93ZM50 50L50.00 94.00L47.13 56.93ZM50 50L30.20 69.80L43.07 52.87ZM50 50L6.00 50.00L43.07 47.13ZM50 50L30.20 30.20L47.13 43.07Z"
private const val ROSE_STAR = "M50.00 6.00L52.87 43.07L69.80 30.20L56.93 47.13L94.00 50.00L56.93 52.87L69.80 69.80L52.87 56.93L50.00 94.00L47.13 56.93L30.20 69.80L43.07 52.87L6.00 50.00L43.07 47.13L30.20 30.20L47.13 43.07Z"
private const val ROSE_RING = "M50 17a33 33 0 1 1 0 66a33 33 0 1 1 0-66"

/** Минимальная длительность заставки (SPLASH_MIN_MS). */
const val SPLASH_MIN_MS = 4000L

/**
 * Заставка ~4 с: роза появляется и доворачивается → прорисовывается контур (кольцо, затем лучи)
 * → заполняется снизу вверх → надпись и свечение → роза «дышит». Тайминги — из styles.css.
 */
@Composable
fun Splash(modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    val turn = remember { Animatable(if (reduce) 1f else 0f) }
    val ring = remember { Animatable(if (reduce) 1f else 0f) }
    val star = remember { Animatable(if (reduce) 1f else 0f) }
    val fill = remember { Animatable(if (reduce) 1f else 0f) }
    val glow = remember { Animatable(if (reduce) 1f else 0f) }
    val title = remember { Animatable(if (reduce) 1f else 0f) }
    val progress = remember { Animatable(if (reduce) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        launch { appear.animateTo(1f, tween(700, easing = Easings.Out)) }
        launch { turn.animateTo(1f, tween(1800, delayMillis = 100, easing = Easings.Turn)) }
        launch { ring.animateTo(1f, tween(900, delayMillis = 250, easing = Easings.EaseInOut)) }
        launch { star.animateTo(1f, tween(1100, delayMillis = 550, easing = Easings.EaseInOut)) }
        launch { fill.animateTo(1f, tween(1500, delayMillis = 1300, easing = Easings.Fill)) }
        launch { glow.animateTo(1f, tween(900, delayMillis = 2400, easing = Easings.EaseOut)) }
        launch { title.animateTo(1f, tween(600, delayMillis = 2400, easing = Easings.EaseOut)) }
        launch { progress.animateTo(1f, tween(3700, delayMillis = 200, easing = Easings.Progress)) }
    }
    // «Дыхание» после 2,8 с
    val breathe = if (reduce) null else rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800, easing = Easings.EaseInOut), RepeatMode.Reverse, StartOffset(2800)),
        label = "breathe",
    )

    val paths = remember {
        RosePaths(parseSvgPath(ROSE_LIGHT), parseSvgPath(ROSE_DARK), parseSvgPath(ROSE_STAR), parseSvgPath(ROSE_RING))
    }

    Column(
        modifier
            .fillMaxSize()
            // Заставка перекрывает экран целиком: нажатия сквозь неё не проходят
            .semantics { contentDescription = "Заставка" }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0xFF1B4380),
                        0.6f to Color(0xFF12305E),
                        1f to Color(0xFF12305E),
                        center = Offset(size.width * 0.5f, size.height * 0.42f),
                        radius = Math.hypot(size.width * 0.5, size.height * 0.58).toFloat(), // farthest-corner
                    ),
                )
            },
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(140.dp)
                .graphicsLayer {
                    alpha = appear.value
                    val s = 0.6f + 0.4f * appear.value
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) {
            // Свечение: inset -30%, radial-gradient до 65 %
            Canvas(Modifier.requiredSize(224.dp).graphicsLayer { alpha = glow.value }) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0x73FF8A2A),
                        0.65f to Color(0x00FF8A2A),
                        center = center,
                        radius = size.minDimension / 2,
                    ),
                )
            }
            Canvas(
                Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        rotationZ = -60f * (1f - turn.value)
                        val b = breathe?.value ?: 1f
                        scaleX = b
                        scaleY = b
                    },
            ) {
                val k = size.minDimension / 100f
                scale(k, k, pivot = Offset.Zero) {
                    // Заполнение снизу вверх
                    clipRect(top = 100f * (1f - fill.value), bottom = 100f) {
                        drawPath(paths.light, Color(0xFFFF9A3D))
                        drawPath(paths.dark, Color(0xFFE0670F))
                    }
                    val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    // Едва заметный силуэт — видно, как роза появляется, ещё до прорисовки контура
                    drawPath(paths.star, Color.White.copy(alpha = 0.22f), style = stroke)
                    drawPath(paths.segment(paths.ring, ring.value), Color.White, style = stroke)
                    drawPath(paths.segment(paths.star, star.value), Color.White, style = stroke)
                }
            }
        }
        T(
            "Тренажёр МЧС",
            modifier = Modifier.graphicsLayer {
                alpha = 0.95f * title.value
                translationY = (1f - title.value) * 8.dp.toPx()
            },
            size = 19.sp,
            weight = FontWeight.SemiBold,
            color = Color.White,
            style = BaseText.copy(letterSpacing = 0.03.em),
        )
        Box(
            Modifier
                .width(140.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .drawBehind { drawRect(Color(0xFFFF8A2A), size = size.copy(width = size.width * progress.value)) },
        )
    }
}

private class RosePaths(val light: Path, val dark: Path, val star: Path, val ring: Path) {
    private val ringLen = PathMeasure().apply { setPath(ring, false) }.length
    private val starLen = PathMeasure().apply { setPath(star, false) }.length

    /** Прорисованная часть контура (stroke-dashoffset). */
    fun segment(path: Path, fraction: Float): Path {
        val out = Path()
        if (fraction <= 0f) return out
        if (fraction >= 1f) return path
        val m = PathMeasure()
        m.setPath(path, false)
        val len = if (path === ring) ringLen else starLen
        m.getSegment(0f, len * fraction, out, true)
        return out
    }
}
