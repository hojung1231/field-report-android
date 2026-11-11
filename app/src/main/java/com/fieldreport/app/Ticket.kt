package com.fieldreport.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class TicketAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * The current trip in the shape of a rail ticket. Collapsed by default to a thin strip; tapping
 * expands it and a long press opens a menu with the actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripTicket(t: Trip, actions: List<TicketAction>) {
    val c = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val (status, tone) = when {
        !t.active -> "종료" to c.outline
        now < t.start -> "출발 예정" to c.primary
        now <= t.end -> "외근 중" to Color(0xFF0E9A68)
        else -> "종료 시간 지남" to Color(0xFFB86E00)
    }
    var open by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // Tap expands or collapses; a long press brings up the actions, which stay out of sight otherwise.
    Box {
    Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(20.dp)).background(c.surface)
        .combinedClickable(onClick = { open = !open }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }, onLongClickLabel = "외근 메뉴 열기")) {
        if (!open) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxHeight().background(tone).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fDate(t.start), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(status, color = Color.White, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                    // Always one line: the font is sized to fit a narrow phone, and wrapping is off.
                    Text("${fTime(t.start)}→${fTime(t.end)}", fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-0.5).sp, maxLines = 1, softWrap = false)
                    Text(t.place.ifBlank { "장소 미입력" } + " · " + t.project, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.ExpandMore, contentDescription = "펼쳐 보기") }
            }
        } else {
            Row(Modifier.fillMaxWidth().background(tone).padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(status, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(fDate(t.start), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = { open = false }) { Icon(Icons.Filled.ExpandLess, contentDescription = "접기", tint = Color.White) }
            }
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column { Label("출발"); Text(fTime(t.start), fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fDuration(t.end - t.start), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tone)
                        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                            val y = size.height / 2; val r = 4.dp.toPx()
                            drawLine(tone, Offset(r, y), Offset(size.width - r, y), strokeWidth = 2.dp.toPx())
                            drawCircle(tone, r, Offset(r, y))
                            drawLine(tone, Offset(size.width - r, y), Offset(size.width - r - 7.dp.toPx(), y - 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(tone, Offset(size.width - r, y), Offset(size.width - r - 7.dp.toPx(), y + 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) { Label("종료"); Text(fTime(t.end), fontSize = 38.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = (-1).sp) }
                }
                Column {
                    Label("행선지"); Text(t.place.ifBlank { "장소 미입력" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (t.addr.isNotBlank()) Text(t.addr, fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.6f)) { Label("프로젝트"); Text(t.project, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Column(Modifier.weight(1f)) { Label("인원"); Text(t.people.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                Column { Label("목적"); Text(t.purpose.ifBlank { "—" }, fontSize = 16.sp, lineHeight = 22.sp) }
            }
        }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        actions.forEach { a ->
            val tint = if (a.danger) c.error else c.onSurface
            DropdownMenuItem(text = { Text(a.label, color = tint) }, leadingIcon = { Icon(a.icon, contentDescription = null, tint = tint) }, onClick = { menu = false; a.onClick() })
        }
    }
    }
}

/** The bottom edge shared by ticket-style cards: a notched tear line, then a compact row of actions. */
@Composable
fun TicketStub(actions: List<TicketAction>) {
    val c = MaterialTheme.colorScheme
    val bg = c.background; val dash = c.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2; val r = 7.dp.toPx()
        drawCircle(bg, r, Offset(0f, y)); drawCircle(bg, r, Offset(size.width, y))
        drawLine(dash, Offset(r + 6.dp.toPx(), y), Offset(size.width - r - 6.dp.toPx(), y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 4.dp)) {
        actions.forEach { a ->
            val tint = if (a.danger) c.error else c.onSurfaceVariant
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = a.onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(a.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Text(a.label, fontSize = 11.sp, color = tint, maxLines = 1)
            }
        }
    }
}

/**
 * A meeting record as a card: a 200-character preview by default, the whole transcript when expanded
 * with the arrow. Tapping the card itself calls [onOpen], which goes back into recording and editing.
 */
@Composable
fun MeetingCard(m: Meeting, actions: List<TicketAction>, onOpen: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    var open by rememberSaveable(m.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(20.dp)).background(c.surface)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable { if (onOpen != null) onOpen() else open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.fillMaxHeight().background(c.primary).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fTime(m.at), color = c.onPrimary, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                Text("회의", color = c.onPrimary, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(m.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${fDate(m.at)} · ${m.raw.length}자" + if (open) " · 전체" else " · 미리보기", fontSize = 12.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { open = !open }) { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "접기" else "펼쳐 보기") }
        }
        // Collapsed: about 200 characters. Opened: the whole record.
        Text((if (open) m.raw else m.preview()).ifBlank { "아직 기록된 내용이 없습니다." }, Modifier.fillMaxWidth().clickable { if (onOpen != null) onOpen() else open = !open }.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
            fontSize = if (open) 15.sp else 14.sp, lineHeight = if (open) 22.sp else 20.sp, color = if (open) c.onSurface else c.onSurfaceVariant)
        TicketStub(actions)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
