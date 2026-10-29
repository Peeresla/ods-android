package io.github.peeresla.ods.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Контурная иконка: части (путь SVG в поле 24×24, акцентная ли деталь). */
class OutlineIcon(val parts: List<Pair<String, Boolean>>)

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

/** Пути из MENU_ICONS в app.js. <rect> и <circle> записаны путями. */
object Icons {
    val Train = OutlineIcon(
        listOf(
            "M8 3h9.5A2.5 2.5 0 0 1 20 5.5V16" to false,
            "M6.5 6h8a2.5 2.5 0 0 1 2.5 2.5v10a2.5 2.5 0 0 1-2.5 2.5h-8a2.5 2.5 0 0 1-2.5-2.5v-10a2.5 2.5 0 0 1 2.5-2.5z" to false,
            "M7.5 13.5l2.5 2.5 4-4.5" to true,
        ),
    )
    val Exam = OutlineIcon(
        listOf(
            circle(12f, 13.5f, 7.5f) to false,
            "M10 2.5h4M12 2.5V6M18.5 7l1.5-1.5" to false,
            "M12 10v3.5l2.5 2" to true,
        ),
    )
    val Mistakes = OutlineIcon(
        listOf(
            "M10.3 4.4 3 17.1A2 2 0 0 0 4.7 20h14.6a2 2 0 0 0 1.7-2.9L13.7 4.4a2 2 0 0 0-3.4 0z" to false,
            "M12 9.5v4M12 16.8h.01" to true,
        ),
    )
    val Scenarios = OutlineIcon(
        listOf(
            "M5 4h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-7l-4.5 3.5V17H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" to false,
            "M8 10.5h.01M12 10.5h.01M16 10.5h.01" to true,
        ),
    )
    val Docs = OutlineIcon(
        listOf(
            "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" to false,
            "M14 3v5h5" to false,
            "M8.5 12.5h7M8.5 16h5" to true,
        ),
    )
    val Settings = OutlineIcon(
        listOf(
            circle(12f, 12f, 3f) to true,
            "M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6" to false,
            circle(12f, 12f, 6.5f) to false,
        ),
    )

    /** Шестерёнка в шапке (index.html). */
    val Gear = OutlineIcon(
        listOf(
            circle(12f, 12f, 3f) to false,
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" to false,
        ),
    )

    /** Огонёк серии (STREAK_ICON). */
    val Streak = OutlineIcon(
        listOf(
            "M12 21.5c3.9 0 6.5-2.7 6.5-6.4 0-3.3-2-5.6-3.6-7.6-.5 1.8-1.4 3-2.6 3.6.2-3.2-1.3-5.9-3.6-8.1.2 3-1.4 5.1-2.8 7.1-.9 1.4-1.4 3-1.4 4.9 0 3.8 2.6 6.5 6.5 6.5z" to false,
            "M12 21.5c-1.7 0-2.8-1.2-2.8-2.8 0-1.8 1.4-2.9 2.3-4.3.9 1.1 3.3 2.4 3.3 4.4 0 1.5-1.1 2.7-2.8 2.7z" to false,
        ),
    )
}

fun parseSvgPath(d: String): Path = PathParser().parsePathString(d).toPath()

@Composable
fun IconView(icon: OutlineIcon, size: Dp, color: Color, accent: Color = color, modifier: Modifier = Modifier, strokeWidth: Float = 1.8f) {
    val paths = remember(icon) { icon.parts.map { (d, a) -> parseSvgPath(d) to a } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for ((p, isAccent) in paths) drawPath(p, if (isAccent) accent else color, style = stroke)
        }
    }
}
