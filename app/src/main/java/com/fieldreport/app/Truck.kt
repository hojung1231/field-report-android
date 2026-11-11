package com.fieldreport.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

const val VENDOR_NAME = "삼삼화물"
const val VENDOR_SMS = "010-9206-3355"
const val VENDOR_TEL = "052-283-3300"
const val KG_PER_PERSON = 20
val CARS = listOf("다마스", "라보", "1톤", "1.4톤", "2.5톤", "3.5톤", "5톤")
val CAUTIONS = listOf("우천 시 젖지 않도록 방수 필수", "운송 중 전도 및 파손 방지 필요", "위에 다른 화물 적재 금지", "지게차 상·하차 필요", "충격 주의")

data class Cargo(val item: String = "", val w: Int = 1000, val d: Int = 1000, val h: Int = 1000, val byPeople: Boolean = false, val kg: Int = 100, val people: Int = 2)
data class TruckForm(
    val at: Long = round30(System.currentTimeMillis() + 3_600_000), val car: String = "1톤",
    val from: Place = Place(""), val to: Place = Place(""),
    val added: List<Cargo> = emptyList(), val draft: Cargo = Cargo(),
    val cautions: List<String> = emptyList(), val note: String = "",
)

private fun n(v: Int) = NumberFormat.getIntegerInstance().format(v)
private fun cargoLines(c: Cargo) = "- 품목: ${c.item}\n- 크기: 가로 ${n(c.w)} × 세로 ${n(c.d)} × 높이 ${n(c.h)}mm\n- 중량: " +
    if (c.byPeople) "약 ${n(c.people * KG_PER_PERSON)}kg (성인 ${c.people}명이 들 수 있는 정도)" else "약 ${n(c.kg)}kg"

/** Cargo to send: everything added, plus the one being edited unless it is an untouched blank after an add. */
fun TruckForm.cargos(): List<Cargo> = if (added.isEmpty() || draft.item.isNotBlank()) added + draft else added

fun truckMessage(t: TruckForm): String {
    fun side(p: Place) = "- 업체명: ${p.name}\n" + (if (p.addr.isBlank()) "" else p.addr + "\n") + "- 담당자: ${p.person}\n- 연락처: ${p.phone}"
    val cs = t.cargos()
    val cargo = if (cs.size == 1) cargoLines(cs[0]) else cs.mapIndexed { i, c -> "[제품 ${i + 1}]\n${cargoLines(c)}" }.joinToString("\n")
    val ca = (t.cautions + t.note.trim()).filter { it.isNotBlank() }.joinToString(", ")
    return "1. 운송 일정\n- 출발일시: ${fKoreanDateTime(t.at)}\n- 차량: ${t.car} 화물차\n\n2. 상차지\n${side(t.from)}\n\n3. 하차지\n${side(t.to)}\n\n4. 운송 제품\n$cargo" +
        if (ca.isEmpty()) "" else "\n- 주의사항: $ca"
}

@Composable
private fun PlaceCard(title: String?, p: Place, onChange: (Place) -> Unit) {
    SectionCard(title) {
        TextButton(onClick = { onChange(p.copy(person = Store.data.myName.ifBlank { p.person }, phone = Store.data.myPhone.ifBlank { p.phone })) }) { Text("내가 담당자") }
        OutlinedTextField(p.name, { onChange(p.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("업체명 (몇 글자만 쓰면 자동 완성)") }, singleLine = true)
        val q = p.name.replace(" ", "")
        if (q.isNotEmpty()) Store.data.places.filter { it.name.replace(" ", "").contains(q, true) && it != p }.take(4).forEach { s ->
            Column(Modifier.fillMaxWidth().clickable { onChange(s) }.padding(vertical = 6.dp)) {
                Text("${s.name} · ${s.person}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(s.addr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(p.addr, { onChange(p.copy(addr = it)) }, Modifier.fillMaxWidth(), label = { Text("주소") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(p.person, { onChange(p.copy(person = it)) }, Modifier.weight(1f), label = { Text("담당자") }, singleLine = true)
            OutlinedTextField(p.phone, { onChange(p.copy(phone = it)) }, Modifier.weight(1f), label = { Text("연락처") }, singleLine = true)
        }
    }
}

/** Lives outside the screen so the draft and step survive switching tabs. */
object TruckState {
    var form by mutableStateOf(TruckForm())
    var step by mutableIntStateOf(0)
    var fromReview by mutableStateOf(false)
    var canUndo by mutableStateOf(false)
    private val history = ArrayDeque<TruckForm>()
    fun set(next: TruckForm) { history.addLast(form); if (history.size > 80) history.removeFirst(); form = next; canUndo = true }
    fun undo() { if (history.isNotEmpty()) form = history.removeLast(); canUndo = history.isNotEmpty() }
}

private val TRUCK_STEPS = listOf("일정", "상차지", "하차지", "제품", "주의사항", "확인")
private val TRUCK_QUESTIONS = listOf("언제, 어떤 차로 보낼까요?", "어디에서 싣나요?", "어디에 내리나요?", "무엇을 보내나요?", "주의할 점이 있나요?", "이대로 보낼까요?")
private const val TRUCK_REVIEW = 5

/** One question per screen, ending in a preview whose rows jump back to edit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TruckScreen() {
    val ctx = LocalContext.current
    val s = TruckState; val t = s.form; val c = t.draft
    fun cargo(next: Cargo) = s.set(t.copy(draft = next))
    fun rememberPlaces() {
        val seen = listOf(t.from, t.to).filter { it.name.isNotBlank() }
        Store.update { d -> d.copy(places = seen + d.places.filter { old -> seen.none { it.name == old.name && it.person == old.person } }) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepBar(s.step, TRUCK_STEPS, Modifier.weight(1f))
            TextButton(onClick = { s.undo() }, enabled = s.canUndo) { Text("↶ 되돌리기") }
        }
        Text(TRUCK_QUESTIONS[s.step], Modifier.padding(top = 14.dp, bottom = 14.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (s.step) {
                0 -> {
                    DateRow(t.at) { d -> s.set(t.copy(at = t.at + d)) }
                    SectionCard { MiniStepper("출발", fTime(t.at), "−30분", "+30분", { s.set(t.copy(at = t.at - MIN30)) }, { s.set(t.copy(at = t.at + MIN30)) }) }
                    Text("차량", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CARS.forEach { car -> FilterChip(t.car == car, { s.set(t.copy(car = car)) }, label = { Text(car) }) } }
                }
                1 -> PlaceCard(null, t.from) { s.set(t.copy(from = it)) }
                2 -> {
                    PlaceCard(null, t.to) { s.set(t.copy(to = it)) }
                    OutlinedButton(onClick = { s.set(t.copy(from = t.to, to = t.from)) }, modifier = Modifier.fillMaxWidth()) { Text("⇅ 상차지와 바꾸기") }
                }
                3 -> {
                    t.added.forEachIndexed { i, a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("제품 ${i + 1} · ${a.item.ifBlank { "품목 미입력" }} · ${n(a.w)}×${n(a.d)}×${n(a.h)}", Modifier.weight(1f))
                            TextButton(onClick = { s.set(t.copy(added = t.added - a)) }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    OutlinedTextField(c.item, { cargo(c.copy(item = it)) }, Modifier.fillMaxWidth(), label = { Text(if (t.added.isEmpty()) "품목" else "제품 ${t.added.size + 1} 품목") }, singleLine = true)
                    SectionCard("크기 (mm)") {
                        MiniStepper("가로", n(c.w), "−50", "+50", { cargo(c.copy(w = maxOf(50, c.w - 50))) }, { cargo(c.copy(w = c.w + 50)) })
                        MiniStepper("세로", n(c.d), "−50", "+50", { cargo(c.copy(d = maxOf(50, c.d - 50))) }, { cargo(c.copy(d = c.d + 50)) })
                        MiniStepper("높이", n(c.h), "−50", "+50", { cargo(c.copy(h = maxOf(50, c.h - 50))) }, { cargo(c.copy(h = c.h + 50)) })
                    }
                    SectionCard("중량") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(!c.byPeople, { cargo(c.copy(byPeople = false)) }, label = { Text("kg으로") })
                            FilterChip(c.byPeople, { cargo(c.copy(byPeople = true)) }, label = { Text("몇 명이 드는지") })
                        }
                        if (c.byPeople) MiniStepper("인원", "${c.people}명 · 약 ${n(c.people * KG_PER_PERSON)}kg", "−1", "+1", { cargo(c.copy(people = maxOf(1, c.people - 1))) }, { cargo(c.copy(people = c.people + 1)) })
                        else MiniStepper("무게", "약 ${n(c.kg)}kg", "−10", "+10", { cargo(c.copy(kg = maxOf(10, c.kg - 10))) }, { cargo(c.copy(kg = c.kg + 10)) })
                    }
                    OutlinedButton(onClick = { s.set(t.copy(added = t.added + c, draft = Cargo())) }, modifier = Modifier.fillMaxWidth()) { Text("+ 이 제품 추가하고 다음 제품 입력") }
                    Text("제품이 하나뿐이면 추가를 누르지 않고 다음으로 넘어가세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                4 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CAUTIONS.forEach { ca -> FilterChip(ca in t.cautions, { s.set(t.copy(cautions = if (ca in t.cautions) t.cautions - ca else t.cautions + ca)) }, label = { Text(ca) }) }
                    }
                    OutlinedTextField(t.note, { s.set(t.copy(note = it)) }, Modifier.fillMaxWidth(), label = { Text("그 밖의 주의사항 (없으면 비워 두세요)") }, minLines = 2)
                }
                else -> {
                    val cs = t.cargos()
                    val rows = listOf(
                        "일정" to "${fKoreanDateTime(t.at)} · ${t.car}",
                        "상차지" to listOf(t.from.name, t.from.person).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "미입력" },
                        "하차지" to listOf(t.to.name, t.to.person).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "미입력" },
                        "제품" to cs.joinToString(", ") { it.item.ifBlank { "품목 미입력" } } + " (${cs.size}개)",
                        "주의사항" to (t.cautions + t.note.trim()).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "없음" },
                    )
                    SectionCard {
                        rows.forEachIndexed { i, (label, value) ->
                            Row(Modifier.fillMaxWidth().clickable { s.fromReview = true; s.step = i }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, fontWeight = FontWeight.SemiBold)
                                }
                                Text("수정", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    SectionCard("$VENDOR_NAME 에 보낼 문자 · $VENDOR_SMS") { Text(truckMessage(t), style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { rememberPlaces(); copyText(ctx, truckMessage(t), "문자 내용을 복사했습니다") }, modifier = Modifier.weight(1f)) { Text("문자 내용 복사") }
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + VENDOR_TEL.replace("-", "")))) }, modifier = Modifier.weight(1f)) { Text("전화 $VENDOR_TEL") }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.step > 0 && !(s.fromReview && s.step < TRUCK_REVIEW)) OutlinedButton(onClick = { s.step-- ; if (s.step < TRUCK_REVIEW) s.fromReview = false }) { Text("이전") }
            Button(onClick = {
                if (s.step == TRUCK_REVIEW) {
                    rememberPlaces()
                    ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + VENDOR_SMS.replace("-", ""))).putExtra("sms_body", truckMessage(t)))
                } else s.step = if (s.fromReview) TRUCK_REVIEW else s.step + 1
            }, modifier = Modifier.weight(1f)) {
                Text(if (s.step == TRUCK_REVIEW) "${VENDOR_NAME}에 문자 보내기" else if (s.fromReview) "완료" else "다음", fontWeight = FontWeight.Bold)
            }
        }
    }
}
