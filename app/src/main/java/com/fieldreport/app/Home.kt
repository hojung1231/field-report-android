package com.fieldreport.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val trip = Store.current
    var editing by remember { mutableStateOf<Trip?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (trip == null) {
            Text("오늘은 어디로\n나가시나요?", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("출발 보고 작성") }
        } else {
            SectionCard {
                Text(if (trip.active) "외근 중 · ${fDate(trip.start)}" else "종료 · ${fDate(trip.start)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${fTime(trip.start)} → ${fTime(trip.end)}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(trip.place.ifBlank { "장소 미입력" }, fontWeight = FontWeight.SemiBold)
                Text("프로젝트 · ${trip.project}", color = MaterialTheme.colorScheme.primary)
                Text("목적  ${trip.purpose}")
                Text("인원  ${trip.people.ifBlank { "—" }}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shareText(ctx, notice(trip)) }, modifier = Modifier.weight(1f)) { Text("공지 공유") }
                    OutlinedButton(onClick = { copyText(ctx, notice(trip), "외근 공지를 복사했습니다") }) { Text("복사") }
                }
                if (Cloud.enabled) Button(onClick = { Toast.makeText(ctx, Cloud.sendReport(trip), Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                    Text("팀장에게 보고 공유" + (Store.data.managerUid?.let { " · " + Cloud.nameOf(it) } ?: ""))
                }
                Row {
                    TextButton(onClick = { editing = trip }) { Text("수정") }
                    TextButton(onClick = { Store.update { d -> d.copy(trips = d.trips.map { if (it.id == trip.id) it.copy(active = !it.active) else it }) } }) { Text(if (trip.active) "외근 종료" else "다시 진행") }
                    TextButton(onClick = { Store.update { d -> d.copy(trips = d.trips.filter { it.id != trip.id }, currentTrip = null) } }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
            }
            OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ 새 출발 보고") }
        }
        val others = Store.data.trips.filter { it.id != trip?.id }.sortedByDescending { it.start }
        if (others.isNotEmpty()) Text("내 외근 기록", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        others.forEach { t ->
            Surface(Modifier.fillMaxWidth().clickable { Store.update { it.copy(currentTrip = t.id) } }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("${t.place.ifBlank { "장소 미입력" }} · ${t.purpose}", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${t.project} · ${fDate(t.start)} ${fTime(t.start)} ~ ${fTime(t.end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (creating || editing != null) DepartureForm(editing) { creating = false; editing = null }
}
