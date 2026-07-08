package com.kaushalya.interrupter.ui.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.data.AppDatabase
import com.kaushalya.interrupter.data.StudentEntity
import com.kaushalya.interrupter.data.StudentRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).studentDao() }
    val repository = remember { StudentRepository(dao) }
    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        error = null
    }

    LaunchedEffect(Unit) {
        val result = repository.listStudents()
        result.onSuccess { students = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Students") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Student")
                    }
                }
            )
        }
    ) { padding ->
        when {
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            refresh()
                        }) { Text("Retry") }
                    }
                }
            }
            students.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No students yet", fontSize = 18.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showAddDialog = true }) { Text("Add Student") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(students) { student ->
                        StudentCard(student)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStudentDialog(
            dao = dao,
            onDismiss = { showAddDialog = false },
            onAdded = { newStudent ->
                students = students + newStudent
                showAddDialog = false
            }
        )
    }
}

@Composable
fun StudentCard(student: StudentEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color(0xFFFF6B00), modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                val info = buildList {
                    student.gender?.let { add(it) }
                    student.studentClass?.let { add("Class $it") }
                }.joinToString(" \u2022 ")
                Text(info.ifEmpty { "Student" }, fontSize = 14.sp, color = Color.Gray)
            }
            if (student.syncPending) {
                Icon(Icons.Default.SyncProblem, null, tint = Color(0xFFFFA000), modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
            }
        }
    }
}

@Composable
fun AddStudentDialog(
    dao: com.kaushalya.interrupter.data.StudentDao,
    onDismiss: () -> Unit,
    onAdded: (StudentEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var studentClass by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Student") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = gender,
                    onValueChange = { gender = it },
                    label = { Text("Gender (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = studentClass,
                    onValueChange = { studentClass = it },
                    label = { Text("Class (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Name is required"
                        return@Button
                    }
                    saving = true
                    val repo = StudentRepository(dao)
                    scope.launch {
                        val result = repo.addStudent(
                            name = name.trim(),
                            gender = gender.ifBlank { null },
                            birthYear = null,
                            studentClass = studentClass.ifBlank { null }
                        )
                        result.onSuccess { student ->
                            onAdded(student)
                        }.onFailure {
                            error = it.message
                            saving = false
                        }
                    }
                },
                enabled = !saving
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
