package com.fieldreport.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
            viewing = p.id
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("현장 사진 ${photos.size}장", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(roots, key = { it.id }) { p ->
                Box(Modifier.aspectRatio(1f).clickable { viewing = p.id }) {
                    AsyncImage(File(dir, p.file), contentDescription = "현장 사진", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    val n = photos.count { it.parent == p.id }
                    if (n > 0) Text("세부 $n", Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color(0x99000000)).padding(horizontal = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        // The shutter sits at the bottom, within thumb reach; photos scroll above it.
        Button(onClick = { shoot(null) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) { Text("촬영", fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
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
