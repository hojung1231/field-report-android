package com.fieldreport.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.abs

@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val trip = Store.current
    val dir = File(ctx.filesDir, "photos")
    var pendingFile by rememberSaveable { mutableStateOf("") }
    var pendingParent by rememberSaveable { mutableStateOf("") }
    var viewing by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("") }
    // Files no photo refers to any more (replaced edits, deleted photos) are removed once undo can no longer bring them back.
    LaunchedEffect(Unit) {
        if (Store.undoCount == 0) withContext(Dispatchers.IO) {
            val keep = Store.data.photos.flatMap { listOfNotNull(it.file, it.original) }.toSet(); val old = System.currentTimeMillis() - 10 * 60_000
            dir.listFiles()?.filter { it.name !in keep && it.lastModified() < old }?.forEach { it.delete() }
        }
    }

    // The system camera app takes the picture, so the phone's full camera pipeline is used.
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val t = Store.current
        if (ok && t != null && pendingFile.isNotEmpty()) {
            val p = Photo(newId(), t.id, pendingParent.ifEmpty { null }, pendingFile, System.currentTimeMillis())
            Store.update { it.copy(photos = it.photos + p) }
            selected = p.id
            // With an LLM connected, each new photo is checked for moulding conditions on a machine display.
            ConditionReader.read(p.id, dir)
        }
        pendingFile = ""
    }
    fun shoot(parent: String?) {
        dir.mkdirs()
        val f = File(dir, "${System.currentTimeMillis()}.jpg")
        pendingFile = f.name; pendingParent = parent ?: ""
        take.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f))
    }

    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("진행 중인 외근이 없습니다", style = MaterialTheme.typography.titleMedium)
            Text("외근 탭에서 출발 보고를 먼저 작성하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val photos = Store.data.photos.filter { it.tripId == trip.id }
    val roots = photos.filter { p -> p.parent == null || photos.none { it.id == p.parent } }
    val c = MaterialTheme.colorScheme
    fun kidsOf(id: String) = photos.filter { it.parent == id }.sortedBy { it.at }
    val sel = photos.firstOrNull { it.id == selected }
    // The top-level photo whose detail shots are laid out below it: the selected one, or the selected one's parent.
    val openRoot = sel?.let { s -> roots.firstOrNull { it.id == s.id } ?: roots.firstOrNull { it.id == s.parent } }
    fun tap(p: Photo) { if (selected == p.id) viewing = p.id else selected = p.id }
    fun delete(p: Photo) { Store.update { d -> d.copy(photos = d.photos.filter { it.id != p.id }) }; selected = "" }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("현장 사진 ${photos.size}장", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            Text("한 번 누르면 선택, 한 번 더 누르면 크게 보기", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
        if (ConditionReader.lastError.isNotBlank()) Text("사출조건 판독 실패: ${ConditionReader.lastError}", color = c.error, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            roots.sortedBy { it.at }.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { p -> Thumb(p, dir, selected == p.id, kidsOf(p.id).size, Modifier.weight(1f)) { tap(p) } }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
                val root = row.firstOrNull { it.id == openRoot?.id }
                if (root != null && sel != null) {
                    val kids = kidsOf(root.id); val col = row.indexOf(root); val line = c.primary
                    if (kids.isNotEmpty()) {
                        // Lines from the overview photo down to each of its detail photos.
                        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                            val w = size.width; val mid = size.height / 2; val px = (col + .5f) / 3f * w; val n = minOf(3, kids.size)
                            val xs = (0 until n).map { (it + .5f) / 3f * w }
                            drawLine(line, Offset(px, 0f), Offset(px, mid), strokeWidth = 2.dp.toPx())
                            drawLine(line, Offset(minOf(px, xs.first()), mid), Offset(maxOf(px, xs.last()), mid), strokeWidth = 2.dp.toPx())
                            xs.forEach { x -> drawLine(line, Offset(x, mid), Offset(x, size.height), strokeWidth = 2.dp.toPx()); drawCircle(line, 3.dp.toPx(), Offset(x, size.height - 3.dp.toPx())) }
                        }
                        kids.chunked(3).forEach { kr ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                kr.forEach { k -> Thumb(k, dir, selected == k.id, kidsOf(k.id).size, Modifier.weight(1f).padding(6.dp)) { tap(k) } }
                                repeat(3 - kr.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                    // Actions for the selected photo, shown as soon as it is outlined.
                    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), color = c.surface, shadowElevation = 3.dp) {
                        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PhotoAction("삭제", Icons.Filled.DeleteOutline, c.error) { delete(sel) }
                            PhotoAction("편집", Icons.Filled.Edit, c.onSurface) { editing = sel.id }
                            PhotoAction("세부 촬영", Icons.Filled.AddAPhoto, c.onSurface) { shoot(sel.id) }
                            PhotoAction(if (sel.cond) "조건 해제" else "사출조건", Icons.Filled.Speed, c.onSurface) { Store.update { d -> d.copy(photos = d.photos.map { if (it.id == sel.id) it.copy(cond = !it.cond) else it }) } }
                            if (Llm.configured) PhotoAction("AI 판독", Icons.Filled.AutoAwesome, c.primary) { ConditionReader.read(sel.id, dir) }
                        }
                    }
                }
            }
            if (photos.isEmpty()) Text("아래 촬영을 누르면 기본 카메라가 열립니다. 찍은 사진을 한 번 누르면 메뉴가, 한 번 더 누르면 크게 보기가 열립니다.", color = c.onSurfaceVariant)
        }
        // The shutter sits at the bottom, within thumb reach; photos scroll above it.
        Button(onClick = { shoot(null) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) { Text("촬영", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }

    val cur = photos.firstOrNull { it.id == viewing }
    if (cur != null) {
        val parent = photos.firstOrNull { it.id == cur.parent }
        val siblings = photos.filter { it.parent == parent?.id || (parent == null && it in roots) }.sortedBy { it.at }
        val kids = photos.filter { it.parent == cur.id }.sortedBy { it.at }
        val i = siblings.indexOfFirst { it.id == cur.id }
        var rank = 1; var up = parent; while (up != null && rank < 9) { rank++; val pid = up.parent; up = photos.firstOrNull { it.id == pid } }
        Dialog(onDismissRequest = { viewing = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewing = "" }) { Text("✕", color = Color.White) }
                    Text("순위 $rank · ${if (rank == 1) "전체 사진" else "세부 사진"}  ${i + 1}/${siblings.size}" + if (kids.isNotEmpty()) " · 세부 ${kids.size}장" else "", color = Color.White)
                }
                // Swipe: up = lower rank (detail), down = upper rank, right = earlier photo, left = later photo.
                Box(Modifier.weight(1f).fillMaxWidth().pointerInput(cur.id) {
                    var dx = 0f; var dy = 0f
                    detectDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDragEnd = {
                        val to = when {
                            abs(dx) > abs(dy) && dx > 80 -> siblings.getOrNull(i - 1)
                            abs(dx) > abs(dy) && dx < -80 -> siblings.getOrNull(i + 1)
                            abs(dy) >= abs(dx) && dy < -80 -> kids.firstOrNull()
                            abs(dy) >= abs(dx) && dy > 80 -> parent
                            else -> null
                        }
                        if (to != null) viewing = to.id
                    }) { _, d -> dx += d.x; dy += d.y }
                }) {
                    AsyncImage(File(dir, cur.file), contentDescription = "현장 사진", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Text("밀기: ↑ 세부 · ↓ 전체 · → 이전 · ← 다음", Modifier.fillMaxWidth().padding(6.dp), color = Color(0xB3FFFFFF), style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shoot(cur.id) }, modifier = Modifier.weight(1f)) { Text("세부 사진 촬영") }
                    OutlinedButton(onClick = { editing = cur.id }) { Text(if (cur.annotations.isEmpty()) "편집" else "편집 · 표시 ${cur.annotations.size}", color = Color.White) }
                    OutlinedButton(onClick = { Store.update { d -> d.copy(photos = d.photos.filter { it.id != cur.id }) }; viewing = parent?.id ?: "" }) { Text("삭제", color = Color.White) }
                }
            }
        }
    }
    Store.data.photos.firstOrNull { it.id == editing }?.let { p -> PhotoEditor(p, dir) { editing = "" } }
}

/** One square thumbnail: outlined when selected, with small marks for moulding conditions and detail shots. */
@Composable
private fun Thumb(p: Photo, dir: File, selected: Boolean, kids: Int, modifier: Modifier, onTap: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.border(3.5.dp, c.primary, RoundedCornerShape(10.dp)) else Modifier).clickable(onClick = onTap)) {
        AsyncImage(File(dir, p.file), contentDescription = "현장 사진", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (p.cond) Text("사출조건", Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0E9A68)).padding(horizontal = 5.dp, vertical = 1.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        else if (ConditionReader.busy[p.id] == true) Text("판독 중", Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp), color = Color.White, fontSize = 10.sp)
        if (kids > 0) Text("세부 $kids", Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp), color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun RowScope.PhotoAction(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}
