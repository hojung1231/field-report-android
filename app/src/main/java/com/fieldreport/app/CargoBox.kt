package com.fieldreport.app

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import java.text.NumberFormat
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// The drawing uses a fixed 320 x 250 design space, scaled to the canvas.
private const val VW = 320f
private const val VH = 250f
private const val ISO = .866f
private const val OX = 140f
private const val OY = 212f
private const val PERSON_MM = 1700f

/** Design units per millimetre so the box, and a 170 cm person beside it, both fit. */
fun cargoBoxScale(w: Int, d: Int, h: Int): Float = minOf(100f / (ISO * max(w, d)), 165f / ((w + d) / 2f + max(h.toFloat(), PERSON_MM)), 165f / PERSON_MM)

/** Isometric projection: x runs up-right, y up-left, z straight up, from the front bottom corner. */
private fun project(x: Float, y: Float, z: Float, s: Float) = Offset(OX + (x - y) * ISO * s, OY - (x + y) * .5f * s - z * s)

/** New size after dragging one handle by ([dx], [dy]) design units, snapped to 50 mm. */
fun dragCargoSize(axis: Char, start: Int, dx: Float, dy: Float, scale: Float): Int {
    val dirX = when (axis) { 'w' -> ISO * scale; 'd' -> -ISO * scale; else -> 0f }
    val dirY = if (axis == 'h') -scale else -.5f * scale
    val mm = (dx * dirX + dy * dirY) / (dirX * dirX + dirY * dirY)
    return (((start + mm) / 50f).roundToInt() * 50).coerceIn(50, 12000)
}

/**
 * A box in 3-D whose width, depth and height are set by dragging the handle on each corner.
 * A 170 cm figure stands beside it, feet level with the box's front bottom corner, for scale.
 */
@Composable
fun CargoBox(cargo: Cargo, onDragStart: () -> Unit, onChange: (Cargo) -> Unit) {
    val c = MaterialTheme.colorScheme
    val current by rememberUpdatedState(cargo); val changed by rememberUpdatedState(onChange); val started by rememberUpdatedState(onDragStart)
    // While dragging, the scale is frozen so the handle stays under the finger; it refits on release.
    var frozen by remember { mutableStateOf<Float?>(null) }
    val s = frozen ?: cargoBoxScale(cargo.w, cargo.d, cargo.h)
    val ink = c.onSurface; val top = c.surface; val right = c.primary.copy(alpha = .18f); val left = c.outlineVariant; val accent = c.primary; val onAccent = c.onPrimary; val mute = c.onSurfaceVariant
    fun n(v: Int) = NumberFormat.getIntegerInstance().format(v)

    Canvas(Modifier.fillMaxWidth().aspectRatio(VW / VH).pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val k = size.width / VW; val p = down.position / k
            val now = current; val sc = cargoBoxScale(now.w, now.d, now.h)
            val handles = mapOf('w' to project(now.w.toFloat(), 0f, 0f, sc), 'd' to project(0f, now.d.toFloat(), 0f, sc), 'h' to project(0f, 0f, now.h.toFloat(), sc))
            val hit = handles.minByOrNull { (_, h) -> hypot(p.x - h.x, p.y - h.y) }?.takeIf { (_, h) -> hypot(p.x - h.x, p.y - h.y) < 42f }
            // Not on a handle: leave the touch alone so the page can still scroll.
            if (hit == null) return@awaitEachGesture
            down.consume(); started(); frozen = sc
            val axis = hit.key; val start = when (axis) { 'w' -> now.w; 'd' -> now.d; else -> now.h }
            var dx = 0f; var dy = 0f
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.positionChange(); dx += delta.x / k; dy += delta.y / k; change.consume()
                val v = dragCargoSize(axis, start, dx, dy, sc)
                changed(when (axis) { 'w' -> current.copy(w = v); 'd' -> current.copy(d = v); else -> current.copy(h = v) })
            }
            frozen = null
        }
    }) {
        val k = size.width / VW
        fun pt(x: Float, y: Float, z: Float) = project(x, y, z, s) * k
        val w = cargo.w.toFloat(); val d = cargo.d.toFloat(); val h = cargo.h.toFloat()
        val o = pt(0f, 0f, 0f); val x = pt(w, 0f, 0f); val y = pt(0f, d, 0f); val z = pt(0f, 0f, h)
        fun face(points: List<Offset>, fill: Color) {
            val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, fill); drawPath(path, ink, style = Stroke(1.5f * k))
        }
        face(listOf(z, pt(w, 0f, h), pt(w, d, h), pt(0f, d, h)), top)
        face(listOf(o, x, pt(w, 0f, h), z), right)
        face(listOf(o, y, pt(0f, d, h), z), left)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        fun label(at: Offset, s: String, size: Float, color: Color) { text.textSize = size * k; text.color = color.toArgb(); drawContext.canvas.nativeCanvas.drawText(s, at.x, at.y, text) }
        label(Offset((o.x + x.x) / 2 + 26 * k, (o.y + x.y) / 2 + 22 * k), n(cargo.w), 12f, ink)
        label(Offset((o.x + y.x) / 2 - 26 * k, (o.y + y.y) / 2 + 22 * k), n(cargo.d), 12f, ink)
        label(pt(w, d, h) + Offset(0f, -10 * k), n(cargo.h) + " mm", 12f, ink)
        listOf(x to "가로", y to "세로", z to "높이").forEach { (p, name) ->
            drawCircle(top, 16 * k, p); drawCircle(accent, 14 * k, p); label(p + Offset(0f, 3.5f * k), name, 10f, onAccent)
        }
        person(min(292f * k, x.x + 40 * k), o.y, PERSON_MM * s * k, mute)
        label(Offset(min(292f * k, x.x + 40 * k), o.y + 12 * k), "170cm", 10f, mute)
    }
}

/** A simple standing figure of height [h] with its feet at ([x], [y]). */
private fun DrawScope.person(x: Float, y: Float, h: Float, color: Color) {
    fun part(left: Float, top: Float, w: Float, hh: Float, r: Float) = drawRoundRect(color, Offset(x + left * h, y - top * h), Size(w * h, hh * h), CornerRadius(r * h))
    drawCircle(color, .07f * h, Offset(x, y - .93f * h))
    part(-.13f, .84f, .26f, .4f, .05f)   // torso
    part(-.2f, .82f, .06f, .36f, .03f)   // arms
    part(.14f, .82f, .06f, .36f, .03f)
    part(-.12f, .46f, .11f, .46f, .04f)  // legs
    part(.01f, .46f, .11f, .46f, .04f)
}
