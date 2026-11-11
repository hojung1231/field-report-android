package com.fieldreport.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

fun copyText(c: Context, text: String, done: String) {
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("외근 보고", text))
    Toast.makeText(c, done, Toast.LENGTH_SHORT).show()
}

fun shareText(c: Context, text: String) {
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "공유"))
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val selected = Store.current
    var editing by remember { mutableStateOf<Trip?>(null) }
    var creating by remember { mutableStateOf(false) }
    // The trip happening now comes first, then the rest from newest to oldest.
    val trips = Store.data.trips.sortedWith(compareByDescending<Trip> { it.active && now in it.start..it.end }.thenByDescending { it.start })
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (trips.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Text("오늘은 어디로\n나가시나요?", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
            Text("아래 버튼으로 출발 보고를 작성하세요.", color = c.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(14.dp).border(2.5.dp, c.primary, RoundedCornerShape(4.dp)))
                Text("테두리가 있는 외근에 회의와 사진이 저장됩니다", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            // Cards scroll inside their own area so the new-report button stays at the bottom.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                trips.forEach { trip ->
                    androidx.compose.runtime.key(trip.id) {
                        TripTicket(trip, listOfNotNull(
                            if (Cloud.enabled) TicketAction("팀장에게 보고", Icons.AutoMirrored.Filled.Send) { Toast.makeText(ctx, Cloud.sendReport(trip), Toast.LENGTH_SHORT).show() } else null,
                            TicketAction("공지 공유", Icons.Filled.Share) { shareText(ctx, notice(trip)) },
                            TicketAction("공지 복사", Icons.Filled.ContentCopy) { copyText(ctx, notice(trip), "외근 공지를 복사했습니다") },
                            TicketAction("수정", Icons.Filled.Edit) { editing = trip },
                            TicketAction(if (trip.active) "외근 종료" else "다시 진행", if (trip.active) Icons.Filled.Flag else Icons.Filled.Replay) { Store.update { d -> d.copy(trips = d.trips.map { if (it.id == trip.id) it.copy(active = !it.active) else it }) } },
                            TicketAction("삭제", Icons.Filled.DeleteOutline, danger = true) { Store.update { d -> d.copy(trips = d.trips.filter { it.id != trip.id }, currentTrip = if (d.currentTrip == trip.id) null else d.currentTrip) } },
                        ), selected = selected?.id == trip.id, onSelect = { Store.edit { it.copy(currentTrip = trip.id) } })
                    }
                }
                Text("누르면 선택 · 선택한 카드를 한 번 더 누르면 펼치기 · 길게 누르면 메뉴", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) { Text("새 출발 보고", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
    if (creating || editing != null) DepartureForm(editing) { creating = false; editing = null }
}
