package com.fieldreport.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A value with a minus button on the left and a plus button on the right. */
@Composable
fun Stepper(label: String, value: String, minus: String, plus: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onMinus, modifier = Modifier.width(76.dp)) { Text(minus, maxLines = 1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onPlus, modifier = Modifier.width(76.dp)) { Text(plus, maxLines = 1) }
        }
    }
}

@Composable
fun SectionCard(title: String? = null, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Segmented progress bar with each step's name written inside its segment; tapping a segment calls [onStep]. */
@Composable
fun StepBar(step: Int, labels: List<String>, modifier: Modifier = Modifier, onStep: ((Int) -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { i, label ->
            val bg = when { i == step -> c.primary; i < step -> c.primary.copy(alpha = .22f); else -> c.surfaceVariant }
            val fg = when { i == step -> c.onPrimary; i < step -> c.primary; else -> c.onSurfaceVariant }
            // Wider segments for longer names so every label fits on one line.
            Box(Modifier.weight(label.length + 1.5f).height(30.dp).clip(CircleShape).background(bg).clickable(enabled = onStep != null && i != step) { onStep?.invoke(i) }, contentAlignment = Alignment.Center) {
                Text(label, color = fg, fontSize = 11.sp, fontWeight = if (i == step) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Date with day arrows and today/tomorrow shortcuts. [onShift] receives the change in milliseconds. */
@Composable
fun DateRow(t: Long, onShift: (Long) -> Unit) {
    val day = daysFromToday(t)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-DAY) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "하루 전") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fDate(t), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(when (day) { 0L -> "오늘"; 1L -> "내일"; 2L -> "모레"; -1L -> "어제"; else -> if (day > 0) "${day}일 뒤" else "${-day}일 전" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onShift(DAY) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "하루 뒤") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            listOf("오늘" to 0L, "내일" to 1L, "모레" to 2L).forEach { (l, n) -> FilterChip(day == n, { onShift((n - day) * DAY) }, label = { Text(l) }) }
        }
    }
}

/** A compact one-line stepper for lists of similar numbers. */
@Composable
fun MiniStepper(label: String, value: String, minus: String, plus: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(minus) }
        Box(Modifier.width(6.dp))
        FilledTonalButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(plus) }
    }
}
