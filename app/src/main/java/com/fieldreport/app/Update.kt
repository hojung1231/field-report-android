package com.fieldreport.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Published next to the APK; the app compares [versionCode] with its own on every launch. */
@Serializable
data class UpdateInfo(val versionCode: Long, val versionName: String = "", val notes: List<String> = emptyList(), val url: String)

private const val UPDATE_URL = "https://raw.githubusercontent.com/hojung1231/field-report-android/apk/version.json"

private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 20000; useCaches = false }

private fun installedVersion(c: Context): Long = c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode

/**
 * On launch, asks the server for the newest version. If it is newer, shows a dialog; on confirm it
 * downloads the APK and hands it to the system installer, which asks the user once more to install.
 */
@Composable
fun UpdateGate() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val latest = withContext(Dispatchers.IO) {
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<UpdateInfo>(open(UPDATE_URL).inputStream.bufferedReader().use { it.readText() }) }.getOrNull()
        }
        if (latest != null && latest.versionCode > installedVersion(ctx)) info = latest
    }

    val u = info ?: return
    AlertDialog(
        onDismissRequest = { if (progress == null) info = null },
        title = { Text("업데이트가 있습니다") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("새 버전 ${u.versionName}")
                u.notes.forEach { Text("· $it") }
                val p = progress
                if (p != null) { LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()); Text("내려받는 중… ${(p * 100).toInt()}%") }
                if (failed) Text("내려받지 못했습니다. 인터넷 연결을 확인하고 다시 눌러 주세요.")
                else if (p == null) Text("확인을 누르면 내려받은 뒤 설치 화면이 열립니다. 작성한 내용은 그대로 유지됩니다.")
            }
        },
        confirmButton = {
            Button(enabled = progress == null, onClick = {
                failed = false; progress = 0f
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                            val out = File(dir, "update.apk")
                            val conn = open(u.url); val total = conn.contentLengthLong.coerceAtLeast(1)
                            conn.inputStream.use { input -> out.outputStream().use { o ->
                                val buf = ByteArray(64 * 1024); var done = 0L
                                while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; progress = (done.toFloat() / total).coerceIn(0f, 1f) }
                            } }
                            out
                        }.getOrNull()
                    }
                    progress = null
                    if (file == null) { failed = true; return@launch }
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("확인") }
        },
        dismissButton = { TextButton(enabled = progress == null, onClick = { info = null }) { Text("나중에") } },
    )
}
