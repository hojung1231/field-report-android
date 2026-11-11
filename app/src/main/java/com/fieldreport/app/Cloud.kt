package com.fieldreport.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

data class TeamTrip(val trip: Trip, val owner: String)
data class Report(val id: String, val from: String, val to: String, val text: String, val place: String, val sentAt: Long)

/**
 * Team sync through Firebase (anonymous sign-in + Firestore).
 * Stays off, and the app stays local-only, until app/google-services.json is present at build time.
 */
object Cloud {
    var enabled by mutableStateOf(false); private set
    var uid by mutableStateOf<String?>(null); private set
    var status by mutableStateOf("서버에 연결되지 않았습니다"); private set
    var teamTrips by mutableStateOf(listOf<TeamTrip>()); private set
    var users by mutableStateOf(mapOf<String, String>()); private set
    var reports by mutableStateOf(listOf<Report>()); private set
    private var db: FirebaseFirestore? = null

    fun nameOf(id: String): String = users[id]?.takeIf { it.isNotBlank() } ?: if (id == uid) Store.data.myName.ifBlank { "나" } else "이름 미등록"

    fun start(c: Context) {
        if (enabled) return
        runCatching { FirebaseApp.initializeApp(c) }.getOrNull() ?: return
        enabled = true; status = "서버에 연결하는 중…"
        db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val ready = { id: String -> uid = id; status = "서버에 연결됨"; listen(); pushAll() }
        val user = auth.currentUser
        if (user != null) ready(user.uid)
        else auth.signInAnonymously()
            .addOnSuccessListener { r -> r.user?.let { ready(it.uid) } }
            .addOnFailureListener { status = "로그인하지 못했습니다. Firebase 콘솔에서 익명 로그인을 켜 주세요." }
    }

    private fun DocumentSnapshot.s(k: String) = getString(k) ?: ""

    private fun listen() {
        val d = db ?: return
        d.collection("trips").addSnapshotListener { snap, _ ->
            if (snap != null) teamTrips = snap.documents.map {
                TeamTrip(Trip(it.id, it.s("project").ifBlank { "기타" }, it.s("purpose"), it.getLong("start") ?: 0, it.getLong("end") ?: 0, it.s("place"), it.s("people"), it.getBoolean("active") ?: false), it.s("owner"))
            }
        }
        d.collection("users").addSnapshotListener { snap, _ -> if (snap != null) users = snap.documents.associate { it.id to it.s("name") } }
        d.collection("reports").addSnapshotListener { snap, _ ->
            if (snap != null) reports = snap.documents.map { Report(it.id, it.s("from"), it.s("to"), it.s("text"), it.s("place"), it.getLong("sentAt") ?: 0) }.sortedByDescending { it.sentAt }
        }
        var projects = listOf<String>(); var places = listOf<Place>()
        d.collection("projects").addSnapshotListener { snap, _ -> if (snap != null) { projects = snap.documents.map { it.s("name") }.filter { it.isNotBlank() }; Store.mergeShared(projects, places) } }
        d.collection("places").addSnapshotListener { snap, _ -> if (snap != null) { places = snap.documents.map { Place(it.s("name"), it.s("addr"), it.s("person"), it.s("phone")) }.filter { it.name.isNotBlank() }; Store.mergeShared(projects, places) } }
    }

    private fun key(s: String) = s.replace(Regex("[/.#\\[\\]*\\s]"), "_").take(120).ifBlank { "_" }
    private fun tripDoc(t: Trip, me: String) = mapOf("owner" to me, "project" to t.project, "purpose" to t.purpose, "start" to t.start, "end" to t.end, "place" to t.place, "people" to t.people, "active" to t.active)
    private fun pushPlace(p: Place) = db?.collection("places")?.document(key(p.name + "|" + p.person))?.set(mapOf("name" to p.name, "addr" to p.addr, "person" to p.person, "phone" to p.phone))

    private fun pushAll() {
        val d = db ?: return; val me = uid ?: return; val data = Store.data
        data.trips.forEach { d.collection("trips").document(it.id).set(tripDoc(it, me)) }
        data.projects.forEach { d.collection("projects").document(key(it)).set(mapOf("name" to it)) }
        data.places.forEach { pushPlace(it) }
        if (data.myName.isNotBlank()) d.collection("users").document(me).set(mapOf("name" to data.myName))
    }

    /** Send only what changed between two local snapshots, so undo reaches the server too. */
    fun sync(old: AppData, new: AppData) {
        val d = db ?: return; val me = uid ?: return
        val before = old.trips.associateBy { it.id }
        new.trips.filter { before[it.id] != it }.forEach { d.collection("trips").document(it.id).set(tripDoc(it, me)) }
        old.trips.filter { o -> new.trips.none { it.id == o.id } }.forEach { d.collection("trips").document(it.id).delete() }
        (new.projects - old.projects.toSet()).forEach { d.collection("projects").document(key(it)).set(mapOf("name" to it)) }
        (new.places - old.places.toSet()).forEach { pushPlace(it) }
        if (new.myName != old.myName && new.myName.isNotBlank()) d.collection("users").document(me).set(mapOf("name" to new.myName))
    }

    /** Returns the message to show the user. */
    fun sendReport(t: Trip): String {
        val d = db ?: return "서버에 연결되지 않았습니다"; val me = uid ?: return "아직 로그인 중입니다"
        val name = Store.data.managerName ?: return "팀 탭에서 팀장을 먼저 지정해 주세요"
        val to = users.entries.firstOrNull { it.value == name }?.key ?: return "${name}님이 아직 이 앱에 접속하지 않았습니다"
        d.collection("reports").document(newId()).set(mapOf("from" to me, "to" to to, "tripId" to t.id, "text" to notice(t), "place" to t.place, "sentAt" to System.currentTimeMillis()))
        return "${nameOf(to)}님에게 보고를 공유했습니다"
    }

    fun unsend(id: String) { db?.collection("reports")?.document(id)?.delete() }
}
