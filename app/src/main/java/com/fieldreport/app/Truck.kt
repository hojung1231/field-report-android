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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
/**
 * Cargo bed of a stock truck in mm and its payload in kg. Figures commonly quoted by Korean
 * freight services; they differ a little between sources and models, and open beds have no fixed
 * height, so [h] is a practical loading height. Used only to suggest a size.
 */
class Car(val name: String, val l: Int, val w: Int, val h: Int, val kg: Int)
val CAR_SPECS = listOf(
    Car("다마스", 1600, 1100, 1100, 400), Car("라보", 2200, 1400, 1300, 500), Car("1톤", 2850, 1600, 1800, 1000), Car("1.4톤", 3100, 1700, 1800, 1400),
    Car("2.5톤", 4300, 1800, 2000, 2500), Car("3.5톤", 4600, 2050, 2000, 3500), Car("5톤", 6200, 2300, 2300, 5000),
)
val CARS = CAR_SPECS.map { it.name }

fun Cargo.weightKg() = if (byPeople) people * KG_PER_PERSON else kg

/** Why [car] cannot take [cargos], or null if it can. */
fun carProblem(car: Car, cargos: List<Cargo>): String? {
    val total = cargos.sumOf { it.weightKg() }
    if (total > car.kg) return "중량 초과 (${total}kg > ${car.kg}kg)"
    cargos.forEach { c ->
        val long = maxOf(c.w, c.d); val short = minOf(c.w, c.d)
        if (long > car.l || short > car.w) return "바닥 크기 초과"
        if (c.h > car.h) return "높이 초과"
    }
    // Several items: leave some slack because boxes never pack perfectly.
    if (cargos.size > 1 && cargos.sumOf { it.w.toLong() * it.d } > car.l.toLong() * car.w * 85 / 100) return "여러 제품을 함께 싣기에 바닥이 부족"
    return null
}
fun recommendCar(cargos: List<Cargo>): Car? = CAR_SPECS.firstOrNull { carProblem(it, cargos) == null }
val CAUTIONS = listOf("우천 시 젖지 않도록 방수 필수", "운송 중 전도 및 파손 방지 필요", "위에 다른 화물 적재 금지", "지게차 상·하차 필요", "충격 주의")

data class Cargo(val item: String = "", val w: Int = 1000, val d: Int = 1000, val h: Int = 1000, val byPeople: Boolean = false, val kg: Int = 100, val people: Int = 2)
data class TruckForm(
    val at: Long = round30(System.currentTimeMillis() + 3_600_000), val car: String = "1톤",
    val from: Place = Place(""), val to: Place = Place(""),
    val added: List<Cargo> = emptyList(), val draft: Cargo = Cargo(),
    val cautions: List<String> = emptyList(), val note: String = "", val carManual: Boolean = false,
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
        VoiceField(p.name, { onChange(p.copy(name = it)) }, Modifier.fillMaxWidth(), label = { Text("업체명 (몇 글자만 쓰면 자동 완성)") }, singleLine = true)
        val q = p.name.replace(" ", "")
        if (q.isNotEmpty()) Store.data.places.filter { it.name.replace(" ", "").contains(q, true) && it != p }.take(4).forEach { s ->
            Column(Modifier.fillMaxWidth().clickable { onChange(s) }.padding(vertical = 6.dp)) {
                Text("${s.name} · ${s.person}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(s.addr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VoiceField(p.addr, { onChange(p.copy(addr = it)) }, Modifier.fillMaxWidth(), label = { Text("주소") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceField(p.person, { onChange(p.copy(person = it)) }, Modifier.weight(1f), label = { Text("담당자") }, singleLine = true)
            VoiceField(p.phone, { onChange(p.copy(phone = it)) }, Modifier.weight(1f), label = { Text("연락처") }, singleLine = true)
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

private val TRUCK_STEPS = listOf("일정", "상차지", "하차지", "제품", "차량", "주의", "확인")
private val TRUCK_QUESTIONS = listOf("언제 보낼까요?", "어디에서 싣나요?", "어디에 내리나요?", "무엇을 보내나요?", "어떤 차로 보낼까요?", "주의할 점이 있나요?", "이대로 보낼까요?")
private const val TRUCK_REVIEW = 6

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
        StepBar(s.step, TRUCK_STEPS, Modifier.fillMaxWidth())
        Row(Modifier.padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(TRUCK_QUESTIONS[s.step], Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { s.undo() }, enabled = s.canUndo) { Text("↶ 되돌리기") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (s.step) {
                0 -> {
                    DateRow(t.at) { d -> s.set(t.copy(at = t.at + d)) }
                    SectionCard { MiniStepper("출발", fTime(t.at), "−30분", "+30분", { s.set(t.copy(at = t.at - MIN30)) }, { s.set(t.copy(at = t.at + MIN30)) }) }
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
                    VoiceField(c.item, { cargo(c.copy(item = it)) }, Modifier.fillMaxWidth(), label = { Text(if (t.added.isEmpty()) "품목" else "제품 ${t.added.size + 1} 품목") }, singleLine = true)
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
                    val cs = t.cargos(); val rec = recommendCar(cs)
                    // Follow the recommendation until the user picks a car themselves.
                    LaunchedEffect(rec?.name, t.carManual) { if (!t.carManual && rec != null && t.car != rec.name) s.form = s.form.copy(car = rec.name) }
                    Text("제품 ${cs.size}개 · 총 약 ${n(cs.sumOf { it.weightKg() })}kg · 가장 큰 제품 ${cs.maxOf { maxOf(it.w, it.d) }.let { n(it) }}mm", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (rec == null) Text("5톤에도 싣기 어려운 크기·중량입니다. 업체와 전화로 상의하세요.", color = MaterialTheme.colorScheme.error)
                    CAR_SPECS.forEach { car ->
                        val problem = carProblem(car, cs); val picked = t.car == car.name
                        Surface(Modifier.fillMaxWidth().clickable { s.set(t.copy(car = car.name, carManual = true)) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            color = if (picked) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(car.name + if (car.name == rec?.name) "  · 추천" else "", fontWeight = FontWeight.Bold, color = if (car.name == rec?.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Text("적재함 ${n(car.l)}×${n(car.w)}mm · ${n(car.kg)}kg" + (problem?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (picked) Text("선택됨", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Text("적재함 크기와 적재중량은 용달 업체들이 흔히 안내하는 순정 차량 기준의 대략값입니다. 차종·연식에 따라 다를 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                5 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CAUTIONS.forEach { ca -> FilterChip(ca in t.cautions, { s.set(t.copy(cautions = if (ca in t.cautions) t.cautions - ca else t.cautions + ca)) }, label = { Text(ca) }) }
                    }
                    VoiceField(t.note, { s.set(t.copy(note = it)) }, Modifier.fillMaxWidth(), label = { Text("그 밖의 주의사항 (없으면 비워 두세요)") }, minLines = 2)
                }
                else -> {
                    val cs = t.cargos()
                    val rows = listOf(
                        "일정" to fKoreanDateTime(t.at),
                        "상차지" to listOf(t.from.name, t.from.person).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "미입력" },
                        "하차지" to listOf(t.to.name, t.to.person).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "미입력" },
                        "제품" to cs.joinToString(", ") { it.item.ifBlank { "품목 미입력" } } + " (${cs.size}개)",
                        "차량" to t.car + if (!t.carManual && recommendCar(cs)?.name == t.car) " (추천)" else "",
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
