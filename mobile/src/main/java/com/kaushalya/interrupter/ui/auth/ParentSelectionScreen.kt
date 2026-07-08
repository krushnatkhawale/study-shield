package com.kaushalya.interrupter.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaushalya.interrupter.data.ParentSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentSelectionScreen(
    parents: List<ParentSummary>,
    onParentSelected: (parentId: String, parentName: String) -> Unit,
    onAddNewParent: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Who's studying?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B00)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a profile to continue",
            fontSize = 16.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(32.dp))

        parents.forEach { parent ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onParentSelected(parent.parentId, parent.parentName) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = parent.parentName.take(2).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B00),
                        modifier = Modifier
                            .size(48.dp)
                            .wrapContentSize(Alignment.Center)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(parent.parentName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onAddNewParent,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Add New Parent", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onSkip) {
            Text("Skip for now", color = Color.Gray)
        }
    }
}
