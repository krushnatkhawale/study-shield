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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaushalya.interrupter.R
import com.kaushalya.interrupter.data.KidProfile
import com.kaushalya.interrupter.data.QuizResult
import com.kaushalya.interrupter.data.SessionManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen kid profile + performance page.
 *
 * UI flow:
 *  - Top summary header (avatar, name, grade, gender, birth, syllabus) with an edit action.
 *  - Performance section: bar chart of quiz scores per attempt, a donut of score distribution,
 *    and a fast-answer insight (configured threshold vs observed fast answers).
 *  - Quiz presentation controls (read-lock, TTS, fast-answer threshold) — moved here from
 *    Select Content per krushnat's UX direction.
 *  - Delete profile (destructive, at the very end, with confirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidDetailScreen(
    kid: KidProfile?,
    kidViewModel: KidProfileViewModel,
    resultViewModel: SessionResultViewModel,
    sessionManager: SessionManager,
    onEditProfile: (KidProfile) -> Unit,
    onStartQuiz: () -> Unit = {},
    onBack: () -> Unit
) {
    if (kid == null) {
        // Kid not carried across process death; fall back to the list.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val detailViewModel: KidDetailViewModel = viewModel()
    LaunchedEffect(kid.name) { detailViewModel.observe(kid.name) }
    val results by detailViewModel.results.collectAsState()

    var confirmDelete by remember { mutableStateOf(false) }

    val accent = Color(0xFFFF6B00)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kid.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { detailViewModel.refresh(kid.name) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileHeaderCard(kid = kid, accent = accent, onEditProfile = { onEditProfile(kid) })

            if (results.isEmpty()) {
                SectionCard(title = stringResource(R.string.kid_detail_performance), accent = accent) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Insights, null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.kid_detail_no_results_title), color = Color.Gray)
                        Text(
                            stringResource(R.string.kid_detail_no_results_body, kid.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onStartQuiz) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.kid_detail_start_first_quiz))
                        }
                    }
                }
            } else {
                val latest = results.first()
                val latestPct = if (latest.totalQuestions > 0) (latest.score * 100f / latest.totalQuestions).toInt() else 0
                PerfHeroCard(
                    text = stringResource(
                        R.string.kid_perf_hero,
                        kid.name,
                        stringResource(bandStringRes(latestPct)),
                        latest.score,
                        latest.totalQuestions
                    ),
                    accent = accent
                )

                SectionCard(title = stringResource(R.string.kid_detail_performance), accent = accent) {
                    var showCharts by remember { mutableStateOf(false) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { showCharts = !showCharts }) {
                            Icon(
                                if (showCharts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (showCharts) stringResource(R.string.kid_detail_charts_hide)
                                else stringResource(R.string.kid_detail_charts)
                            )
                        }
                    }
                    if (showCharts) {
                        Text(
                            stringResource(R.string.kid_detail_chart_over_attempts),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        QuizScoreBarChart(results = results, accent = accent)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            stringResource(R.string.kid_detail_chart_distribution),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScoreDistribution(results = results, accent = accent)
                        Spacer(modifier = Modifier.height(16.dp))

                        FastAnswerInsight(
                            kid = kid,
                            results = results,
                            sessionManager = sessionManager,
                            accent = accent
                        )
                    }
                }
            }

            SectionCard(title = "Quiz presentation", accent = accent) {
                QuizPresentationConfigCard(kid = kid, sessionManager = sessionManager)
            }

            // Danger zone
            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete ${kid.name}'s profile", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${kid.name}'s profile?") },
            text = {
                Text("This removes the kid and their locally stored results. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    kidViewModel.deleteKid(kid)
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ProfileHeaderCard(kid: KidProfile, accent: Color, onEditProfile: () -> Unit) {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (kid.gender == "Girl") Icons.Default.Face5 else Icons.Default.Face,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = accent
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(kid.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (kid.gender.isNotBlank()) {
                        Text("Gender: ${kid.gender}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }
                    Text("Grade: ${kid.grade}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    if (kid.birthYear > 0) {
                        Text(
                            "Born: ${kid.birthYear}${kid.dateOfBirth?.let { " (${sdf.format(Date(it))})" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    if (!kid.syllabus.isNullOrBlank()) {
                        Text("Syllabus: ${kid.syllabus}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                OutlinedButton(onClick = onEditProfile) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        }
    }
}

/** Hero card leading with a plain-words summary of the latest quiz ("Rohan did well — 8 out of 10"). */
@Composable
private fun PerfHeroCard(text: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Maps a quiz percentage to the shared band string used across kids, results and detail screens. */
fun bandStringRes(pct: Int): Int = when {
    pct >= 80 -> R.string.band_did_well
    pct >= 50 -> R.string.band_ok
    else -> R.string.band_needs_practice
}

/** Vertical bar chart of quiz % across the most recent attempts. */
@Composable
private fun QuizScoreBarChart(results: List<QuizResult>, accent: Color) {
    val shown = results.takeLast(12)
    val barGap = 6.dp
    val barTopRadius = 4.dp

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(barGap)
        ) {
            shown.forEach { r ->
                val pct = if (r.totalQuestions > 0) (r.score * 100f / r.totalQuestions).coerceIn(0f, 100f) else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        val barHeightPx = (size.height * pct / 100f).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            color = when {
                                pct >= 80f -> Color(0xFF2E7D32)
                                pct >= 50f -> accent
                                else -> Color(0xFFC62828)
                            },
                            topLeft = Offset(0f, size.height - barHeightPx),
                            size = Size(size.width, barHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barTopRadius.toPx()),
                            style = Fill
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(barGap)) {
            shown.forEach { r ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        shortName(r.contentName ?: "Quiz"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Donut chart of attempts bucketed into score bands. */
@Composable
private fun ScoreDistribution(results: List<QuizResult>, accent: Color) {
    data class Band(val label: String, val color: Color)

    val highLabel = stringResource(R.string.band_did_well) + " (≥80%)"
    val midLabel = stringResource(R.string.band_ok) + " (50–79%)"
    val lowLabel = stringResource(R.string.band_needs_practice) + " (<50%)"

    val all = results.map { r ->
        if (r.totalQuestions > 0) r.score * 100f / r.totalQuestions else 0f
    }
    val bands = listOf(
        Band(highLabel, Color(0xFF2E7D32)),
        Band(midLabel, accent),
        Band(lowLabel, Color(0xFFC62828))
    )
    val counts = bands.map { b ->
        when (b.label) {
            highLabel -> all.count { it >= 80f }
            midLabel -> all.count { it >= 50f && it < 80f }
            else -> all.count { it < 50f }
        }
    }
    val total = counts.sum().coerceAtLeast(1)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 24.dp.toPx()
                var start = -90f
                counts.forEachIndexed { i, c ->
                    if (c > 0) {
                        val sweep = c * 360f / total
                        drawArc(
                            color = bands[i].color,
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
                Text(stringResource(R.string.kid_detail_attempts), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bands.forEachIndexed { i, b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(b.color, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(b.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${counts[i]}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * Compares the configured fast-answer threshold against the observed fast answers.
 *
 * NOTE: the TV currently reports only a *count* of fast answers, not per-answer response
 * times, so this presents the configured threshold vs the share of the latest attempt flagged
 * as fast. A true per-answer time chart would require the TV to record and send per-answer
 * timestamps (not stored today).
 */
@Composable
private fun FastAnswerInsight(
    kid: KidProfile,
    results: List<QuizResult>,
    sessionManager: SessionManager,
    accent: Color
) {
    val config = sessionManager.getKidQuizConfig(kid.id)
    val thresholdSec = (config.fastAnswerThresholdMs / 1000f).let { if (it < 1) 1 else it }
    val latest = results.firstOrNull()
    val latestFast = latest?.fastAnswerCount ?: 0
    val latestTotal = latest?.totalQuestions ?: 0
    val latestFastPct = if (latestTotal > 0) latestFast * 100f / latestTotal else 0f

    Text(
        "Fast-answer insight",
        style = MaterialTheme.typography.labelMedium,
        color = Color.Gray,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoChip(
                    icon = Icons.Default.Timer,
                    label = "Threshold: ${"%.1f".format(thresholdSec)}s",
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (latest != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(
                        icon = Icons.Default.Bolt,
                        label = "Latest: $latestFast of $latestTotal answered fast (${latestFastPct.toInt()}%)",
                        accent = if (latestFast > 0) Color(0xFFB26A00) else Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (latestFastPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (latestFast > 0) Color(0xFFB26A00) else Color(0xFF2E7D32),
                    trackColor = Color(0xFFE0E0E0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (latestFast == 0)
                        "No suspiciously fast answers in the latest quiz."
                    else
                        "$latestFast answer(s) were given faster than the ${"%.1f".format(thresholdSec)}s threshold — review the Results page for details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else {
                Text("Awaiting the kid's next quiz.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
    Text(
        "Tip: per-answer response times aren't captured yet, so this tracks the fast-answer count relative to your threshold.",
        style = MaterialTheme.typography.labelSmall,
        color = Color.Gray
    )
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = accent)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun shortName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length <= 10) return trimmed
    // Try the last segment (e.g. "Science · Light") for a short, meaningful label.
    val last = trimmed.split("·").lastOrNull()?.trim()
    return (last ?: trimmed).take(10)
}
