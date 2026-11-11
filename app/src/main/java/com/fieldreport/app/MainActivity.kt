package com.fieldreport.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.load(applicationContext)
        Cloud.start(applicationContext)
        enableEdgeToEdge()
        setContent { App() }
    }
}

private val Light = lightColorScheme(primary = Color(0xFF1D55F5), background = Color(0xFFEEF2F6), surface = Color(0xFFFFFFFF))
private val Dark = darkColorScheme(primary = Color(0xFF6C93FF), background = Color(0xFF0A0F15), surface = Color(0xFF141B24))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        UpdateGate()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val tabs = listOf("외근" to Icons.Filled.Home, "회의" to Icons.Filled.Mic, "촬영" to Icons.Filled.PhotoCamera,
            "용차" to Icons.Filled.LocalShipping, "수신" to Icons.AutoMirrored.Filled.Send, "팀" to Icons.Filled.Groups)
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("외근 보고") }, actions = {
                    TextButton(onClick = { Store.undo() }, enabled = Store.undoCount > 0) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                        Text(" 실행 취소" + if (Store.undoCount > 0) " ${Store.undoCount}" else "")
                    }
                })
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen()
                    2 -> CameraScreen()
                    3 -> TruckScreen()
                    4 -> InboxScreen()
                    5 -> TeamScreen()
                    else -> ComingSoon(tabs[tab].first)
                }
            }
        }
    }
}

@Composable
fun ComingSoon(name: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$name 탭은 아직 만들지 않았습니다", style = MaterialTheme.typography.titleMedium)
        Text("음성 녹음과 AI 정리를 붙일 때 추가됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
