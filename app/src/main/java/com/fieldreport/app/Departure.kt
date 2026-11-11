package com.fieldreport.app

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val QUESTIONS = listOf("어떤 프로젝트인가요?", "외근 목적은 무엇인가요?", "언제 다녀오시나요?", "어디로 가시나요?", "누가 가시나요?", "이대로 보고할까요?")
private const val REVIEW = 5

/** One question per screen; the last screen is a preview whose rows jump back to edit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepartureForm(edit: Trip?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember { round30(System.currentTimeMillis()) }
    var project by remember { mutableStateOf(edit?.project ?: "") }
    var purpose by remember { mutableStateOf(edit?.purpose ?: "") }
    var start by remember { mutableStateOf(edit?.start ?: now) }
    var end by remember { mutableStateOf(edit?.end ?: (now + 6 * MIN30)) }
    var place by remember { mutableStateOf(edit?.place ?: "") }
    var chosen by remember { mutableStateOf((edit?.people ?: Store.data.myName).split(",").map { it.trim() }.filter { it.isNotEmpty() }) }
    var typedName by remember { mutableStateOf("") }
    val people = chosen.joinToString(", ")
    var step by remember { mutableIntStateOf(if (edit == null) 0 else REVIEW) }
    var fromReview by remember { mutableStateOf(edit != null) }

    fun next() {
        if (step == 1 && purpose.isBlank()) { Toast.makeText(ctx, "목적을 입력해 주세요", Toast.LENGTH_SHORT).show(); return }
        step = if (fromReview) REVIEW else step + 1
        if (step == REVIEW) fromReview = true
    }
    fun submit() {
        if (purpose.isBlank()) { step = 1; return }
        val t = Trip(edit?.id ?: newId(), project.trim().ifBlank { "기타" }, purpose.trim(), start, end, place.trim(), people.trim(), edit?.active ?: true)
        Store.update { d ->
            d.copy(
                trips = if (edit == null) d.trips + t else d.trips.map { if (it.id == t.id) t else it },
                currentTrip = t.id, myName = d.myName.ifBlank { chosen.firstOrNull() ?: "" },
                projects = if (t.project != "기타" && t.project !in d.projects) d.projects + t.project else d.projects,
            )
        }
        onClose()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                StepBar(step, listOf("프로젝트", "목적", "일시", "장소", "인원", "확인"), Modifier.fillMaxWidth()) { i -> if (i < step || fromReview) step = i }
                Row(Modifier.padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(QUESTIONS[step], Modifier.weight(1f), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (step) {
                        0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Mine first, by how often I used each; then everything the team has entered.
                            val used = Store.data.trips.groupingBy { it.project }.eachCount()
                            val all = (Store.data.projects + DEFAULT_PROJECTS).distinct().filter { it != "기타" }.sortedWith(compareByDescending<String> { used[it] ?: 0 }.thenBy { it }) + "기타"
                            val q = project.trim()
                            val suggestion = if (q.isNotEmpty() && q !in all) all.firstOrNull { it.startsWith(q, true) } ?: all.firstOrNull { it != "기타" && it.contains(q, true) } else null
                            VoiceField(project, { project = it }, Modifier.fillMaxWidth(), label = { Text("프로젝트명") }, singleLine = true, textStyle = MaterialTheme.typography.titleMedium)
                            if (suggestion != null) Surface(Modifier.fillMaxWidth().clickable { project = suggestion }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("추천", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(suggestion, Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.SemiBold)
                                    Text("눌러서 입력", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            val shown = if (q.isEmpty() || q in all) all else all.filter { it.contains(q, true) }.ifEmpty { all }
                            // Two rows that continue off the right edge; drag sideways for more.
                            Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(shown.filterIndexed { i, _ -> i % 2 == 0 }, shown.filterIndexed { i, _ -> i % 2 == 1 }).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { p -> FilterChip(selected = project == p, onClick = { project = if (project == p) "" else p }, label = { Text(p, Modifier.padding(vertical = 6.dp)) }) }
                                    }
                                }
                            }
                        }
                        1 -> AutoFocusField(purpose, { purpose = it }, "예: 고객사 참관 T/O", lines = 5)
                        2 -> WhenStep(start, end) { s, e -> start = s; end = e }
                        3 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            AutoFocusField(place, { place = it }, "예: 아산공장", lines = 1)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Store.data.trips.map { it.place }.filter { it.isNotBlank() }.distinct().takeLast(6).forEach { p -> FilterChip(place == p, { place = p }, label = { Text(p) }) }
                            }
                        }
                        4 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            fun add(n: String) { val v = n.trim(); if (v.isNotEmpty() && v !in chosen) chosen = chosen + v }
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("외근자 ${chosen.size}명", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    if (chosen.isEmpty()) Text("아래에서 이름을 누르면 여기로 올라옵니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        chosen.forEach { n -> FilterChip(selected = true, onClick = { chosen = chosen - n }, label = { Text("$n  ✕", Modifier.padding(vertical = 6.dp)) }) }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VoiceField(typedName, { typedName = it }, Modifier.weight(1f), label = { Text("직접 입력") }, singleLine = true)
                                OutlinedButton(onClick = { add(typedName); typedName = "" }, enabled = typedName.isNotBlank()) { Text("추가") }
                            }
                            val roster = (Store.data.team.map { it.name } + Store.data.myName).filter { it.isNotBlank() && it !in chosen }.distinct().sorted()
                            Text("팀원 (이름순)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (roster.isEmpty()) Text(if (Store.data.team.isEmpty()) "등록한 팀원이 없습니다. 팀 탭에서 팀원을 등록하면 여기에 나옵니다." else "모든 팀원이 외근자로 선택됐습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                roster.forEach { n -> FilterChip(selected = false, onClick = { add(n) }, label = { Text(n, Modifier.padding(vertical = 6.dp)) }) }
                            }
                        }
                        else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            val rows = listOf("프로젝트" to project.ifBlank { "기타" }, "목적" to purpose, "일시" to "${fDate(start)} ${fTime(start)} ~ ${fTime(end)} (${fDuration(end - start)})", "장소" to place.ifBlank { "미입력" }, "인원" to people.ifBlank { "미입력" })
                            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                                Column {
                                    rows.forEachIndexed { i, (label, value) ->
                                        Row(Modifier.fillMaxWidth().clickable { step = i }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text("수정", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                        }
                                        if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                    }
                                }
                            }
                            Text("항목을 누르면 그 항목만 고칠 수 있습니다.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step in 1 until REVIEW && !fromReview) OutlinedButton(onClick = { step-- }, modifier = Modifier.height(54.dp)) { Text("이전") }
                    Button(onClick = { if (step == REVIEW) submit() else next() }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (step == REVIEW) (if (edit == null) "출발 보고" else "수정 내용 저장") else if (fromReview) "완료" else "다음", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoFocusField(value: String, onChange: (String) -> Unit, hint: String, lines: Int) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    VoiceField(value, onChange, Modifier.fillMaxWidth().focusRequester(focus), placeholder = { Text(hint) }, singleLine = lines == 1, minLines = lines,
        textStyle = MaterialTheme.typography.titleLarge, shape = RoundedCornerShape(18.dp))
}

/** Date on its own line; start and end joined by a timeline so the two times cannot be mistaken for each other. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenStep(start: Long, end: Long, onChange: (Long, Long) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary; val faint = MaterialTheme.colorScheme.outlineVariant
    val day = daysFromToday(start)
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(start - DAY, end - DAY) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "하루 전") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fDate(start), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(when (day) { 0L -> "오늘"; 1L -> "내일"; 2L -> "모레"; -1L -> "어제"; else -> if (day > 0) "${day}일 뒤" else "${-day}일 전" }, style = MaterialTheme.typography.labelMedium, color = primary)
            }
            IconButton(onClick = { onChange(start + DAY, end + DAY) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "하루 뒤") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            listOf("오늘" to 0L, "내일" to 1L, "모레" to 2L).forEach { (l, n) -> FilterChip(day == n, { val d = (n - day) * DAY; onChange(start + d, end + d) }, label = { Text(l) }) }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(Modifier.height(IntrinsicSize.Min).padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 16.dp)) {
                Canvas(Modifier.width(18.dp).fillMaxHeight()) {
                    val x = size.width / 2; val top = 34.dp.toPx(); val bottom = size.height - 30.dp.toPx()
                    drawLine(faint, Offset(x, top), Offset(x, bottom), strokeWidth = 3.dp.toPx())
                    drawCircle(primary, 7.dp.toPx(), Offset(x, top))
                    drawCircle(primary, 6.dp.toPx(), Offset(x, bottom), style = Stroke(3.dp.toPx()))
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    TimeRow("출발", fTime(start), 40, FontWeight.Bold, primary, { onChange(start - MIN30, end - MIN30) }, { onChange(start + MIN30, end + MIN30) })
                    Surface(Modifier.padding(vertical = 10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(fDuration(end - start) + " 동안", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    TimeRow("종료", fTime(end), 30, FontWeight.Medium, MaterialTheme.colorScheme.onSurface, { onChange(start, maxOf(start + MIN30, end - MIN30)) }, { onChange(start, end + MIN30) })
                }
            }
        }
        Text("외근 시간 바로 정하기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 3, 4, 6, 8).forEach { h -> FilterChip(end - start == h * 2 * MIN30, { onChange(start, start + h * 2 * MIN30) }, label = { Text("${h}시간") }) }
        }
    }
}

@Composable
private fun TimeRow(label: String, time: String, size: Int, weight: FontWeight, color: androidx.compose.ui.graphics.Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time, fontSize = size.sp, fontWeight = weight, color = color)
        }
        FilledTonalButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("−30분") }
        Spacer(Modifier.width(6.dp))
        FilledTonalButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+30분") }
    }
}
