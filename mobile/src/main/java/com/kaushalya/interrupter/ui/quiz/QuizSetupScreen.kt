package com.kaushalya.interrupter.ui.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.data.QuizLoader
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.StudyContent
import com.kaushalya.interrupter.ui.KidProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSetupScreen(
    onBack: () -> Unit,
    kidViewModel: KidProfileViewModel,
    sessionManager: SessionManager
) {
    val context = LocalContext.current
    val kidProfiles by kidViewModel.kidProfiles.collectAsState()
    var selectedKidIndex by remember { mutableIntStateOf(0) }
    var availableQuizzes by remember { mutableStateOf<List<StudyContent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedKidIndex, kidProfiles) {
        if (kidProfiles.isNotEmpty()) {
            loading = true
            error = null
            try {
                val loader = QuizLoader(context)
                val selectedKid = kidProfiles[selectedKidIndex]
                val quizzes = loader.loadQuizzesForGradeRemoteFirst(selectedKid.grade)
                availableQuizzes = quizzes
                sessionManager.selectedKidId = selectedKid.id
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            kidProfiles.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChildCare, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No kid profiles found", fontSize = 18.sp, color = Color.Gray)
                        Text("Add a kid profile first to set up quizzes", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            availableQuizzes.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Quiz, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No quizzes found for ${kidProfiles[selectedKidIndex].grade}", fontSize = 18.sp, color = Color.Gray)
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Kid tabs
                    if (kidProfiles.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedKidIndex,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            kidProfiles.forEachIndexed { index, kid ->
                                Tab(
                                    selected = selectedKidIndex == index,
                                    onClick = { selectedKidIndex = index },
                                    text = { Text(kid.name) }
                                )
                            }
                        }
                    }

                    // Quiz list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(availableQuizzes) { quiz ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Quiz, null, tint = Color(0xFFFF6B00), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(quiz.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        val questionCount = quiz.questions?.size ?: 0
                                        val cat = quiz.category ?: ""
                                        Text("$cat \u2022 $questionCount questions", fontSize = 14.sp, color = Color.Gray)
                                    }
                                    TextButton(onClick = { /* TODO: schedule from quiz */ }) {
                                        Text("Select")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
