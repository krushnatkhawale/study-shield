package com.kaushalya.interrupter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.R
import com.kaushalya.interrupter.data.KidProfile
import com.kaushalya.interrupter.data.QuizResult
import com.kaushalya.interrupter.data.SessionManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionResultScreen(
    viewModel: SessionResultViewModel,
    kidViewModel: KidProfileViewModel,
    sessionManager: SessionManager,
    onEditKid: (KidProfile) -> Unit = {},
    onPlayAgain: (QuizResult) -> Unit = {},
    onBack: () -> Unit
) {
    val recentResults by viewModel.recentResults.collectAsState()
    val selectedResult by viewModel.selectedResult.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val kidProfiles by kidViewModel.kidProfiles.collectAsState()
    var selectedKidIndex by remember { mutableIntStateOf(0) }

    val context = LocalContext.current

    val allKidNames = remember(kidProfiles) {
        listOf(context.getString(R.string.all)) + kidProfiles.map { it.name }
    }

    val filteredResults = remember(recentResults, selectedKidIndex, kidProfiles) {
        if (selectedKidIndex == 0 || kidProfiles.isEmpty()) {
            recentResults
        } else {
            val selectedKidName = kidProfiles[selectedKidIndex - 1].name
            recentResults.filter { it.childName == selectedKidName }
        }
    }

    // One-time offer: after the default Trial kid finishes a test, invite the parent
    // to update the kid profile to unlock class-based tests.
    val expUpgradeKid by viewModel.expUpgradeKid.collectAsState()
    val expKid = expUpgradeKid
    if (expKid != null) {
        ExpUpgradePromptDialog(
            kidName = expKid.name,
            onUpdateKidInfo = {
                viewModel.markExpPromptHandled(expKid)
                onEditKid(expKid)
            },
            onDismiss = {
                viewModel.markExpPromptHandled(expKid)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.signin_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.results_refresh))
                    }
                    IconButton(onClick = { viewModel.retrySync() }) {
                        Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.results_sync))
                    }
                }
            )
        }
    ) { padding ->
        if (selectedResult != null) {
            ResultDetailContent(
                result = selectedResult!!,
                viewModel = viewModel,
                onPlayAgain = { onPlayAgain(selectedResult!!) },
                onBack = { viewModel.clearSelection() },
                modifier = Modifier.padding(padding)
            )
        } else {
            Column(modifier = Modifier.padding(padding)) {
                // Kid tabs
                if (kidProfiles.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedKidIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allKidNames.forEachIndexed { index, name ->
                            Tab(
                                selected = selectedKidIndex == index,
                                onClick = { selectedKidIndex = index },
                                text = { Text(name) }
                            )
                        }
                    }
                }

                ResultListContent(
                    results = filteredResults,
                    viewModel = viewModel,
                    onSelectResult = { viewModel.selectResult(it) },
                    onPlayAgain = { onPlayAgain(it) },
                    syncState = syncState,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResultListContent(
    results: List<QuizResult>,
    viewModel: SessionResultViewModel,
    onSelectResult: (QuizResult) -> Unit,
    onPlayAgain: (QuizResult) -> Unit,
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (syncState) {
            is SyncState.Syncing -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.syncing_results), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            is SyncState.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, null, tint = Color(0xFFD32F2F))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(syncState.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD32F2F))
                        }
                    }
                }
            }
            is SyncState.Success -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF38A169))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(syncState.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF38A169))
                        }
                    }
                }
            }
            is SyncState.Idle -> {}
        }

        if (results.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_results_yet),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.no_results_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // SS-EXP-08: one-tap play again for the same child
            val lastResult = results.maxByOrNull { it.completedAt }
            if (lastResult != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B00))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAgain(lastResult) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.play_again_for, lastResult.childName),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            val grouped = results.groupBy {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it.completedAt))
            }
            grouped.forEach { (dateHeader, groupResults) ->
                item {
                    Text(
                        dateHeader,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(groupResults) { result ->
                    ResultCard(result = result, viewModel = viewModel, onClick = { onSelectResult(result) })
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: QuizResult,
    viewModel: SessionResultViewModel,
    onClick: () -> Unit
) {
    val percentage = if (result.totalQuestions > 0) (result.score * 100 / result.totalQuestions) else 0
    val isSynced = result.syncStatus == 1
    val quizTitle = (result.contentName ?: result.category ?: result.childName).let {
        if (it.length > 30) it.take(30) + "…" else it
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (percentage >= 80) Color(0xFFE8F5E9)
            else if (percentage >= 50) Color(0xFFFFF8E1)
            else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (percentage >= 80) Color(0xFF38A169)
                        else if (percentage >= 50) Color(0xFFFFA000)
                        else Color(0xFFE53935)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$percentage%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(quizTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(
                    stringResource(
                        R.string.result_row_summary,
                        result.score,
                        result.totalQuestions,
                        stringResource(bandStringRes(percentage))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.fastAnswerCount > 0) {
                    Text(
                        stringResource(R.string.fast_answers, result.fastAnswerCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB26A00)
                    )
                }
            }
            Icon(
                if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = if (isSynced) stringResource(R.string.result_synced_reason) else stringResource(R.string.result_not_synced_reason),
                tint = if (isSynced) Color(0xFF38A169) else Color(0xFF9E9E9E),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultDetailContent(
    result: QuizResult,
    viewModel: SessionResultViewModel,
    onPlayAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val percentage = if (result.totalQuestions > 0) (result.score * 100 / result.totalQuestions) else 0
    val message = viewModel.getMessageForScore(result.score, result.totalQuestions)
    val timeMinutes = result.timeSpentSeconds / 60
    val timeSeconds = result.timeSpentSeconds % 60

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.signin_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            if (percentage >= 80) Color(0xFF38A169)
                            else if (percentage >= 50) Color(0xFFFFA000)
                            else Color(0xFFE53935)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$percentage%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                }
            }

            item {
                Text(
                    result.childName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ResultDetailRow(stringResource(R.string.score), "${result.score} / ${result.totalQuestions}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ResultDetailRow(stringResource(R.string.time_spent), "${timeMinutes}m ${timeSeconds}s")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ResultDetailRow(stringResource(R.string.completed), sdf.format(Date(result.completedAt)))
                        if (result.contentName != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ResultDetailRow(stringResource(R.string.quiz), result.contentName)
                        }
                        if (result.category != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ResultDetailRow(stringResource(R.string.category), result.category)
                        }
                        if (result.fastAnswerCount > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                stringResource(R.string.fast_answers_warning, result.fastAnswerCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB26A00),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (percentage >= 80) Color(0xFFE8F5E9)
                        else if (percentage >= 50) Color(0xFFFFF8E1)
                        else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (percentage >= 80) stringResource(R.string.great_effort)
                            else if (percentage >= 50) stringResource(R.string.good_try)
                            else stringResource(R.string.keep_practicing),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (percentage >= 80) Color(0xFF2E7D32)
                            else if (percentage >= 50) Color(0xFFF57F17)
                            else Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                val syncStatusText = when (result.syncStatus) {
                    1 -> stringResource(R.string.synced_to_cloud)
                    2 -> stringResource(R.string.sync_failed_retry)
                    else -> stringResource(R.string.saved_locally)
                }
                val syncIcon = when (result.syncStatus) {
                    1 -> Icons.Default.CloudDone
                    2 -> Icons.Default.CloudOff
                    else -> Icons.Default.CloudUpload
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(syncIcon, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(syncStatusText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            // SS-EXP-08: one-tap play again for the same child
            item {
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.play_again_for, result.childName),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
