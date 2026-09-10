package com.kaushalya.interrupter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaushalya.interrupter.R
import com.kaushalya.interrupter.data.Avatars
import com.kaushalya.interrupter.data.ClassGradeDto
import com.kaushalya.interrupter.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.*

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
    var selectedSyllabus by remember { mutableStateOf(kid?.syllabus ?: "") }
    var selectedAvatar by remember { mutableStateOf(kid?.avatar ?: "hero") }
    var expanded by remember { mutableStateOf(false) }

    val api = remember { RetrofitClient.getApiService() }
    var classGrades by remember { mutableStateOf<List<ClassGradeDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            classGrades = api.getClassGrades().body().orEmpty()
                .filter { !it.name.isNullOrBlank() }
                .sortedBy { it.name!! }
        } catch (e: Exception) {
            Log.w("KidFormScreen", "Could not load class grades, using canonical class list", e)
        }
    }

    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val genders = listOf("Boy", "Girl", "Other")

    fun handleSaveAndBack() {
        viewModel.editingKid = null
        onBack()
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
                        viewModel.saveKid(name, gender, year, dob, grade, selectedSyllabus.takeIf { it.isNotBlank() }, selectedAvatar)
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.full_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Class first: canonical backend class names, each with its typical age, so the parent
            // picks by age ("Nursery · age 3"), not by board jargon. Values match the backend
            // class-grades list exactly (bandForClassName normalizes synonyms on the server).
            val fallbackClasses = listOf("Nursery", "Junior KG", "Sr KG") + (1..10).map { "Class $it" }
            val gradeOptions = remember(classGrades) {
                (classGrades.mapNotNull { it.name } + fallbackClasses)
                    .distinct()
                    .sortedBy(::typicalAgeFor)
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
                    gradeOptions.forEach { option ->
                        FilterChip(
                            selected = grade == option,
                            onClick = { grade = option },
                            label = {
                                Text("$option · ${stringResource(R.string.class_age_hint, typicalAgeFor(option))}")
                            }
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
                        input.toIntOrNull()?.let { y ->
                            if (grade.isBlank()) {
                                val age = Calendar.getInstance().get(Calendar.YEAR) - y
                                if (age in 3..15) grade = suggestedClassForAge(age)
                            }
                        }
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

            // Syllabus is only for updating an existing kid — on first add the board stays the backend
            // default (board `ALL`), so parents are not asked board jargon up front.
            if (kid != null) {
                val syllabusOptions = listOf("CBSE", "ICSE", "State Board", "International", "Other")

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSyllabus,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.syllabus_optional)) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        syllabusOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedSyllabus = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Celebration mascot (shown on the TV completion screen after a quiz)
            Column {
                Text(stringResource(R.string.celebration_mascot), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.mascot_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Avatars.ALL.forEach { avatar ->
                        FilterChip(
                            selected = selectedAvatar == avatar.id,
                            onClick = { selectedAvatar = avatar.id },
                            label = { Text(avatar.label) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
