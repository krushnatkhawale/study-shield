package com.kaushalya.interrupter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaushalya.interrupter.data.KidProfile
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun KidProfileScreen(
    viewModel: KidProfileViewModel = viewModel(),
    onAddKid: () -> Unit = {},
    onEditKid: (KidProfile) -> Unit = {}
) {
    val kids by viewModel.kidProfiles.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddKid,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Kid")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Manage Kid Profiles",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (kids.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChildFriendly, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No kid profiles added yet.", color = Color.Gray)
                        Text("Click + to add your first child.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(kids) { kid ->
                        KidItem(
                            kid = kid,
                            onEdit = { onEditKid(kid) },
                            onDelete = { viewModel.deleteKid(kid) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KidItem(
    kid: KidProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (kid.gender == "Girl") Icons.Default.Face5 else Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kid.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (kid.gender.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = { },
                            label = { Text(kid.gender, fontSize = 10.sp) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
                Text("Grade: ${kid.grade}", style = MaterialTheme.typography.bodyMedium)
                if (kid.birthYear > 0) {
                    Text(
                        "Born: ${kid.birthYear}${kid.dateOfBirth?.let { " (${sdf.format(Date(it))})" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (kid.grade.equals("Exp", ignoreCase = true)) {
                    Text(
                        "Starter profile — update info to unlock class-based tests",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B00)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
