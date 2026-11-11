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
import androidx.compose.material3.Button
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

/** Meetings of the current trip: speak and it is written down live; several can be kept per trip. */
@Composable
fun MeetingScreen() {
    val ctx = LocalContext.current
    val trip = Store.current
    var openId by rememberSaveable { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }

    fun append(id: String, said: String) = Store.edit { d -> d.copy(meetings = d.meetings.map { if (it.id == id) it.copy(raw = (it.raw.trimEnd() + " " + said).trim()) else it }) }
    val dictation = remember {
        Dictation(ctx.applicationContext, onFinal = { said -> if (openId.isNotEmpty()) append(openId, said) }, onPartial = { partial = it },
            fail = { msg -> recording = false; Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() })
    }
    DisposableEffect(Unit) { onDispose { dictation.stop() } }
    fun startRec() { Store.checkpoint(); recording = true; dictation.start() }
    fun stopRec() { recording = false; dictation.stop() }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec() else Toast.makeText(ctx, "마이크 권한을 허용해야 녹음할 수 있습니다", Toast.LENGTH_LONG).show()
    }

    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("진행 중인 외근이 없습니다", style = MaterialTheme.typography.titleMedium)
            Text("외근 탭에서 출발 보고를 먼저 작성하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val meetings = Store.data.meetings.filter { it.tripId == trip.id }.sortedBy { it.at }
    val m = meetings.firstOrNull { it.id == openId }

    if (m == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val id = newId()
                Store.update { d -> d.copy(meetings = d.meetings + Meeting(id, trip.id, "회의 ${meetings.size + 1} · ${fTime(System.currentTimeMillis())}", System.currentTimeMillis())) }
                openId = id
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) { Text("회의 시작", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Text("${trip.place.ifBlank { "외근" }} 회의록 ${meetings.size}건", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (meetings.isEmpty()) Text("회의 시작을 누르고 녹음하면 말하는 내용이 바로 글로 기록됩니다. 한 외근에 회의록을 여러 개 만들 수 있습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            meetings.forEach { x ->
                SectionCard {
                    Column(Modifier.fillMaxWidth().clickable { openId = x.id }) {
                        Text(x.title, fontWeight = FontWeight.SemiBold)
                        Text("${fDate(x.at)} ${fTime(x.at)} · ${x.raw.length}자", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { stopRec(); openId = "" }) { Text("← 목록") }
            OutlinedTextField(m.title, { v -> Store.edit { d -> d.copy(meetings = d.meetings.map { if (it.id == m.id) it.copy(title = v) else it }) } }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp))
        }
        Button(onClick = { if (recording) stopRec() else askMic.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFFE2334C) else MaterialTheme.colorScheme.primary)) {
            Text(if (recording) "■  녹음 중 · 눌러서 완료" else "●  회의 녹음 시작", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (recording) partial.ifBlank { "듣고 있습니다…" } else "녹음한 내용은 아래에 쌓이고 직접 고칠 수 있습니다.", color = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        OutlinedTextField(m.raw, { v -> Store.edit { d -> d.copy(meetings = d.meetings.map { if (it.id == m.id) it.copy(raw = v) else it }) } }, Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text("회의 내용") }, shape = RoundedCornerShape(16.dp), readOnly = recording)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { shareText(ctx, "[${m.title}]\n${m.raw}") }) { Text("공유") }
            TextButton(onClick = { copyText(ctx, m.raw, "회의록을 복사했습니다") }) { Text("복사") }
            TextButton(onClick = { stopRec(); Store.update { d -> d.copy(meetings = d.meetings.filter { it.id != m.id }) }; openId = "" }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        }
    }
}
