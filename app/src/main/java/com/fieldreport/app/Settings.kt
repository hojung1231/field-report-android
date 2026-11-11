package com.fieldreport.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App settings: who I am, my own LLM for summaries, and the address-search key. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val d = Store.data
    var testing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("설정", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
                }
                SectionCard("내 이름") {
                    VoiceField(d.myName, { v -> Store.edit { it.copy(myName = v) } }, Modifier.fillMaxWidth(), placeholder = { Text("예: 이병화 선임") }, singleLine = true)
                }
                SectionCard("AI 요약 (내 LLM 연결)") {
                    Text("회의 카드의 미리보기를 AI가 200자로 요약합니다. OpenAI 방식의 채팅 주소를 쓰는 서버면 연결됩니다. 이 정보는 이 기기에만 저장됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { Store.edit { it.copy(llmUrl = "https://api.openai.com/v1/chat/completions", llmModel = it.llmModel.ifBlank { "gpt-4.1-mini" }) } }, label = { Text("OpenAI 주소 넣기") })
                        AssistChip(onClick = { Store.edit { it.copy(llmUrl = "https://서버주소/api/chat") } }, label = { Text("사내 서버 형식") })
                        AssistChip(onClick = { Store.edit { it.copy(llmUrl = "http://192.168.0.10:11434/v1/chat/completions") } }, label = { Text("내 PC(Ollama) 형식") })
                    }
                    OutlinedTextField(d.llmUrl, { v -> Store.edit { it.copy(llmUrl = v.trim()) } }, Modifier.fillMaxWidth(), label = { Text("채팅 주소 (전체 URL)") }, singleLine = true)
                    OutlinedTextField(d.llmModel, { v -> Store.edit { it.copy(llmModel = v.trim()) } }, Modifier.fillMaxWidth(), label = { Text("모델 이름") }, singleLine = true)
                    OutlinedTextField(d.llmKey, { v -> Store.edit { it.copy(llmKey = v.trim()) } }, Modifier.fillMaxWidth(), label = { Text("API 키 (없으면 비워 두세요)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedButton(enabled = Llm.configured && !testing, modifier = Modifier.fillMaxWidth(), onClick = {
                        testing = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { Llm.chat("한국어로 짧게 답한다.", "연결 확인입니다. '연결됨'이라고만 답하세요.", 30) } }
                            testing = false
                            Toast.makeText(ctx, r.fold({ "연결됐습니다. 응답: ${it.take(40)}" }, { it.message ?: "연결하지 못했습니다. 주소와 인터넷 연결을 확인해 주세요." }), Toast.LENGTH_LONG).show()
                        }
                    }) { Text(if (testing) "확인 중…" else "연결 확인") }
                }
                SectionCard("주소 검색 (네이버 검색 API)") {
                    Text("네이버 개발자센터에서 받은 Client ID와 Secret을 넣으면 업체명으로 주소를 앱 안에서 검색합니다. 서버에 연결되어 있으면 한 사람만 넣어도 팀 전체에 적용됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(d.naverId, { v -> Store.edit { it.copy(naverId = v.trim()) } }, Modifier.fillMaxWidth(), label = { Text("Client ID") }, singleLine = true)
                    OutlinedTextField(d.naverSecret, { v -> Store.edit { it.copy(naverSecret = v.trim()) } }, Modifier.fillMaxWidth(), label = { Text("Client Secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            }
        }
    }
}
