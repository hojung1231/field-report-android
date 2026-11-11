package com.fieldreport.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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

/** Segmented progress bar: one segment per step, with "n단계 / 총 m단계" and the step's name under it. */
@Composable
fun StepBar(step: Int, labels: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.indices.forEach { i ->
                Box(Modifier.weight(1f).height(if (i == step) 8.dp else 6.dp).clip(CircleShape).background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Row {
            Text("${step + 1}단계 / 총 ${labels.size}단계", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("  ·  ${labels[step]}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
