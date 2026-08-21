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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onBack: () -> Unit
) {
    val recentResults by viewModel.recentResults.collectAsState()
    val selectedResult by viewModel.selectedResult.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val kidProfiles by kidViewModel.kidProfiles.collectAsState()
    var selectedKidIndex by remember { mutableIntStateOf(0) }

    val allKidNames = remember(kidProfiles) {
        listOf("All") + kidProfiles.map { it.name }
    }

    val filteredResults = remember(recentResults, selectedKidIndex, kidProfiles) {
        if (selectedKidIndex == 0 || kidProfiles.isEmpty()) {
            recentResults
        } else {
            val selectedKidName = kidProfiles[selectedKidIndex - 1].name
            recentResults.filter { it.childName == selectedKidName }
        }
    }

    // One-time offer: after the default Exp kid finishes a test, invite the parent
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
                title = { Text("Session Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.retrySync() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedResult != null) {
            ResultDetailContent(
                result = selectedResult!!,
                viewModel = viewModel,
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
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Recent Quiz Results",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "View your child's quiz performance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
                            Text("Syncing results...", style = MaterialTheme.typography.bodyMedium)
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
                            "No results yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Quiz results will appear here after your child completes a session on the TV.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(results) { result ->
                ResultCard(result = result, viewModel = viewModel, onClick = { onSelectResult(result) })
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
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val percentage = if (result.totalQuestions > 0) (result.score * 100 / result.totalQuestions) else 0
    val message = viewModel.getMessageForScore(result.score, result.totalQuestions)
    val isSynced = result.syncStatus == 1

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
                Text(result.childName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "${result.score}/${result.totalQuestions} correct",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    sdf.format(Date(result.completedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = if (isSynced) "Synced" else "Not synced",
                    tint = if (isSynced) Color(0xFF38A169) else Color(0xFF9E9E9E),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultDetailContent(
    result: QuizResult,
    viewModel: SessionResultViewModel,
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
                title = { Text("Result Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        ResultDetailRow("Score", "${result.score} / ${result.totalQuestions}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ResultDetailRow("Time Spent", "${timeMinutes}m ${timeSeconds}s")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        ResultDetailRow("Completed", sdf.format(Date(result.completedAt)))
                        if (result.contentName != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ResultDetailRow("Quiz", result.contentName)
                        }
                        if (result.category != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ResultDetailRow("Category", result.category)
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
                            if (percentage >= 80) "Great effort!"
                            else if (percentage >= 50) "Good try!"
                            else "Keep practicing!",
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
                    1 -> "Synced to cloud"
                    2 -> "Sync failed - tap sync to retry"
                    else -> "Saved locally"
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
