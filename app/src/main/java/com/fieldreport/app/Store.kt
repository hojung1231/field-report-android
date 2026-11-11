package com.fieldreport.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@Serializable
data class Trip(
    val id: String, val project: String = "기타", val purpose: String = "", val start: Long, val end: Long,
    val place: String = "", val people: String = "", val active: Boolean = true, val addr: String = "",
)

/** A photo. `parent` links a detail shot (rank 2+) to the overview it belongs to. */
@Serializable
data class Photo(
    val id: String, val tripId: String, val parent: String? = null, val file: String, val at: Long, val memo: String = "",
    /** The untouched first original once the photo has been edited; [file] is then the rendered result. */
    val original: String? = null, val annotations: List<Annotation> = emptyList(),
    /** True when the photo shows moulding conditions on an injection machine's display. */
    val cond: Boolean = false,
)

@Serializable
data class Place(val name: String, val addr: String = "", val person: String = "", val phone: String = "")

@Serializable
data class TeamMember(val name: String, val phone: String = "")

@Serializable
data class Meeting(
    val id: String, val tripId: String, val title: String, val at: Long,
    /** The original speech-to-text record; never changed by the AI. */
    val raw: String = "",
    /** AI results kept beside the original: a summary of about 200 characters and a tidied full version. */
    val summary: String = "", val organized: String = "",
    /** Which version the card is showing. */
    val showAi: Boolean = false,
) {
    val hasAi: Boolean get() = summary.isNotBlank() || organized.isNotBlank()
}

/** What a collapsed meeting card shows: the AI summary when that version is switched on, otherwise the first 200 characters of the original. */
fun Meeting.preview(): String = if (showAi && summary.isNotBlank()) summary else raw.trim().let { if (it.length > 200) it.take(200).trimEnd() + "…" else it }

/** What an expanded meeting card shows: the tidied version when switched on, otherwise the original. */
fun Meeting.full(): String = if (showAi && hasAi) organized.ifBlank { summary } else raw

@Serializable
data class AppData(
    val trips: List<Trip> = emptyList(), val photos: List<Photo> = emptyList(), val projects: List<String> = emptyList(),
    val places: List<Place> = emptyList(), val currentTrip: String? = null, val myName: String = "", val myPhone: String = "", val managerUid: String? = null,
    val team: List<TeamMember> = emptyList(), val managerName: String? = null, val meetings: List<Meeting> = emptyList(),
    /** Naver search API key for looking up addresses by company name; entered in the app, never in the repo. */
    val naverId: String = "", val naverSecret: String = "",
    /** The user's own LLM for summaries: full chat endpoint URL, model name and optional key. Stays on this device. */
    val llmUrl: String = "", val llmModel: String = "", val llmKey: String = "",
)

/** Local store: one JSON file, immutable snapshots, so every change can be undone. */
object Store {
    var data by mutableStateOf(AppData()); private set
    var undoCount by mutableStateOf(0); private set
    private var file: File? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val history = ArrayDeque<AppData>()

    fun load(c: Context) {
        if (file != null) return
        file = File(c.filesDir, "store.json")
        file?.takeIf { it.exists() }?.let { f -> runCatching { data = json.decodeFromString<AppData>(f.readText()) } }
    }

    fun update(change: (AppData) -> AppData) {
        val old = data
        history.addLast(data); if (history.size > 50) history.removeFirst()
        data = change(data); undoCount = history.size; save(); Cloud.sync(old, data)
    }

    fun undo() { val old = data; history.removeLastOrNull()?.let { data = it; save(); Cloud.sync(old, it) }; undoCount = history.size }

    /** A change that should not get its own undo step, such as each keystroke of a text field. */
    fun edit(change: (AppData) -> AppData) { val old = data; data = change(data); save(); Cloud.sync(old, data) }

    /** Marks the current state as an undo point before a run of [edit] calls. */
    fun checkpoint() { history.addLast(data); if (history.size > 50) history.removeFirst(); undoCount = history.size }

    /** Team-shared lists arriving from the server; not an undoable user action. */
    fun mergeShared(projects: List<String>, places: List<Place>) {
        val d = data
        val next = d.copy(projects = (d.projects + projects).distinct(), places = (d.places + places).distinctBy { it.name + "|" + it.person })
        if (next != d) { data = next; save() }
    }

    private fun save() { runCatching { file?.writeText(json.encodeToString(data)) } }

    val current: Trip? get() = data.trips.firstOrNull { it.id == data.currentTrip } ?: data.trips.lastOrNull { it.active }
}

/** Projects every install starts with; the team list grows from here. */
val DEFAULT_PROJECTS = listOf("bbp 페이즈3 내장트림 개발", "국책)히팅시스템", "조절식 그립핸들 개발")

/** The first other trip whose time range overlaps [start, end); back-to-back trips do not overlap. */
fun overlappingTrip(trips: List<Trip>, start: Long, end: Long, exceptId: String?): Trip? = trips.firstOrNull { it.id != exceptId && start < it.end && it.start < end }

fun newId(): String = UUID.randomUUID().toString().take(12)

const val MIN30 = 30 * 60_000L
const val DAY = 86_400_000L

/** Nearest 30-minute mark. */
fun round30(t: Long): Long = (t + MIN30 / 2) / MIN30 * MIN30

private fun zdt(t: Long): ZonedDateTime = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
fun fDate(t: Long): String = zdt(t).let { "${it.monthValue}/${it.dayOfMonth}(${"월화수목금토일"[it.dayOfWeek.value - 1]})" }
fun fTime(t: Long): String = zdt(t).let { "%02d:%02d".format(it.hour, it.minute) }
fun fKoreanDateTime(t: Long): String = zdt(t).let {
    val h = it.hour
    "${it.monthValue}월 ${it.dayOfMonth}일(${"월화수목금토일"[it.dayOfWeek.value - 1]}) ${if (h < 12) "오전" else "오후"} ${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(it.minute)}"
}

fun notice(t: Trip): String =
    "<외근공지>\n1. 목적 : ${t.purpose}\n2. 일시 : ${fDate(t.start)} ${fTime(t.start)} ~ ${fTime(t.end)}\n3. 장소 : ${t.place}\n4. 인원 : ${t.people}"

fun fDuration(ms: Long): String { val m = ms / 60_000; return listOf(if (m >= 60) "${m / 60}시간" else "", if (m % 60 > 0) "${m % 60}분" else "").filter { it.isNotEmpty() }.joinToString(" ") }

/** Whole days from today to the day of [t] in local time: 0 today, 1 tomorrow. */
fun daysFromToday(t: Long): Long = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate())
