package com.kaushalya.interrupter.ui.parents

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.data.ParentRepository
import com.kaushalya.interrupter.data.ParentSummary
import com.kaushalya.interrupter.data.SessionManager
import kotlinx.coroutines.launch

private val genders = listOf("Male", "Female", "Other")
private val relations = listOf("Mother", "Father", "Guardian", "Grandparent", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentManagementScreen(
    sessionManager: SessionManager,
    onBack: () -> Unit
) {
    val repository = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    var parents by remember { mutableStateOf<List<ParentSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val currentParentId = sessionManager.parentId
    val profile = sessionManager.profile
    // Lookup map: parentId -> relation
    val parentRelationMap = remember(profile) {
        profile.parents.associate { it.parentId to it.relation }
    }

    LaunchedEffect(Unit) {
        val result = repository.listParents()
        result.onSuccess { parents = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parents") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Parent")
            }
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
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                val result = repository.listParents()
                                result.onSuccess { parents = it }
                                    .onFailure { error = it.message }
                                loading = false
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                if (parents.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No parents added yet.", color = Color.Gray)
                            Text("Tap + to add a parent.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(parents) { parent ->
                            val isAccountHolder = parent.parentId == currentParentId
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(parent.parentName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            if (isAccountHolder) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                SuggestionChip(
                                                    onClick = { },
                                                    label = { Text("Primary", fontSize = 10.sp) },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            parentRelationMap[parent.parentId] ?: "Parent",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (!isAccountHolder) {
                                        IconButton(onClick = {
                                            scope.launch {
                                                repository.deleteParent(parent.parentId)
                                                loading = true
                                                val result = repository.listParents()
                                                result.onSuccess { parents = it }
                                                    .onFailure { error = it.message }
                                                loading = false
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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

    if (showAddDialog) {
        AddParentDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, gender, relation ->
                showAddDialog = false
                scope.launch {
                    loading = true
                    error = null
                    repository.addParent(name, gender, relation)
                    val result = repository.listParents()
                    result.onSuccess { parents = it
                        // Sync profile: update names from API, preserve relation from dialog
                        val apiParents = it.map { p -> sessionManager.profile.parents.find { it.parentId == p.parentId }
                            ?: com.kaushalya.interrupter.data.ProfileParent(p.parentId, p.parentName, gender, relation) }
                        sessionManager.updateProfile { copy(parents = apiParents) }
                    }
                        .onFailure { error = it.message }
                    loading = false
                }
            }
        )
    }
}

@Composable
fun AddParentDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(genders[0]) }
    var relation by remember { mutableStateOf(relations[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Parent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Gender", style = MaterialTheme.typography.labelMedium)
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

                Text("Relation with Kids", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    relations.forEach { option ->
                        FilterChip(
                            selected = relation == option,
                            onClick = { relation = option },
                            label = { Text(option) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), gender, relation) },
                enabled = name.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
