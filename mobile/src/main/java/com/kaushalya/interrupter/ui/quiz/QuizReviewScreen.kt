package com.kaushalya.interrupter.ui.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.data.FeedbackRepository
import com.kaushalya.interrupter.data.QuizQuestion
import com.kaushalya.interrupter.data.StudyContent
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Review of a quiz pack: lists every question with its options and highlights the
 * correct answer. Each question carries up / down / report actions together with an
 * optional (or mandatory, for report) comment. Feedback is persisted per question
 * per account on the backend. Reviewing does not record a quiz result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizReviewScreen(
    pack: StudyContent?,
    onBack: () -> Unit
) {
    val questions = pack?.questions ?: emptyList()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedbackRepository = remember { FeedbackRepository.getInstance(context) }

    // Per-question feedback state keyed by question id (local mirror of backend).
    val feedbackState = remember { mutableStateMapOf<Long, FeedbackUiState>() }

    // Load existing feedback for each reviewable question on open.
    LaunchedEffect(questions) {
        questions.forEach { q ->
            val id = q.id ?: return@forEach
            try {
                val resp = RetrofitClient.getApiService().getQuestionFeedback(id)
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    feedbackState[id] = FeedbackUiState(
                        vote = body.vote,
                        downCategory = body.downCategory,
                        comment = body.comment,
                        reported = body.reported
                    )
                }
            } catch (_: Exception) {
                // Keep default state; a later action will re-sync on submit.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pack == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Quiz not available for review", color = Color.Gray)
                }
                return@Scaffold
            }

            val quizName = pack.name.split("·").lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: pack.name
            val subject = pack.category ?: "General"

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(quizName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Subject: $subject • ${questions.size} question${if (questions.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            if (questions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No questions to review in this quiz.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    itemsIndexed(questions) { index, q ->
                        QuizReviewCard(
                            number = index + 1,
                            question = q,
                            state = feedbackState[q.id],
                            onUp = { id, comment -> scope.launch { feedbackRepository.submit(id, "UP", null, comment, false) } },
                            onDown = { id, category, comment ->
                                scope.launch { feedbackRepository.submit(id, "DOWN", category, comment, false) }
                            },
                            onClear = { id -> scope.launch { feedbackRepository.submit(id, "NONE", null, null, false) } },
                            onReport = { id, comment ->
                                scope.launch { feedbackRepository.submit(id, feedbackState[id]?.vote ?: "NONE", null, comment, true) }
                            },
                            onLocalState = { id, state -> feedbackState[id] = state }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizReviewCard(
    number: Int,
    question: QuizQuestion,
    state: FeedbackUiState?,
    onUp: (Long, String?) -> Unit,
    onDown: (Long, String, String?) -> Unit,
    onClear: (Long) -> Unit,
    onReport: (Long, String?) -> Unit,
    onLocalState: (Long, FeedbackUiState) -> Unit
) {
    val id = question.id
    // Without a backend question id there is nothing to attach feedback to.
    val feedbackEnabled = id != null

    var showUpDialog by remember { mutableStateOf(false) }
    var showDownDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Q$number. ${question.question}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            question.options.forEach { option ->
                val isCorrect = option == question.answer
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCorrect) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isCorrect) "Correct answer" else null,
                        tint = if (isCorrect) Color(0xFF2E7D32) else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCorrect) Color(0xFF2E7D32) else Color.Unspecified
                    )
                }
            }

            if (question.answer.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "No correct answer marked",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            if (feedbackEnabled) {
                val current = state ?: FeedbackUiState()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            when (current.vote) {
                                "UP" -> onClear(id)          // tapping again clears the once-per-user up vote
                                else -> showUpDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (current.vote == "UP") ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFE8F5E9)
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = "Up vote", tint = Color(0xFF2E7D32))
                    }

                    OutlinedButton(
                        onClick = {
                            when (current.vote) {
                                "DOWN" -> onClear(id)
                                else -> showDownDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = if (current.vote == "DOWN") ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFEBEE)
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Filled.ThumbDown, contentDescription = "Down vote", tint = Color(0xFFC62828))
                    }

                    OutlinedButton(
                        onClick = { showReportDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = if (current.reported) ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFFF3E0)
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = "Report", tint = Color(0xFFFF6B00))
                    }
                }

                if (current.downCategory != null || current.comment != null || current.reported) {
                    val bits = buildList {
                        if (current.reported) add("reported")
                        if (current.downCategory != null) add(current.downCategory.lowercase().replace('_', ' '))
                        if (!current.comment.isNullOrBlank()) add("\"${current.comment}\"")
                    }
                    if (bits.isNotEmpty()) {
                        Text(
                            bits.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showUpDialog && id != null) {
        FeedbackCommentDialog(
            title = "Rate question up",
            confirmLabel = "Vote Up",
            commentRequired = false,
            onDismiss = { showUpDialog = false },
            onConfirm = { comment ->
                onUp(id, comment)
                onLocalState(id, (state ?: FeedbackUiState()).copy(vote = "UP", comment = comment ?: state?.comment))
                showUpDialog = false
            }
        )
    }

    if (showDownDialog && id != null) {
        DownVoteDialog(
            onDismiss = { showDownDialog = false },
            onConfirm = { category, comment ->
                onDown(id, category, comment)
                onLocalState(
                    id,
                    (state ?: FeedbackUiState()).copy(
                        vote = "DOWN",
                        downCategory = category,
                        comment = comment ?: state?.comment
                    )
                )
                showDownDialog = false
            }
        )
    }

    if (showReportDialog && id != null) {
        FeedbackCommentDialog(
            title = "Report question",
            confirmLabel = "Report",
            commentRequired = true,
            onDismiss = { showReportDialog = false },
            onConfirm = { comment ->
                onReport(id, comment)
                onLocalState(id, (state ?: FeedbackUiState()).copy(reported = true, comment = comment))
                showReportDialog = false
            }
        )
    }
}

/** Local mirror of a question's saved feedback — vote, optional down category, comment. */
private data class FeedbackUiState(
    val vote: String = "NONE",
    val downCategory: String? = null,
    val comment: String? = null,
    val reported: Boolean = false
)

private val DownCategoryLabels = listOf(
    "WRONG_ANSWER" to "Wrong answer",
    "TYPO" to "Typo",
    "OFFENSIVE" to "Offensive",
    "OTHER" to "Other"
)

@Composable
private fun FeedbackCommentDialog(
    title: String,
    confirmLabel: String,
    commentRequired: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(if (commentRequired) "Comment (required)" else "Comment (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(comment.trim().takeIf { it.isNotEmpty() }) },
                enabled = !commentRequired || comment.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DownVoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var category by remember { mutableStateOf(DownCategoryLabels.first().first) }
    var comment by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate question down") },
        text = {
            Column {
                Text("Category (optional)", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { categoryExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(DownCategoryLabels.first { it.first == category }.second)
                }
                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    DownCategoryLabels.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { category = value; categoryExpanded = false }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(category, comment.trim().takeIf { it.isNotEmpty() }) }) {
                Text("Vote Down")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
