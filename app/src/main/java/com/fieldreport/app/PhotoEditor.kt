package com.fieldreport.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SELECT = "select"
private val TOOLS = listOf(SELECT to "선택", KIND_CIRCLE to "원", KIND_RECTANGLE to "사각형", KIND_ARROW to "화살표")
private val COLORS = listOf("#ed4d4d", "#f3cd39", "#65c86f")
private val COLOR_NAMES = listOf("빨간색", "노란색", "초록색")
private fun parse(hex: String) = Color(android.graphics.Color.parseColor(hex))

private class Gesture(val id: String, val before: List<Annotation>, val initial: Annotation, val startX: Double, val startY: Double, val fresh: Boolean, val pendingPair: Boolean, val pair: TwoPointState) {
    var moved = false; var long = false; var two = false
}

private fun Context.activity(): Activity? { var c = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

/**
 * Annotates one photo with circles, rectangles and arrows, in this app's own look.
 * The drawing and selection behaviour follows the source editor; the layout does not.
 *
 * The first original is kept untouched; saving renders the shapes onto that original at full
 * resolution into a new file and stores the shape data beside it. Re-opening loads the original
 * with the stored shapes, so shapes are never doubled and quality does not drop with each save.
 */
@Composable
fun PhotoEditor(photo: Photo, dir: File, onClose: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val density = LocalDensity.current
    val source = photo.original ?: photo.file
    val sourceFile = remember(source) { File(dir, source) }
    val photoSize = remember(source) { PhotoRender.orientedSize(sourceFile) }

    var shapes by remember { mutableStateOf(renumber(photo.annotations)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(SELECT) }
    var color by remember { mutableStateOf(COLORS[0]) }
    var trash by remember { mutableStateOf(false) }
    var drop by remember { mutableStateOf(false) }
    var undoCount by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val history = remember { ArrayDeque<List<Annotation>>() }
    val gesture = remember { arrayOfNulls<Gesture>(1) }
    val hold = remember { arrayOfNulls<Job>(1) }
    val canvasOrigin = remember { arrayOf(Offset.Zero) }
    val trashBounds = remember { arrayOf(Rect.Zero) }
    val preview = remember { floatArrayOf(1f, 1f) } // canvas width and height in dp

    fun change(value: List<Annotation>) { shapes = renumber(value); dirty = true }
    fun checkpoint(value: List<Annotation>) { history.addLast(value); if (history.size > 30) history.removeFirst(); undoCount = history.size }
    fun leave() { if (saving) return; if (dirty) confirmClose = true else onClose() }

    DisposableEffect(Unit) { onDispose { hold[0]?.cancel() } }

    fun inTrash(root: Offset) = trashBounds[0].contains(root)

    fun begin(p: List<TouchPoint>) {
        if (saving || p.isEmpty()) return
        hold[0]?.cancel()
        val w = preview[0].toDouble(); val h = preview[1].toDouble()
        val existing = hitShape(shapes, p[0].x, p[0].y, w, h)
        val pendingPair = existing == null && tool == SELECT && selected != null
        var shape = existing ?: if (pendingPair) shapes.find { it.id == selected } else null
        var fresh = false
        val before = shapes
        if (shape == null && tool != SELECT) {
            // The first touch is the shape's start corner; for an arrow, its tail.
            shape = Annotation(newId(), shapes.size + 1, tool, color, p[0].x, p[0].y, clamp(p[0].x + .12), clamp(p[0].y + .12)); fresh = true
            change(shapes + shape)
            // After adding an object the tool returns to "select", so the next touch adjusts it.
            tool = SELECT
        }
        if (shape == null) { selected = null; return }
        selected = shape.id; color = shape.color
        val g = Gesture(shape.id, before, shape, p[0].x, p[0].y, fresh, pendingPair, TwoPointState(firstId = p[0].id)).also { it.two = p.size > 1 }
        gesture[0] = g
        if (p.size > 1) change(shapes.map { if (it.id == g.id) (if (fresh) resizeFromTouches(it, p, g.pair) else pinchFromTouches(it, p, g.pair, w, h)) else it })
        else if (!fresh && !pendingPair) hold[0] = scope.launch {
            delay(380)
            // Held in place: offer the trash.
            val cur = gesture[0]; if (cur === g && !cur.moved && !cur.two) { cur.long = true; trash = true }
        }
    }

    fun move(p: List<TouchPoint>, root: Offset) {
        val g = gesture[0] ?: return
        if (g.pair.finished) return
        if (g.two && p.size < 2) { g.pair.finished = true; return }
        if (p.isEmpty() || (g.pendingPair && p.size < 2)) return
        val w = preview[0].toDouble(); val h = preview[1].toDouble()
        if (!g.long && movedPastHold(g.startX, g.startY, p[0].x, p[0].y, w, h)) { g.moved = true; hold[0]?.cancel() }
        var shape = g.initial
        if (p.size > 1) {
            hold[0]?.cancel(); g.two = true; g.long = false; trash = false; drop = false
            val current = shapes.first { it.id == g.id }
            shape = if (g.fresh) resizeFromTouches(current, p, g.pair) else pinchFromTouches(current, p, g.pair, w, h)
        } else if (g.fresh) shape = shape.copy(x2 = p[0].x, y2 = p[0].y)
        else if (!g.two) shape = translateShape(shape, p[0].x - g.startX, p[0].y - g.startY)
        change(shapes.map { if (it.id == g.id) shape else it })
        if (g.long) drop = inTrash(root)
    }

    fun end(root: Offset) {
        hold[0]?.cancel()
        val g = gesture[0] ?: return
        if (g.pendingPair && !g.two) { selected = null; gesture[0] = null; return }
        val w = preview[0].toDouble(); val h = preview[1].toDouble()
        val inside = g.long && inTrash(root)
        checkpoint(g.before)
        if (inside) { change(shapes.filter { it.id != g.id }); selected = null }
        else if (g.fresh && !g.two) change(shapes.map { s ->
            // A plain tap makes a default-sized shape; a tapped arrow points up from its tail.
            if (s.id == g.id && hypot((s.x2 - s.x1) * w, (s.y2 - s.y1) * h) < 20) s.copy(x2 = clamp(s.x1 + .16), y2 = clamp(s.y1 + if (s.kind == KIND_ARROW) -.14 else .17)) else s
        })
        gesture[0] = null; trash = false; drop = false
    }

    fun save() {
        if (saving || preview[0] < 2f) return
        saving = true
        val final = renumber(shapes)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { if (final.isEmpty()) source else PhotoRender.render(sourceFile, final, preview[0], dir).name } }
            result.onSuccess { name ->
                Store.update { d -> d.copy(photos = d.photos.map { if (it.id == photo.id) it.copy(file = name, original = source, annotations = final) else it }) }
                onClose()
            }.onFailure { saving = false; Toast.makeText(ctx, "사진 저장: ${it.message ?: it}", Toast.LENGTH_LONG).show() }
        }
    }

    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = { leave() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(cs.background).safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { leave() }) { Icon(Icons.Filled.Close, contentDescription = "사진 편집 닫기") }
                    Column(Modifier.weight(1f)) {
                        Text("사진 편집", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("표시 ${shapes.size}개", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    TextButton(enabled = undoCount > 0 && !saving, onClick = { history.removeLastOrNull()?.let { change(it); undoCount = history.size; selected = null } }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" 되돌리기")
                    }
                    Button(onClick = { save() }, enabled = !saving, shape = RoundedCornerShape(14.dp)) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = cs.onPrimary, strokeWidth = 2.dp) else Text("저장", fontWeight = FontWeight.Bold)
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(cs.surfaceVariant).padding(8.dp), contentAlignment = Alignment.Center) {
                    val fit = min(maxWidth.value / photoSize.first, maxHeight.value / photoSize.second)
                    val wDp = max(1f, photoSize.first * fit); val hDp = max(1f, photoSize.second * fit)
                    preview[0] = wDp; preview[1] = hDp
                    Box(Modifier.size(wDp.dp, hDp.dp).background(Color.Black)
                        .onGloballyPositioned { canvasOrigin[0] = it.positionInRoot() }
                        .pointerInput(wDp, hDp) {
                            awaitPointerEventScope {
                                var active = false; var count = 0
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val pressed = ev.changes.filter { it.pressed }
                                    val pts = pressed.map { TouchPoint(it.id.value, clamp(it.position.x.toDouble() / size.width), clamp(it.position.y.toDouble() / size.height)) }
                                    val root = canvasOrigin[0] + (pressed.firstOrNull() ?: ev.changes.first()).position
                                    if (!active) { if (pts.isNotEmpty()) { active = true; count = pts.size; begin(pts) } }
                                    else if (pts.isEmpty()) { active = false; count = 0; end(root) }
                                    else {
                                        // A finger lifting ends the two-finger part at once, so size and position cannot jump.
                                        if (pts.size < count) { gesture[0]?.let { if (it.two && pts.size < 2) it.pair.finished = true } } else move(pts, root)
                                        count = pts.size
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        }) {
                        AsyncImage(sourceFile, contentDescription = "사진 편집 캔버스", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Canvas(Modifier.fillMaxSize()) {
                            val u = 1.dp.toPx(); val wPx = size.width; val hPx = size.height
                            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * u; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
                            shapes.forEach { s ->
                                val x1 = (s.x1 * wPx).toFloat(); val y1 = (s.y1 * hPx).toFloat(); val x2 = (s.x2 * wPx).toFloat(); val y2 = (s.y2 * hPx).toFloat()
                                val left = min(x1, x2); val top = min(y1, y2); val w = max(u, abs(x2 - x1)); val h = max(u, abs(y2 - y1)); val c = parse(s.color)
                                when (s.kind) {
                                    KIND_CIRCLE -> drawOval(c, Offset(left, top), Size(w, h), style = Stroke(3 * u))
                                    KIND_RECTANGLE -> drawRect(c, Offset(left, top), Size(w, h), style = Stroke(3 * u))
                                    else -> {
                                        drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = 3 * u)
                                        val angle = atan2(y2 - y1, x2 - x1); val head = 14 * u
                                        drawPath(Path().apply { moveTo(x2, y2); lineTo(x2 - head * cos(angle - .45f), y2 - head * sin(angle - .45f)); lineTo(x2 - head * cos(angle + .45f), y2 - head * sin(angle + .45f)); close() }, c)
                                    }
                                }
                                val cx = (left + 9 * u).coerceIn(13 * u, max(13 * u, wPx - 13 * u)); val cy = (top - 13 * u).coerceIn(13 * u, max(13 * u, hPx - 13 * u))
                                drawCircle(Color.White, 11 * u, Offset(cx, cy)); drawCircle(c, 11 * u, Offset(cx, cy), style = Stroke(2 * u))
                                text.color = c.toArgb(); drawContext.canvas.nativeCanvas.drawText(s.number.toString(), cx, cy + 4 * u, text)
                                if (selected == s.id) drawRect(Color.White, Offset(left - 5 * u, top - 5 * u), Size(w + 10 * u, h + 10 * u), style = Stroke(u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * u, 4 * u))))
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        TOOLS.forEachIndexed { i, (id, label) ->
                            SegmentedButton(selected = tool == id, onClick = { tool = id }, shape = SegmentedButtonDefaults.itemShape(i, TOOLS.size),
                                icon = { Icon(when (id) { SELECT -> Icons.Filled.PanTool; KIND_CIRCLE -> Icons.Filled.RadioButtonUnchecked; KIND_RECTANGLE -> Icons.Filled.CropSquare; else -> Icons.Filled.ArrowUpward }, contentDescription = null, modifier = Modifier.size(18.dp)) }) { Text(label, maxLines = 1) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        COLORS.forEachIndexed { i, c ->
                            Box(Modifier.size(30.dp).clip(CircleShape).background(parse(c)).border(if (color == c) 3.dp else 0.dp, if (color == c) cs.onSurface else Color.Transparent, CircleShape)
                                .clickable(onClickLabel = "${COLOR_NAMES[i]} 윤곽선") { color = c; selected?.let { id -> checkpoint(shapes); change(shapes.map { if (it.id == id) it.copy(color = c) else it }) } })
                        }
                        Text(when { selected != null -> "끌어서 이동 · 두 손가락으로 크기 조절 · 길게 눌러 삭제"; tool == SELECT -> "표시를 눌러 선택하거나 위에서 도형을 고르세요"; else -> "사진 위를 누른 채 끌면 그려집니다" },
                            Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (trash) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp).clip(RoundedCornerShape(18.dp))
                .background(if (drop) cs.error else cs.inverseSurface).onGloballyPositioned { trashBounds[0] = it.boundsInRoot() }.padding(horizontal = 26.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = if (drop) cs.onError else cs.inverseOnSurface)
                Text(if (drop) "놓으면 삭제" else "여기로 끌어 삭제", fontWeight = FontWeight.Bold, color = if (drop) cs.onError else cs.inverseOnSurface)
            }
        }
    }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { Text("편집 내용을 닫을까요?") }, text = { Text("사진의 편집 내용을 저장하지 않고 닫습니다.") },
        confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("닫기") } }, dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("계속 편집") } })
}
