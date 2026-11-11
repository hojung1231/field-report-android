package com.fieldreport.app

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A mark drawn on a photo. Coordinates are fractions of the photo (0..1), so the same data
 * draws on the preview and on the full-resolution original. An arrow runs from its tail
 * (x1, y1), where the finger first touched, to its head (x2, y2).
 */
@Serializable
data class Annotation(val id: String, val number: Int, val kind: String, val color: String, val x1: Double, val y1: Double, val x2: Double, val y2: Double)

const val KIND_CIRCLE = "circle"
const val KIND_RECTANGLE = "rectangle"
const val KIND_ARROW = "arrow"

data class TouchPoint(val id: Long, val x: Double, val y: Double)
enum class PinchAxis { BOTH, HORIZONTAL, VERTICAL }
class PinchBaseline(val shape: Annotation, val distance: Double, val gapX: Double, val gapY: Double, val midX: Double, val midY: Double, val axis: PinchAxis)

/** Which two fingers a two-finger gesture is bound to, and where it started. */
class TwoPointState(var ids: Pair<Long, Long>? = null, var firstId: Long? = null, var finished: Boolean = false, var baseline: PinchBaseline? = null)

fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0): Double = max(lo, min(hi, v))

/** Numbers always run 1..n in list order, so deleting one pulls the later ones forward. */
fun renumber(shapes: List<Annotation>): List<Annotation> = shapes.mapIndexed { i, s -> s.copy(number = i + 1) }

/** Fingers on opposite sides of the shape along one axis stretch only that axis. */
fun pinchAxis(shape: Annotation, a: TouchPoint, b: TouchPoint): PinchAxis {
    fun region(v: Double, s: Double, e: Double) = if (v < min(s, e)) -1 else if (v > max(s, e)) 1 else 0
    val ax = region(a.x, shape.x1, shape.x2); val bx = region(b.x, shape.x1, shape.x2)
    val ay = region(a.y, shape.y1, shape.y2); val by = region(b.y, shape.y1, shape.y2)
    if (ay * by == -1 && ax == bx) return PinchAxis.VERTICAL
    if (ax * bx == -1 && ay == by) return PinchAxis.HORIZONTAL
    return PinchAxis.BOTH
}

/**
 * Scale and move an existing shape with two fingers. The shape keeps its geometry at the moment
 * the second finger lands; everything is measured from that baseline, and once either finger
 * lifts the gesture is finished, so the remaining finger cannot make the shape jump.
 */
fun pinchFromTouches(shape: Annotation, touches: List<TouchPoint>, state: TwoPointState, width: Double, height: Double): Annotation {
    if (state.finished) return shape
    if (state.ids == null && touches.size >= 2) state.ids = touches[0].id to touches[1].id
    val ids = state.ids ?: return shape
    val a = touches.find { it.id == ids.first }; val b = touches.find { it.id == ids.second }
    if (a == null || b == null) { state.finished = true; return shape }
    val distance = hypot((b.x - a.x) * width, (b.y - a.y) * height); val midX = (a.x + b.x) / 2; val midY = (a.y + b.y) / 2
    val initial = state.baseline ?: run {
        state.baseline = PinchBaseline(shape, max(1.0, distance), abs(b.x - a.x), abs(b.y - a.y), midX, midY, pinchAxis(shape, a, b))
        return shape
    }
    val s = initial.shape; val w = abs(s.x2 - s.x1); val h = abs(s.y2 - s.y1)
    val capW = if (w != 0.0) 1 / w else Double.POSITIVE_INFINITY; val capH = if (h != 0.0) 1 / h else Double.POSITIVE_INFINITY
    val scale = minOf(max(.02, distance / initial.distance), capW, capH)
    // The axis is locked from the starting regions for the whole gesture.
    val scaleX = when (initial.axis) { PinchAxis.VERTICAL -> 1.0; PinchAxis.HORIZONTAL -> min(max(.02, abs(b.x - a.x) / initial.gapX), capW); else -> scale }
    val scaleY = when (initial.axis) { PinchAxis.HORIZONTAL -> 1.0; PinchAxis.VERTICAL -> min(max(.02, abs(b.y - a.y) / initial.gapY), capH); else -> scale }
    var spanX = (s.x2 - s.x1) * scaleX; var spanY = (s.y2 - s.y1) * scaleY
    // A perfectly horizontal or vertical arrow has no span to multiply on one axis; open it from zero.
    if (s.kind == KIND_ARROW && initial.axis == PinchAxis.HORIZONTAL && w == 0.0) spanX = min(1.0, w + max(0.0, scaleX - 1) * max(h * height, 1.0) / width)
    if (s.kind == KIND_ARROW && initial.axis == PinchAxis.VERTICAL && h == 0.0) spanY = min(1.0, h + max(0.0, scaleY - 1) * max(w * width, 1.0) / height)
    val halfW = abs(spanX) / 2; val halfH = abs(spanY) / 2
    val cx = clamp((s.x1 + s.x2) / 2 + midX - initial.midX, halfW, 1 - halfW); val cy = clamp((s.y1 + s.y2) / 2 + midY - initial.midY, halfH, 1 - halfH)
    return s.copy(x1 = cx - spanX / 2, y1 = cy - spanY / 2, x2 = cx + spanX / 2, y2 = cy + spanY / 2)
}

/** A touch that drifts less than 8 units is still "held in place" for the long-press. */
fun movedPastHold(startX: Double, startY: Double, x: Double, y: Double, width: Double, height: Double): Boolean = hypot((x - startX) * width, (y - startY) * height) > 8

/** While a shape is being drawn, two fingers pin its two corners; each finger keeps its own corner. */
fun resizeFromTouches(shape: Annotation, touches: List<TouchPoint>, state: TwoPointState): Annotation {
    if (state.finished) return shape
    if (state.ids == null && touches.size >= 2) { val first = touches.find { it.id == state.firstId } ?: touches[0]; state.ids = first.id to touches.first { it.id != first.id }.id }
    val ids = state.ids ?: return shape
    val a = touches.find { it.id == ids.first }; val b = touches.find { it.id == ids.second }
    if (a == null || b == null) { state.finished = true; return shape }
    return shape.copy(x1 = a.x, y1 = a.y, x2 = b.x, y2 = b.y)
}

/** Topmost shape under a point: arrows by distance to the line, others by their padded box. */
fun hitShape(shapes: List<Annotation>, x: Double, y: Double, width: Double, height: Double): Annotation? = shapes.lastOrNull { shape ->
    val ax = shape.x1 * width; val ay = shape.y1 * height; val bx = shape.x2 * width; val by = shape.y2 * height; val px = x * width; val py = y * height
    if (shape.kind == KIND_ARROW) {
        val dx = bx - ax; val dy = by - ay; val len = dx * dx + dy * dy
        val t = clamp(((px - ax) * dx + (py - ay) * dy) / (if (len == 0.0) 1.0 else len))
        hypot(px - ax - t * dx, py - ay - t * dy) < 24
    } else px >= min(ax, bx) - 15 && px <= max(ax, bx) + 15 && py >= min(ay, by) - 15 && py <= max(ay, by) + 15
}

/** Move without leaving the photo and without changing size. */
fun translateShape(s: Annotation, dx: Double, dy: Double): Annotation {
    val x = clamp(dx, -min(s.x1, s.x2), 1 - max(s.x1, s.x2)); val y = clamp(dy, -min(s.y1, s.y2), 1 - max(s.y1, s.y2))
    return s.copy(x1 = s.x1 + x, x2 = s.x2 + x, y1 = s.y1 + y, y2 = s.y2 + y)
}
