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
import androidx.compose.runtime.mutableStateOf
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
private fun PlaceCard(title: String, p: Place, onChange: (Place) -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TruckScreen() {
    val ctx = LocalContext.current
    var t by remember { mutableStateOf(TruckForm()) }
    val history = remember { ArrayDeque<TruckForm>() }
    var canUndo by remember { mutableStateOf(false) }
    fun set(next: TruckForm) { history.addLast(t); if (history.size > 80) history.removeFirst(); t = next; canUndo = true }
    fun remember() {
        val seen = listOf(t.from, t.to).filter { it.name.isNotBlank() }
        Store.update { d -> d.copy(places = seen + d.places.filter { old -> seen.none { it.name == old.name && it.person == old.person } }) }
    }
    val c = t.draft
    fun cargo(next: Cargo) = set(t.copy(draft = next))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Row {
                Column(Modifier.weight(1f)) { Text(VENDOR_NAME, fontWeight = FontWeight.Bold); Text("문자 $VENDOR_SMS · 전화 $VENDOR_TEL", style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { if (history.isNotEmpty()) t = history.removeLast(); canUndo = history.isNotEmpty() }, enabled = canUndo) { Text("↶ 되돌리기") }
            }
        }
        SectionCard("1. 운송 일정") {
            Stepper("출발 날짜", fDate(t.at), "−1일", "+1일", { set(t.copy(at = t.at - DAY)) }, { set(t.copy(at = t.at + DAY)) })
            Stepper("출발 시간", fTime(t.at), "−30", "+30", { set(t.copy(at = t.at - MIN30)) }, { set(t.copy(at = t.at + MIN30)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CARS.forEach { car -> FilterChip(t.car == car, { set(t.copy(car = car)) }, label = { Text(car) }) } }
        }
        PlaceCard("2. 상차지", t.from) { set(t.copy(from = it)) }
        OutlinedButton(onClick = { set(t.copy(from = t.to, to = t.from)) }, modifier = Modifier.fillMaxWidth()) { Text("⇅ 상차지 · 하차지 바꾸기") }
        PlaceCard("3. 하차지", t.to) { set(t.copy(to = it)) }
        SectionCard("4. 운송 제품" + if (t.added.isNotEmpty()) " (${t.added.size}개 추가됨)" else "") {
            t.added.forEachIndexed { i, a ->
                Row {
                    Text("제품 ${i + 1} · ${a.item.ifBlank { "품목 미입력" }} · ${n(a.w)}×${n(a.d)}×${n(a.h)}", Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { set(t.copy(added = t.added - a)) }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
            }
            OutlinedTextField(c.item, { cargo(c.copy(item = it)) }, Modifier.fillMaxWidth(), label = { Text("품목") }, singleLine = true)
            Stepper("가로 mm", n(c.w), "−50", "+50", { cargo(c.copy(w = maxOf(50, c.w - 50))) }, { cargo(c.copy(w = c.w + 50)) })
            Stepper("세로 mm", n(c.d), "−50", "+50", { cargo(c.copy(d = maxOf(50, c.d - 50))) }, { cargo(c.copy(d = c.d + 50)) })
            Stepper("높이 mm", n(c.h), "−50", "+50", { cargo(c.copy(h = maxOf(50, c.h - 50))) }, { cargo(c.copy(h = c.h + 50)) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!c.byPeople, { cargo(c.copy(byPeople = false)) }, label = { Text("kg으로") })
                FilterChip(c.byPeople, { cargo(c.copy(byPeople = true)) }, label = { Text("몇 명이 드는지") })
            }
            if (c.byPeople) Stepper("약 ${n(c.people * KG_PER_PERSON)}kg · 1명당 ${KG_PER_PERSON}kg 기준", "${c.people}명", "−1명", "+1명", { cargo(c.copy(people = maxOf(1, c.people - 1))) }, { cargo(c.copy(people = c.people + 1)) })
            else Stepper("kg", "약 ${n(c.kg)}", "−10", "+10", { cargo(c.copy(kg = maxOf(10, c.kg - 10))) }, { cargo(c.copy(kg = c.kg + 10)) })
            OutlinedButton(onClick = { set(t.copy(added = t.added + c, draft = Cargo())) }, modifier = Modifier.fillMaxWidth()) { Text("+ 이 제품 추가하고 다음 제품 입력") }
            Text("제품이 하나뿐이면 추가를 누르지 않아도 됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CAUTIONS.forEach { ca -> FilterChip(ca in t.cautions, { set(t.copy(cautions = if (ca in t.cautions) t.cautions - ca else t.cautions + ca)) }, label = { Text(ca) }) }
            }
            OutlinedTextField(t.note, { set(t.copy(note = it)) }, Modifier.fillMaxWidth(), label = { Text("그 밖의 주의사항") })
        }
        val msg = truckMessage(t)
        SectionCard("보낼 문자") { Text(msg) }
        Button(onClick = {
            remember()
            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + VENDOR_SMS.replace("-", ""))).putExtra("sms_body", msg))
        }, modifier = Modifier.fillMaxWidth()) { Text("${VENDOR_NAME}에 문자 보내기") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { remember(); copyText(ctx, msg, "문자 내용을 복사했습니다") }, modifier = Modifier.weight(1f)) { Text("문자 내용 복사") }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + VENDOR_TEL.replace("-", "")))) }, modifier = Modifier.weight(1f)) { Text("전화 걸기") }
        }
    }
}
