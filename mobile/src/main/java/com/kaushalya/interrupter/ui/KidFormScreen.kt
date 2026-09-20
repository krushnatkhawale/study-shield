package com.kaushalya.interrupter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaushalya.interrupter.R
import com.kaushalya.interrupter.data.Avatars
import com.kaushalya.interrupter.data.BoardClassDto
import com.kaushalya.interrupter.data.BoardDto
import com.kaushalya.interrupter.data.ClassGradeDto
import com.kaushalya.interrupter.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.*

private val AccentOrange = Color(0xFFFF6B00)

/** Typical age for each class name, mirroring the backend's age → class mapping. */
private fun typicalAgeFor(className: String): Int {
    val t = className.lowercase(Locale.ROOT)
    return when {
        t.contains("nursery") -> 3
        t.contains("junior") || t.contains("lkg") -> 4
        t.contains("sr") || t.contains("senior") || t.contains("ukg") -> 5
        else -> className.filter { it.isDigit() }.toIntOrNull()?.let { it + 5 } ?: 6
    }
}

/** Backend-compatible class for an age (same rules as `QuestionBankContent.classNameForAge`). */
private fun suggestedClassForAge(age: Int): String = when {
    age <= 3 -> "Nursery"
    age <= 4 -> "Junior KG"
    age <= 5 -> "Sr KG"
    age <= 7 -> "Class 1"
    else -> "Class ${(age - 5).coerceIn(1, 10)}"
}

private val MascotColors = listOf(
    Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFFAED581), Color(0xFFFFD54F),
    Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFFFB74D), Color(0xFF7986CB),
    Color(0xFFF06292), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFDCE775)
)

private fun initialsFor(label: String): String =
    label.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KidFormScreen(
    onBack: () -> Unit,
    viewModel: KidProfileViewModel = viewModel()
) {
    val kid = viewModel.editingKid

    // Default-kid placeholders (blank gender, birthYear 0) start as empty form fields.
    var name by remember { mutableStateOf(kid?.name ?: "") }
    var gender by remember { mutableStateOf(kid?.gender?.takeIf { it.isNotBlank() } ?: "Boy") }
    var birthYear by remember { mutableStateOf(kid?.birthYear?.takeIf { it > 0 }?.toString() ?: "") }
    var grade by remember { mutableStateOf(kid?.grade?.takeIf { it.isNotBlank() } ?: "") }
    var dob by remember { mutableStateOf(kid?.dateOfBirth) }
    // Syllabus now stores the selected board code (default ALL); shown for add + edit.
    var selectedBoardId by remember { mutableStateOf<Long?>(null) }
    var selectedBoardCode by remember { mutableStateOf(kid?.syllabus?.takeIf { it.isNotBlank() } ?: "ALL") }
    var selectedAvatar by remember { mutableStateOf(kid?.avatar ?: "hero") }
    var photoUri by remember { mutableStateOf(kid?.photoUri) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            photoUri = it.toString()
        }
    }
    var boardExpanded by remember { mutableStateOf(false) }

    val api = remember { RetrofitClient.getApiService() }
    var classGrades by remember { mutableStateOf<List<ClassGradeDto>>(emptyList()) }
    var boards by remember { mutableStateOf<List<BoardDto>>(emptyList()) }
    var allBoardClasses by remember { mutableStateOf<List<BoardClassDto>>(emptyList()) }
    var boardClasses by remember { mutableStateOf<List<BoardClassDto>>(emptyList()) }

    // (1) Load boards + all board-classes + legacy class-grades (offline fallback).
    LaunchedEffect(Unit) {
        try {
            boards = api.getBoards().body().orEmpty().filter { !it.name.isNullOrBlank() }
        } catch (e: Exception) {
            Log.w("KidFormScreen", "Could not load boards", e)
        }
        try {
            allBoardClasses = api.getBoardClasses().body().orEmpty()
                .filter { !it.displayName.isNullOrBlank() }
        } catch (e: Exception) {
            Log.w("KidFormScreen", "Could not load board-classes", e)
        }
        try {
            classGrades = api.getClassGrades().body().orEmpty()
                .filter { !it.name.isNullOrBlank() }
                .sortedBy { it.name!! }
        } catch (e: Exception) {
            Log.w("KidFormScreen", "Could not load class grades, using canonical class list", e)
        }
        // Resolve the kid's saved board code to a board id when boards are available.
        if (selectedBoardCode != "ALL") {
            boards.firstOrNull { it.code == selectedBoardCode }?.id?.let { selectedBoardId = it }
        }
    }

    // (2) On board change, load that board's classes; reset grade if not offered.
    LaunchedEffect(selectedBoardId) {
        val id = selectedBoardId
        if (id == null) {
            boardClasses = emptyList()
            return@LaunchedEffect
        }
        try {
            val loaded = api.getBoardClassesForBoard(id).body().orEmpty()
                .filter { !it.displayName.isNullOrBlank() }
            boardClasses = loaded
            if (grade.isNotBlank() && loaded.none { it.displayName == grade }) {
                grade = ""
            }
        } catch (e: Exception) {
            Log.w("KidFormScreen", "Could not load board-classes for board $id", e)
            boardClasses = emptyList()
        }
    }

    val selectedBoard = boards.firstOrNull { it.id == selectedBoardId }
        ?: boards.firstOrNull { it.code == selectedBoardCode }

    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val genders = listOf("Boy", "Girl", "Other")

    fun handleSaveAndBack() {
        viewModel.editingKid = null
        onBack()
    }

    fun suggestGradeForBirthYear(y: Int) {
        if (grade.isNotBlank()) return
        val age = Calendar.getInstance().get(Calendar.YEAR) - y
        if (age !in 3..15) return
        // Prefer a loaded board-class displayName whose typical age matches; else canonical name.
        val boardOfferings = (boardClasses.ifEmpty { allBoardClasses }).mapNotNull { it.displayName }.distinct()
        val canonical = suggestedClassForAge(age)
        grade = when {
            canonical in boardOfferings -> canonical
            boardOfferings.isNotEmpty() ->
                boardOfferings.minByOrNull { kotlin.math.abs(typicalAgeFor(it) - age) } ?: canonical
            else -> canonical
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (kid == null) stringResource(R.string.add_kid_title) else stringResource(R.string.edit_kid_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.signin_back))
                    }
                }
            )
        },
        floatingActionButton = {
            Button(
                onClick = {
                    val year = birthYear.toIntOrNull() ?: 0
                    if (name.isNotBlank() && grade.isNotBlank()) {
                        viewModel.saveKid(name, gender, year, dob, grade, selectedBoardCode.takeIf { it.isNotBlank() }, selectedAvatar, photoUri)
                        handleSaveAndBack()
                    }
                },
                enabled = name.isNotBlank() && grade.isNotBlank()
            ) {
                Text(stringResource(R.string.save_profile))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top: centered avatar + photo buttons.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.clip(CircleShape).clickable { photoPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    KidAvatar(photoUri = photoUri, name = name.ifBlank { "Kid" }, size = 96.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { photoPicker.launch("image/*") }) { Text("Choose photo") }
                    if (!photoUri.isNullOrBlank()) {
                        TextButton(onClick = { photoUri = null }) { Text("Remove photo") }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.full_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 2. Board selector (backend boards; default ALL).
            ExposedDropdownMenuBox(
                expanded = boardExpanded,
                onExpandedChange = { boardExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = (selectedBoard?.name ?: if (selectedBoardCode == "ALL") "All Boards" else selectedBoardCode),
                    onValueChange = {},
                    label = { Text("Board") },
                    readOnly = true,
                    supportingText = selectedBoard?.code?.let { code -> { Text(code) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = boardExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = boardExpanded,
                    onDismissRequest = { boardExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("All Boards")
                                Text("ALL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            selectedBoardId = null
                            selectedBoardCode = "ALL"
                            boardExpanded = false
                        }
                    )
                    boards.forEach { board ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(board.name.orEmpty())
                                    board.code?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            },
                            onClick = {
                                selectedBoardId = board.id
                                selectedBoardCode = board.code ?: board.name.orEmpty()
                                boardExpanded = false
                            }
                        )
                    }
                }
            }

            // 3. Class selector: board-classes when available, else legacy + canonical fallback.
            val fallbackClasses = listOf("Nursery", "Junior KG", "Sr KG") + (1..10).map { "Class $it" }
            val effectiveBoardClasses = boardClasses.ifEmpty { allBoardClasses.filter { selectedBoardId == null || it.boardId == selectedBoardId } }
            val gradeChips: List<Pair<String, Int?>> = if (effectiveBoardClasses.isNotEmpty()) {
                effectiveBoardClasses.mapNotNull { bc ->
                    bc.displayName?.let { it to bc.ordinal }
                }.distinctBy { it.first }.sortedBy { it.second ?: typicalAgeFor(it.first) }
            } else {
                val legacy = classGrades.mapNotNull { it.name }
                ((legacy + fallbackClasses).distinct().sortedBy(::typicalAgeFor)).map { it to null }
            }
            Column {
                Text(stringResource(R.string.grade_class), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.select_class),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    gradeChips.forEach { (option, ordinal) ->
                        val label = if (ordinal != null) "$option · L$ordinal" else "$option · ${stringResource(R.string.class_age_hint, typicalAgeFor(option))}"
                        FilterChip(
                            selected = grade == option,
                            onClick = { grade = option },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Column {
                Text(stringResource(R.string.gender_label), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genders.forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { gender = option },
                            label = { Text(option) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = birthYear,
                onValueChange = { input ->
                    if (input.all { char -> char.isDigit() } && input.length <= 4) {
                        birthYear = input
                        // Pre-pick the class whose typical age matches the entered birth year.
                        input.toIntOrNull()?.let { suggestGradeForBirthYear(it) }
                    }
                },
                label = { Text(stringResource(R.string.birth_year)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.birth_year_example)) },
                singleLine = true
            )

            // Date Picker
            var showDatePicker by remember { mutableStateOf(false) }
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dob
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dob = datePickerState.selectedDateMillis
                            showDatePicker = false
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            OutlinedTextField(
                value = dob?.let { sdf.format(Date(it)) } ?: "",
                onValueChange = { },
                label = { Text(stringResource(R.string.birthday_optional)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                    }
                }
            )

            // 4. Celebration mascot: horizontal scroller of initial-circle cards.
            Column {
                Text(stringResource(R.string.celebration_mascot), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.mascot_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(Avatars.ALL, key = { it.id }) { avatar ->
                        val index = Avatars.ALL.indexOf(avatar)
                        val selected = selectedAvatar == avatar.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.width(72.dp).clickable { selectedAvatar = avatar.id }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MascotColors[index % MascotColors.size])
                                    .then(
                                        if (selected) Modifier.border(3.dp, AccentOrange, CircleShape)
                                        else Modifier
                                    )
                            ) {
                                Text(
                                    initialsFor(avatar.label),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                avatar.label,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
