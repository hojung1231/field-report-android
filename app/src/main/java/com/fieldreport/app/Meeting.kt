package com.fieldreport.app

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Meetings of the current trip. Recording and editing happen on a draft; a card is only created
 * or changed when the user presses save, so backing out never leaves an empty card behind.
 */
@Composable
fun MeetingScreen() {
    val ctx = LocalContext.current
    val trip = Store.current
    // "" = list, NEW = a meeting not saved yet, otherwise the id of the card being continued.
    var openId by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var raw by rememberSaveable { mutableStateOf("") }
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    var recording by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dictation = remember {
        Dictation(ctx.applicationContext, onFinal = { said -> raw = (raw.trimEnd() + " " + said).trim() }, onPartial = { partial = it },
            fail = { msg -> recording = false; Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() })
    }
    DisposableEffect(Unit) { onDispose { dictation.stop() } }
    fun stopRec() { recording = false; dictation.stop() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { recording = true; dictation.start() } else Toast.makeText(ctx, "마이크 권한을 허용해야 녹음할 수 있습니다", Toast.LENGTH_LONG).show()
    }

    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("진행 중인 외근이 없습니다", style = MaterialTheme.typography.titleMedium)
            Text("외근 탭에서 출발 보고를 먼저 작성하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val meetings = Store.data.meetings.filter { it.tripId == trip.id }
    val saved = meetings.firstOrNull { it.id == openId }
    fun open(m: Meeting?) {
        val now = System.currentTimeMillis()
        openId = m?.id ?: NEW; title = m?.title ?: "회의 ${meetings.size + 1} · ${fTime(now)}"; raw = m?.raw ?: ""; startedAt = m?.at ?: now
    }
    fun close() { stopRec(); openId = ""; confirmDiscard = false }
    val changed = if (saved != null) title != saved.title || raw != saved.raw else raw.isNotBlank()

    if (openId.isEmpty() || (openId != NEW && saved == null)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${trip.place.ifBlank { "외근" }} 회의록 ${meetings.size}건", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Cards scroll inside their own area so the start button stays put at the bottom, within thumb reach.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (meetings.isEmpty()) Text("아래 회의 시작을 누르고 녹음하면 말하는 내용이 바로 글로 기록됩니다. 저장을 눌러야 카드로 남습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                meetings.sortedByDescending { it.at }.forEach { x ->
                    androidx.compose.runtime.key(x.id) { SwipeRightToDelete(onDelete = { Store.update { d -> d.copy(meetings = d.meetings.filter { it.id != x.id }) } }) {
                        MeetingCard(x, listOf(
                            TicketAction("이어서 녹음", Icons.Filled.Mic) { open(x) },
                            TicketAction("공유", Icons.Filled.Share) { shareText(ctx, "[${x.title}]\n${x.raw}") },
                            TicketAction("복사", Icons.Filled.ContentCopy) { copyText(ctx, x.raw, "회의록을 복사했습니다") },
                            TicketAction("삭제", Icons.Filled.DeleteOutline, danger = true) { Store.update { d -> d.copy(meetings = d.meetings.filter { it.id != x.id }) } },
                        ))
                    } }
                }
            }
            Button(onClick = { open(null) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) { Text("회의 시작", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("회의 제목") }, singleLine = true, shape = RoundedCornerShape(14.dp))
        Button(onClick = { if (recording) stopRec() else askMic.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFFE2334C) else MaterialTheme.colorScheme.primary)) {
            Text(if (recording) "■  녹음 중 · 눌러서 멈춤" else "●  회의 녹음 시작", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (recording) partial.ifBlank { "듣고 있습니다…" } else "녹음한 내용은 아래에 쌓이고 직접 고칠 수 있습니다.", color = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        OutlinedTextField(raw, { raw = it }, Modifier.fillMaxWidth().weight(1f), placeholder = { Text("회의 내용") }, shape = RoundedCornerShape(16.dp), readOnly = recording)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { if (changed) { stopRec(); confirmDiscard = true } else close() }, modifier = Modifier.height(52.dp)) { Text("취소") }
            Button(onClick = {
                stopRec()
                val m = Meeting(saved?.id ?: newId(), trip.id, title.trim().ifBlank { "회의 · ${fTime(startedAt)}" }, startedAt, raw.trim(), if (saved != null && saved.raw == raw.trim()) saved.summary else "")
                Store.update { d -> d.copy(meetings = if (saved == null) d.meetings + m else d.meetings.map { if (it.id == m.id) m else it }) }
                close()
            }, enabled = raw.isNotBlank() || saved != null, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("저장", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("저장하지 않고 닫을까요?") }, text = { Text(if (saved == null) "녹음한 내용이 사라지고 카드가 만들어지지 않습니다." else "이번에 바꾼 내용이 사라집니다.") },
        confirmButton = { TextButton(onClick = { close() }) { Text("닫기", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("계속 작성") } })
}

private const val NEW = "new"
