package com.fieldreport.app

import android.graphics.Paint
import android.graphics.Typeface
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Rough outline of mainland South Korea as (longitude, latitude). Line art, not a survey. */
private val KR = listOf(126.1 to 37.75, 126.68 to 37.95, 127.1 to 38.28, 128.0 to 38.32, 128.37 to 38.61, 128.62 to 38.05, 129.05 to 37.65, 129.35 to 37.1, 129.43 to 36.55, 129.57 to 36.02, 129.45 to 35.5, 129.2 to 35.15, 128.95 to 35.05, 128.6 to 34.85, 128.05 to 34.85, 127.75 to 34.75, 127.3 to 34.55, 126.75 to 34.35, 126.45 to 34.4, 126.15 to 34.75, 126.38 to 35.1, 126.45 to 35.6, 126.55 to 35.95, 126.5 to 36.35, 126.15 to 36.75, 126.5 to 36.98, 126.85 to 37.0, 126.6 to 37.4)
private val CITY = mapOf("서울" to (126.98 to 37.57), "인천" to (126.7 to 37.46), "수원" to (127.03 to 37.26), "화성" to (126.83 to 37.2), "평택" to (127.11 to 36.99), "안산" to (126.83 to 37.32), "안양" to (126.95 to 37.39), "성남" to (127.13 to 37.42), "용인" to (127.18 to 37.24), "이천" to (127.44 to 37.27), "파주" to (126.78 to 37.76), "김포" to (126.72 to 37.62), "부천" to (126.77 to 37.5), "시흥" to (126.8 to 37.38), "광명" to (126.86 to 37.48), "고양" to (126.83 to 37.66), "의정부" to (127.03 to 37.74), "남양주" to (127.22 to 37.64), "오산" to (127.08 to 37.15), "안성" to (127.28 to 37.01), "춘천" to (127.73 to 37.88), "원주" to (127.95 to 37.34), "강릉" to (128.88 to 37.75), "속초" to (128.59 to 38.21), "천안" to (127.15 to 36.81), "아산" to (127.0 to 36.79), "당진" to (126.63 to 36.89), "서산" to (126.45 to 36.78), "청주" to (127.49 to 36.64), "오창" to (127.42 to 36.71), "충주" to (127.93 to 36.99), "진천" to (127.44 to 36.86), "음성" to (127.69 to 36.94), "세종" to (127.29 to 36.48), "대전" to (127.38 to 36.35), "논산" to (127.1 to 36.19), "공주" to (127.12 to 36.45), "전주" to (127.15 to 35.82), "군산" to (126.74 to 35.97), "익산" to (126.95 to 35.95), "완주" to (127.16 to 35.9), "광주" to (126.85 to 35.16), "목포" to (126.39 to 34.81), "여수" to (127.66 to 34.76), "순천" to (127.49 to 34.95), "광양" to (127.7 to 34.94), "대구" to (128.6 to 35.87), "구미" to (128.34 to 36.12), "경산" to (128.74 to 35.83), "포항" to (129.34 to 36.02), "경주" to (129.22 to 35.86), "안동" to (128.73 to 36.57), "김천" to (128.11 to 36.14), "영천" to (128.94 to 35.97), "칠곡" to (128.4 to 35.99), "울산" to (129.31 to 35.54), "부산" to (129.08 to 35.18), "창원" to (128.68 to 35.23), "마산" to (128.57 to 35.21), "김해" to (128.89 to 35.23), "양산" to (129.04 to 35.34), "진주" to (128.11 to 35.18), "거제" to (128.62 to 34.88), "사천" to (128.06 to 35.0), "밀양" to (128.75 to 35.5), "제주" to (126.53 to 33.5), "서귀포" to (126.56 to 33.25))

/** The city a free-text place belongs to, by the longest city name it contains. */
fun cityOf(place: String): String? = CITY.keys.filter { place.contains(it) }.maxByOrNull { it.length }

@Composable
fun KoreaMap(pins: Map<String, List<String>>) {
    val land = MaterialTheme.colorScheme.surfaceVariant; val line = MaterialTheme.colorScheme.outline
    val ink = MaterialTheme.colorScheme.onSurface.toArgb(); val dot = Color(0xFF0E9A68)
    Canvas(Modifier.fillMaxWidth(0.8f).aspectRatio(260f / 420f)) {
        val k = size.width / 260f
        fun pt(lon: Double, lat: Double) = Offset(((lon - 125.6) * 58).toFloat() * k, ((38.85 - lat) * 72).toFloat() * k)
        val path = Path().apply { KR.forEachIndexed { i, (lo, la) -> val p = pt(lo, la); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
        drawPath(path, land); drawPath(path, line, style = Stroke(1.5f * k))
        val j = pt(126.55, 33.38)
        drawOval(land, Offset(j.x - 24 * k, j.y - 11 * k), Size(48 * k, 22 * k)); drawOval(line, Offset(j.x - 24 * k, j.y - 11 * k), Size(48 * k, 22 * k), style = Stroke(1.5f * k))
        val paint = Paint().apply { color = ink; textSize = 11f * k; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        pins.forEach { (city, names) ->
            val (lo, la) = CITY[city] ?: return@forEach; val p = pt(lo, la); val left = lo > 128.3
            drawCircle(dot.copy(alpha = .25f), 9 * k, p); drawCircle(dot, 4.5f * k, p)
            paint.textAlign = if (left) Paint.Align.RIGHT else Paint.Align.LEFT
            drawContext.canvas.nativeCanvas.drawText("$city · ${names.joinToString(", ")}", p.x + (if (left) -9 else 9) * k, p.y + 4 * k, paint)
        }
    }
}

@Composable
private fun Offline() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("서버에 연결되지 않았습니다", style = MaterialTheme.typography.titleMedium)
        Text("Firebase 설정 파일을 넣어 빌드하면 팀 기능이 켜집니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TeamScreen() {
    val ctx = LocalContext.current
    val d = Store.data
    val now = System.currentTimeMillis()
    var newName by remember { mutableStateOf("") }
    fun add(m: TeamMember) { val n = m.name.trim(); if (n.isNotEmpty() && d.team.none { it.name == n }) Store.update { it.copy(team = it.team + m.copy(name = n)) } }
    // Picking a phone row returns that contact's name and number without asking for the whole address book.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { uri ->
            runCatching {
                ctx.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { c -> if (c.moveToFirst()) add(TeamMember(c.getString(0) ?: "", c.getString(1) ?: "")) }
            }
        }
    }
    // Who is out: everyone's trips when the server is connected, otherwise only mine.
    val me = d.myName.ifBlank { "나" }
    val out: Map<String, Trip> = if (Cloud.enabled)
        Cloud.teamTrips.filter { it.trip.active && now < it.trip.end + DAY / 2 }.groupBy { Cloud.nameOf(it.owner) }.mapValues { e -> e.value.minBy { it.trip.start }.trip }
    else d.trips.filter { it.active && now < it.end + DAY / 2 }.minByOrNull { it.start }?.let { mapOf(me to it) } ?: emptyMap()
    val pins = out.entries.mapNotNull { (n, t) -> cityOf(t.place)?.let { it to n } }.groupBy({ it.first }, { it.second })
    val miss = out.filter { cityOf(it.value.place) == null }
    val roster = d.team.sortedBy { it.name }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            Text("지금 외근 중 ${out.size}명", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (!Cloud.enabled) Text("서버 연결 전이라 지금은 내 외근만 표시됩니다. 연결하면 팀원 위치가 함께 나옵니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { KoreaMap(pins) }
            if (miss.isNotEmpty()) Text("지도에 표시하지 못한 위치: " + miss.entries.joinToString(", ") { "${it.key}(${it.value.place.ifBlank { "장소 미입력" }})" } + ". 장소에 시·군 이름을 넣으면 표시됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("내 이름") {
            VoiceField(d.myName, { v -> Store.edit { it.copy(myName = v) } }, Modifier.fillMaxWidth(), placeholder = { Text("예: 이병화 선임") }, singleLine = true)
        }
        SectionCard("팀원 ${roster.size}명") {
            if (roster.isEmpty()) Text("아직 등록한 팀원이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            roster.forEach { m ->
                val t = out[m.name]; val isManager = d.managerName == m.name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (isManager) " · 팀장" else "", fontWeight = FontWeight.SemiBold)
                        Text(if (t != null) "${t.place.ifBlank { "장소 미입력" }} · ${fTime(t.start)}~${fTime(t.end)}" else m.phone.ifBlank { "외근 일정 없음" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { Store.update { it.copy(managerName = if (isManager) null else m.name) } }) { Text(if (isManager) "팀장 해제" else "팀장 지정") }
                    TextButton(onClick = { Store.update { x -> x.copy(team = x.team - m, managerName = if (isManager) null else x.managerName) } }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceField(newName, { newName = it }, Modifier.weight(1f), label = { Text("이름으로 추가") }, singleLine = true)
                Button(onClick = { add(TeamMember(newName)); newName = "" }, enabled = newName.isNotBlank()) { Text("추가") }
            }
            OutlinedButton(onClick = { runCatching { pick.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)) } }, modifier = Modifier.fillMaxWidth()) { Text("휴대폰 연락처에서 가져오기") }
            val online = Cloud.users.values.filter { n -> n.isNotBlank() && n != d.myName && d.team.none { it.name == n } }.distinct().sorted()
            if (online.isNotEmpty()) {
                Text("이 앱을 쓰고 있는 사람", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                online.forEach { n -> TextButton(onClick = { add(TeamMember(n)) }) { Text("+ $n") } }
            }
        }
    }
}

@Composable
fun InboxScreen() {
    if (!Cloud.enabled) { Offline(); return }
    var box by rememberSaveable { mutableIntStateOf(0) }
    val me = Cloud.uid
    val list = Cloud.reports.filter { if (box == 0) it.to == me else it.from == me }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow {
            listOf("받은 보고", "보낸 보고").forEachIndexed { i, l -> SegmentedButton(box == i, { box = i }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) } }
        }
        if (list.isEmpty()) Text(if (box == 0) "아직 받은 보고가 없습니다." else "아직 보낸 보고가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { r ->
            SectionCard {
                Row {
                    Text(Cloud.nameOf(if (box == 0) r.from else r.to), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("${fDate(r.sentAt)} ${fTime(r.sentAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(r.text)
                if (box == 1) TextButton(onClick = { Cloud.unsend(r.id) }) { Text("전송 취소", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
