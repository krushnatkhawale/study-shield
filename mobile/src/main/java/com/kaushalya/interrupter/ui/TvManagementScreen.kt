package com.kaushalya.interrupter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kaushalya.interrupter.data.ConnectedTV
import com.kaushalya.interrupter.data.NetworkWithTvs
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TvManagementScreen(
    viewModel: TvManagementViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Current Network", "History")

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentSsid()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> CurrentNetworkTab(viewModel)
            1 -> HistoryTab(viewModel)
        }
    }
}

@Composable
fun CurrentNetworkTab(viewModel: TvManagementViewModel) {
    val ssid by viewModel.currentSsid
    val discoveredTvs by viewModel.discoveredTvs.collectAsState()
    val savedTvs by viewModel.currentNetworkTvs.collectAsState()
    val isDiscovering = viewModel.isDiscovering

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Active WiFi Network", style = MaterialTheme.typography.labelMedium)
                        Text(ssid, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Available TVs", style = MaterialTheme.typography.titleMedium)
                if (isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { viewModel.startDiscovery() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Now")
                    }
                }
            }
        }

        if (discoveredTvs.isEmpty() && !isDiscovering) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No TVs found. Make sure TV app is open.", color = Color.Gray)
                }
            }
        }

        items(discoveredTvs) { tv ->
            val ip = tv.host?.hostAddress ?: ""
            val isSaved = savedTvs.any { it.ipAddress == ip }
            
            TvDeviceItem(
                name = tv.serviceName,
                ip = ip,
                isAvailable = true,
                isSaved = isSaved,
                onSave = { viewModel.saveConnection(tv.serviceName, ip) }
            )
        }

        if (savedTvs.isNotEmpty()) {
            item {
                Text("Previously Connected Here", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            
            items(savedTvs.filter { saved -> discoveredTvs.none { it.host?.hostAddress == saved.ipAddress } }) { tv ->
                TvDeviceItem(
                    name = tv.name,
                    ip = tv.ipAddress,
                    isAvailable = false,
                    isSaved = true,
                    isFavorite = tv.isFavorite,
                    onToggleFavorite = { viewModel.toggleFavorite(tv) },
                    onForget = { viewModel.forgetTv(tv) }
                )
            }
        }
    }
}

@Composable
fun HistoryTab(viewModel: TvManagementViewModel) {
    val history by viewModel.networksWithTvs.collectAsState()

    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No network history yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(history) { item ->
                NetworkHistoryItem(
                    item = item,
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onForget = { viewModel.forgetTv(it) }
                )
            }
        }
    }
}

@Composable
fun TvDeviceItem(
    name: String,
    ip: String,
    isAvailable: Boolean,
    isSaved: Boolean,
    isFavorite: Boolean = false,
    onSave: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(
                    if (isAvailable) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (isAvailable) Color(0xFF4CAF50) else Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(ip, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (isAvailable && !isSaved) {
                    Text("New device detected!", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                }
            }

            if (onToggleFavorite != null) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.Gray
                    )
                }
            }

            if (!isSaved && onSave != null) {
                Button(onClick = onSave, shape = RoundedCornerShape(12.dp)) {
                    Text("Remember")
                }
            } else if (onForget != null) {
                IconButton(onClick = onForget) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Forget", tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun NetworkHistoryItem(
    item: NetworkWithTvs,
    onToggleFavorite: (ConnectedTV) -> Unit,
    onForget: (ConnectedTV) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(item.network.ssid, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            Text("Last connected: ${sdf.format(Date(item.network.lastConnected))}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                item.tvs.forEach { tv ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tv.name, style = MaterialTheme.typography.bodyMedium)
                            Text(tv.ipAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        
                        IconButton(onClick = { onToggleFavorite(tv) }) {
                            Icon(
                                if (tv.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (tv.isFavorite) Color.Red else Color.Gray
                            )
                        }
                        IconButton(onClick = { onForget(tv) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Forget", modifier = Modifier.size(20.dp), tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
