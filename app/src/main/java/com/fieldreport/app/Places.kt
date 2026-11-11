package com.fieldreport.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One result of a place search by name. */
data class FoundPlace(val name: String, val address: String, val phone: String, val category: String)

/** Parses a Naver local-search response. Titles arrive with <b> tags and HTML entities around the match. */
fun parseNaverLocal(json: String): List<FoundPlace> = runCatching {
    Json.parseToJsonElement(json).jsonObject["items"]?.jsonArray.orEmpty().map { e ->
        val o = e.jsonObject
        fun s(k: String) = o[k]?.jsonPrimitive?.content.orEmpty()
        val title = s("title").replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").trim()
        FoundPlace(title, s("roadAddress").ifBlank { s("address") }, s("telephone"), s("category"))
    }.filter { it.name.isNotBlank() }
}.getOrDefault(emptyList())

/** Searches places by name. Throws with a readable message when the key is wrong or the network fails. */
fun searchPlaces(query: String, clientId: String, clientSecret: String): List<FoundPlace> {
    val url = "https://openapi.naver.com/v1/search/local.json?display=5&query=" + URLEncoder.encode(query, "UTF-8")
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000; readTimeout = 10000
        setRequestProperty("X-Naver-Client-Id", clientId); setRequestProperty("X-Naver-Client-Secret", clientSecret)
    }
    val code = c.responseCode
    if (code == 401 || code == 403) error("검색 API 키가 맞지 않습니다. 설정의 주소 검색 키를 확인해 주세요.")
    if (code == 429) error("오늘 검색 한도를 넘었습니다.")
    if (code != 200) error("검색 서버 오류 ($code)")
    return parseNaverLocal(c.inputStream.bufferedReader().use { it.readText() })
}

/** Opens Naver Map on the query: the app if installed, otherwise the website. */
fun openNaverMap(c: Context, query: String) {
    val q = Uri.encode(query)
    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("nmap://search?query=$q&appname=${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://map.naver.com/p/search/$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/**
 * "주소 검색" for a typed company name: lists matching places with their addresses, and picking one
 * hands it back. Without a search key it opens Naver Map instead, so the address can still be looked up.
 */
@Composable
fun PlaceSearch(query: String, onPick: (FoundPlace) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<FoundPlace>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf("") }
    val hasKey = Store.data.naverId.isNotBlank() && Store.data.naverSecret.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(enabled = query.trim().length >= 2 && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            val q = query.trim()
            if (!hasKey) { openNaverMap(ctx, q); Toast.makeText(ctx, "네이버 지도에서 찾아 주소를 복사해 오세요. 검색 키를 넣으면 앱 안에서 바로 검색됩니다.", Toast.LENGTH_LONG).show(); return@OutlinedButton }
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { searchPlaces(q, Store.data.naverId, Store.data.naverSecret) } }
                busy = false; searched = q
                r.onSuccess { results = it }.onFailure { results = null; Toast.makeText(ctx, it.message ?: "검색하지 못했습니다. 인터넷 연결을 확인해 주세요.", Toast.LENGTH_LONG).show() }
            }
        }) { Text(if (busy) "검색 중…" else if (hasKey) "‘${query.trim().take(12)}’ 주소 검색" else "네이버 지도에서 주소 찾기") }
        results?.let { list ->
            if (list.isEmpty()) Text("‘$searched’ 검색 결과가 없습니다. 지역명을 함께 적어 보세요. 예: 태산솔루션즈 화성", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { p ->
                Surface(Modifier.fillMaxWidth().clickable { onPick(p); results = null }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold)
                        Text(p.address, style = MaterialTheme.typography.bodySmall)
                        if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
