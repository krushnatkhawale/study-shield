package com.kaushalya.interrupter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaushalya.interrupter.data.AppDatabase
import com.kaushalya.interrupter.data.QuizResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private val BandBest = Color(0xFF2E7D32)
private val BandBetter = Color(0xFFFF6B00)
private val BandGood = Color(0xFF1E88E5)
private val BandAttention = Color(0xFFC62828)

fun bandOf(pct: Int): String = when {
    pct >= 80 -> "Best"
    pct >= 50 -> "Better"
    pct >= 30 -> "Good"
    else -> "Needs attention"
}

fun bandColorOf(pct: Int): Color = when {
    pct >= 80 -> BandBest
    pct >= 50 -> BandBetter
    pct >= 30 -> BandGood
    else -> BandAttention
}

fun friendlyDay(ts: Long): String {
    fun dayStart(millis: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
    val diffDays = ((dayStart(System.currentTimeMillis()) - dayStart(ts)) / 86_400_000L).toInt()
    return when {
        diffDays <= 0 -> "Today"
        diffDays == 1 -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(ts))
    }
}

fun friendlyTime(ts: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizAnalyticsScreen(packName: String, kidName: String, kidPhotoUri: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<QuizResult>?>(null) }
    LaunchedEffect(packName, kidName) {
        results = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).quizResultDao()
                .getResultsForContent(packName, kidName)
        }
    }
    val quizShort = packName.split("·").lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: packName

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KidAvatar(photoUri = kidPhotoUri, name = kidName, size = 32.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("$quizShort • $kidName")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val data = results
        when {
            data == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            data.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) { Text("No attempts yet.", color = Color.Gray) }
            else -> {
                val pcts = data.map { if (it.totalQuestions > 0) it.score * 100 / it.totalQuestions else 0 }
                val best = pcts.count { it >= 80 }
                val better = pcts.count { it in 50..79 }
                val good = pcts.count { it in 30..49 }
                val attention = pcts.count { it < 30 }
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Total Attempts: ${data.size}",
                                fontWeight = FontWeight.Bold
                            )
                            val attention = pcts.count { it < 30 }
                            if (attention > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                        .background(
                                            BandAttention.copy(alpha = 0.12f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = BandAttention,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "$attention attempt${if (attention == 1) "" else "s"} below 30% — needs attention. The kid may need help with the questions or the topic.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Score bands", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            BandDonut(best = best, better = better, good = good, attention = attention)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Best ≥ 80% · Better 50–79% · Good 30–49% · Needs attention < 30%",
                                style = MaterialTheme.typography.labelSmall, color = Color.Gray
                            )
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Attempts per day (last 14 days)", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            DayWiseBars(data)
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("History", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            data.sortedByDescending { it.completedAt }.forEach { r ->
                                val pct = if (r.totalQuestions > 0) r.score * 100 / r.totalQuestions else 0
                                val bandColor = bandColorOf(pct)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(26.dp).clip(CircleShape)
                                            .background(bandColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            bandOf(pct).take(1),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${friendlyDay(r.completedAt)}, ${friendlyTime(r.completedAt)} — ${r.score}/${r.totalQuestions} ($pct%)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandDonut(best: Int, better: Int, good: Int, attention: Int) {
    val bands = listOf(
        "Best" to BandBest, "Better" to BandBetter, "Good" to BandGood,
        "Needs attention" to BandAttention
    )
    val counts = listOf(best, better, good, attention)
    val total = counts.sum().coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 24.dp.toPx()
                var start = -90f
                counts.forEachIndexed { i, c ->
                    if (c > 0) {
                        val sweep = c * 360f / total
                        drawArc(
                            color = bands[i].second,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Attempts", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bands.forEachIndexed { i, (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${counts[i]}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DayWiseBars(data: List<QuizResult>) {
    fun dayStart(millis: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }
    val today = dayStart(System.currentTimeMillis())
    val days = (13 downTo 0).map { today - it * 86_400_000L }
    val counts = days.map { d -> data.count { dayStart(it.completedAt) == d } }
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        counts.forEach { c ->
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                val frac = if (max == 0) 0f else c.toFloat() / max
                val labelH = 18.dp
                val barH = ((maxHeight - labelH) * frac).coerceAtLeast(if (c == 0) 4.dp else 4.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (c > 0) "$c" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.height(labelH)
                    )
                    Canvas(modifier = Modifier.fillMaxWidth().height(barH)) {
                        drawRoundRect(
                            color = BandBetter,
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Fill
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { i, d ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val label = when (i) {
                    13 -> "Today"
                    12 -> "Yday"
                    else -> dayFmt.format(Date(d))
                }
                val isLabeled = i == 0 || i == 13 || i == 12 || i % 3 == 0
                Text(
                    if (isLabeled) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray, maxLines = 1
                )
            }
        }
    }
}
