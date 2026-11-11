package com.fieldreport.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The current trip drawn as a rail ticket: coloured header, departure → arrival, tear line, stub. */
@Composable
fun TripTicket(t: Trip) {
    val c = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val (status, tone) = when {
        !t.active -> "종료" to c.outline
        now < t.start -> "출발 예정" to c.primary
        now <= t.end -> "외근 중" to Color(0xFF0E9A68)
        else -> "종료 시간 지남" to Color(0xFFB86E00)
    }
    Column(Modifier.fillMaxWidth()) {
        // Header band
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(tone).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("외근 승차권", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 13.sp)
            Text("  ·  $status", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(fDate(t.start), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        // Main part: departure → arrival
        Column(Modifier.fillMaxWidth().background(c.surface).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Label("출발")
                    Text(fTime(t.start), fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fDuration(t.end - t.start), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tone)
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val y = size.height / 2; val r = 4.dp.toPx()
                        drawLine(tone, Offset(r, y), Offset(size.width - r, y), strokeWidth = 2.dp.toPx())
                        drawCircle(tone, r, Offset(r, y))
                        // arrow head at the arrival end
                        drawLine(tone, Offset(size.width - r, y), Offset(size.width - r - 7.dp.toPx(), y - 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(tone, Offset(size.width - r, y), Offset(size.width - r - 7.dp.toPx(), y + 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label("종료")
                    Text(fTime(t.end), fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp)
                }
            }
            Column {
                Label("행선지")
                Text(t.place.ifBlank { "장소 미입력" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.6f)) { Label("프로젝트"); Text(t.project, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Column(Modifier.weight(1f)) { Label("인원"); Text(t.people.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        // Tear line with a punched notch at each edge
        val bg = c.background; val dash = c.outlineVariant
        Canvas(Modifier.fillMaxWidth().height(22.dp).background(c.surface)) {
            val y = size.height / 2; val r = 11.dp.toPx()
            drawCircle(bg, r, Offset(0f, y)); drawCircle(bg, r, Offset(size.width, y))
            drawLine(dash, Offset(r + 8.dp.toPx(), y), Offset(size.width - r - 8.dp.toPx(), y), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }
        // Stub
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)).background(c.surface).padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp)) {
            Label("목적")
            Text(t.purpose.ifBlank { "—" }, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
