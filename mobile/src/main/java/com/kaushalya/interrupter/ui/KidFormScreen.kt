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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var grade by remember { mutableStateOf(kid?.grade?.takeIf { it.isNotBlank() && !it.equals("Exp", true) } ?: "") }
    var dob by remember { mutableStateOf(kid?.dateOfBirth) }
    var selectedSyllabus by remember { mutableStateOf(kid?.syllabus ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val genders = listOf("Boy", "Girl", "Other")

    fun handleSaveAndBack() {
        viewModel.editingKid = null
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (kid == null) "Add Kid Profile" else "Edit Kid Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            Button(
                onClick = {
                    val year = birthYear.toIntOrNull()
                    if (name.isNotBlank() && year != null && grade.isNotBlank()) {
                        viewModel.saveKid(name, gender, year, dob, grade, selectedSyllabus.takeIf { it.isNotBlank() })
                        handleSaveAndBack()
                    }
                },
                enabled = name.isNotBlank() && birthYear.isNotBlank() && grade.isNotBlank()
            ) {
                Text("Save Profile")
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
                label = { Text("Full Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Column {
                Text("Gender *", style = MaterialTheme.typography.labelMedium)
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
                onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 4) birthYear = it },
                label = { Text("Birth Year *") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 2015") },
                singleLine = true
            )

            OutlinedTextField(
                value = grade,
                onValueChange = { grade = it },
                label = { Text("Grade / Class *") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Grade 4") },
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
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            OutlinedTextField(
                value = dob?.let { sdf.format(Date(it)) } ?: "",
                onValueChange = { },
                label = { Text("Birthday (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Select Date")
                    }
                }
            )

            // Syllabus Dropdown
            val syllabusOptions = listOf("CBSE", "ICSE", "State Board", "International", "Other")

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedSyllabus,
                    onValueChange = {},
                    label = { Text("Syllabus (Optional)") },
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

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
