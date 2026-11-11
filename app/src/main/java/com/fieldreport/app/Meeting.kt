package com.fieldreport.app

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Meetings of the current trip. There is no typing: the microphone button records, speech turns
 * into text as it is spoken, and stopping saves it as a card. Nothing spoken means no card.
 */
@Composable
fun MeetingScreen() {
    val ctx = LocalContext.current
    val trip = Store.current
    var recording by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf("") }   // id of the card being continued, or "" for a new meeting
    var spoken by remember { mutableStateOf("") }   // what this recording has captured so far
    var partial by remember { mutableStateOf("") }
    val liveScroll = rememberScrollState()

    val dictation = remember {
        Dictation(ctx.applicationContext, onFinal = { said -> spoken = (spoken + " " + said).trim() }, onPartial = { partial = it },
            fail = { msg -> recording = false; Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() })
    }
    /** Ends the recording; with [save] the captured text becomes a card or is added to the continued one. */
    fun finish(save: Boolean) {
        val text = spoken.trim(); val tripId = Store.current?.id; val into = target
        recording = false; dictation.stop(); spoken = ""; partial = ""; target = ""
        if (!save || text.isEmpty() || tripId == null) return
        val now = System.currentTimeMillis()
        val id = if (into.isNotEmpty() && Store.data.meetings.any { it.id == into }) into else newId()
        Store.update { d ->
            if (id == into) d.copy(meetings = d.meetings.map { if (it.id == id) it.copy(raw = (it.raw + " " + text).trim(), summary = "") else it })
            else d.copy(meetings = d.meetings + Meeting(id, tripId, "회의 ${d.meetings.count { it.tripId == tripId } + 1} · ${fTime(now)}", now, text))
        }
        Summarizer.summarize(id)
    }
    // Leaving the tab mid-recording keeps what was said.
    DisposableEffect(Unit) { onDispose { if (recording) finish(true) else dictation.stop() } }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { spoken = ""; partial = ""; recording = true; dictation.start() } else { target = ""; Toast.makeText(ctx, "마이크 권한을 허용해야 녹음할 수 있습니다", Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(spoken, partial) { liveScroll.scrollTo(liveScroll.maxValue) }

    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("진행 중인 외근이 없습니다", style = MaterialTheme.typography.titleMedium)
            Text("외근 탭에서 출발 보고를 먼저 작성하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val meetings = Store.data.meetings.filter { it.tripId == trip.id }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${trip.place.ifBlank { "외근" }} 회의록 ${meetings.size}건", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            Text(if (Llm.configured) "AI 요약 켜짐" else "길게 눌러 공유·삭제", style = MaterialTheme.typography.labelMedium, color = if (Llm.configured) c.primary else c.onSurfaceVariant)
        }
        if (Summarizer.lastError.isNotBlank()) Text("AI 요약 실패: ${Summarizer.lastError}", color = c.error, style = MaterialTheme.typography.bodySmall)
        // Cards scroll inside their own area so the microphone stays put at the bottom, within thumb reach.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (meetings.isEmpty() && !recording) Text("아래 마이크를 누르면 바로 녹음이 시작되고, 말하는 내용이 글로 바뀝니다. 한 번 더 누르면 회의 카드로 저장됩니다.", color = c.onSurfaceVariant)
            meetings.sortedByDescending { it.at }.forEach { x ->
                key(x.id) {
                    SwipeRightToDelete(onDelete = { Store.update { d -> d.copy(meetings = d.meetings.filter { it.id != x.id }) } }) {
                        MeetingCard(x, listOfNotNull(
                            TicketAction("이어서 녹음", Icons.Filled.Mic) { if (!recording) { target = x.id; askMic.launch(Manifest.permission.RECORD_AUDIO) } },
                            if (Llm.configured) TicketAction(if (x.summary.isBlank()) "AI로 요약" else "다시 요약", Icons.Filled.AutoAwesome) { Summarizer.summarize(x.id) } else null,
                            TicketAction("공유", Icons.Filled.Share) { shareText(ctx, "[${x.title}]\n${x.raw}") },
                            TicketAction("복사", Icons.Filled.ContentCopy) { copyText(ctx, x.raw, "회의록을 복사했습니다") },
                            TicketAction("삭제", Icons.Filled.DeleteOutline, danger = true) { Store.update { d -> d.copy(meetings = d.meetings.filter { it.id != x.id }) } },
                        ))
                    }
                }
            }
        }
        if (recording) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = c.primary.copy(alpha = .10f)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (target.isEmpty()) "녹음 중 · 새 회의" else "녹음 중 · ${meetings.firstOrNull { it.id == target }?.title ?: "회의"}에 이어서", color = Color(0xFFE2334C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(liveScroll)) {
                    Text(spoken.ifBlank { if (partial.isBlank()) "듣고 있습니다…" else "" }, fontSize = 15.sp, lineHeight = 22.sp)
                    if (partial.isNotBlank()) Text(partial, fontSize = 15.sp, lineHeight = 22.sp, color = c.primary)
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(if (recording) Color(0xFFE2334C) else c.primary)
                .clickable(onClickLabel = if (recording) "녹음 끝내고 저장" else "회의 녹음 시작") { if (recording) finish(true) else { target = ""; askMic.launch(Manifest.permission.RECORD_AUDIO) } }, contentAlignment = Alignment.Center) {
                Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            if (recording) TextButton(onClick = { finish(false) }, modifier = Modifier.align(Alignment.CenterEnd)) { Text("저장 안 함", color = c.error) }
        }
    }
}
