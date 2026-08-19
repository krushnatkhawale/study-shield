package com.kaushalya.interrupter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaushalya.interrupter.R
import com.kaushalya.interrupter.data.*
import com.kaushalya.interrupter.ui.parents.ParentManagementScreen
import com.kaushalya.interrupter.ui.quiz.QuizSetupScreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Option1 : Screen("control", "Library", Icons.AutoMirrored.Filled.LibraryBooks)
    object ConnectedTvs : Screen("connected_tvs", "Connected TVs", Icons.Default.Tv)
    object Kids : Screen("kids", "Kids", Icons.Default.ChildCare)
    object QuizSetup : Screen("quiz_setup", "Quiz Setup", Icons.Default.Quiz)
    object Parents : Screen("parents", "Parents", Icons.Default.People)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object SessionResults : Screen("session_results", "Results", Icons.Default.Assessment)

    // Dev / Debug (not in drawer by default)
    object ProfData : Screen("profdata", "ProfData", Icons.Default.Info)

    // Study Flow (not in drawer)
    object StudyStart : Screen("study_start", "Start Study", Icons.Default.School)
    object Scheduler : Screen("scheduler", "Scheduler", Icons.Default.Event)
    object ContentSelection : Screen("content", "Select Content", Icons.AutoMirrored.Filled.List)

    // Kid Form (not in drawer)
    object KidForm : Screen("kid_form", "Kid Profile", Icons.Default.ChildCare)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    studyViewModel: StudyViewModel,
    sessionManager: SessionManager,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isGuest = sessionManager.isGuest

    val items = buildList {
        add(Screen.Home)
        add(Screen.Option1)
        add(Screen.ConnectedTvs)
        add(Screen.Kids)
        add(Screen.SessionResults)
        if (!isGuest) {
            add(Screen.QuizSetup)
            add(Screen.Parents)
        }
        add(Screen.Settings)
        add(Screen.ProfData)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(sessionManager)
                Spacer(modifier = Modifier.height(8.dp))
                items.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                if (!isGuest) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        label = { Text("Sign Out") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onSignOut()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                val title = items.find { it.route == currentRoute }?.title ?: "StudyShield"
                CenterAlignedTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Home.route) { StatsDashboardScreen() }
                composable(Screen.Option1.route) {
                    ControlScreen(studyViewModel, onStartStudy = { navController.navigate(Screen.StudyStart.route) })
                }
                composable(Screen.ConnectedTvs.route) { TvManagementScreen() }
                composable(Screen.SessionResults.route) {
                    val resultViewModel: SessionResultViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    SessionResultScreen(
                        viewModel = resultViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Kids.route) {
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    KidProfileScreen(
                        viewModel = kidViewModel,
                        onAddKid = { navController.navigate(Screen.KidForm.route) },
                        onEditKid = { kid ->
                            kidViewModel.editingKid = kid
                            navController.navigate(Screen.KidForm.route)
                        }
                    )
                }
                composable(Screen.KidForm.route) {
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    KidFormScreen(
                        viewModel = kidViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                if (!isGuest) {
                    composable(Screen.QuizSetup.route) { QuizSetupScreen(onBack = { navController.popBackStack() }) }
                    composable(Screen.Parents.route) { ParentManagementScreen(sessionManager = sessionManager, onBack = { navController.popBackStack() }) }
                }
                composable(Screen.Settings.route) { SettingsScreen(studyViewModel) }
                composable(Screen.ProfData.route) { ProfDataScreen() }

                // Study Flow
                composable(Screen.StudyStart.route) {
                    StartStudyScreen(
                        viewModel = studyViewModel,
                        onNavigateToScheduler = { navController.navigate(Screen.Scheduler.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Scheduler.route) {
                    SchedulerScreen(
                        viewModel = studyViewModel,
                        onNavigateToContent = { navController.navigate(Screen.ContentSelection.route) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.ContentSelection.route) {
                    ContentSelectionScreen(
                        viewModel = studyViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerHeader(sessionManager: SessionManager) {
    val displayName = if (sessionManager.isGuest) {
        "Guest"
    } else {
        sessionManager.parentName ?: sessionManager.loginId ?: "User"
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Name") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = editName.trim()
                    if (trimmed.isNotBlank()) {
                        sessionManager.parentName = trimmed
                        scope.launch {
                            try {
                                ParentRepository().updateMyName(trimmed)
                            } catch (_: Exception) {}
                        }
                    }
                    showEditDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (sessionManager.isGuest) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (sessionManager.isGuest) "Guest Mode" else "Welcome back!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!sessionManager.isGuest) {
                IconButton(onClick = {
                    editName = sessionManager.parentName ?: sessionManager.loginId ?: ""
                    showEditDialog = true
                }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit name",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StatsDashboardScreen() {
    val context = LocalContext.current
    val resultViewModel: SessionResultViewModel = viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity
    )
    val recentResults by resultViewModel.recentResults.collectAsState()
    val totalSessions = recentResults.size
    val totalCorrect = recentResults.sumOf { it.score }
    val totalQuestions = recentResults.sumOf { it.totalQuestions }
    val avgPercentage = if (totalQuestions > 0) (totalCorrect * 100 / totalQuestions) else 0
    val totalTimeMinutes = recentResults.sumOf { it.timeSpentSeconds } / 60

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Home - Statistics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Study Minutes", "$totalTimeMinutes", Icons.Default.Timer, Modifier.weight(1f), Color(0xFF1E88E5))
                StatCard("Sessions", "$totalSessions", Icons.Default.CheckCircle, Modifier.weight(1f), Color(0xFF43A047))
            }
        }
        item {
            StatCard("Correct Answers", "$avgPercentage%", Icons.AutoMirrored.Filled.TrendingUp, Modifier.fillMaxWidth(), Color(0xFFFF6B00))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Recent Activity", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (recentResults.isEmpty()) {
                        Text("No quiz sessions yet. Start a study session to see results here.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        recentResults.take(5).forEach { result ->
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val percentage = if (result.totalQuestions > 0) (result.score * 100 / result.totalQuestions) else 0
                            Text(
                                "• ${result.childName} - ${result.score}/${result.totalQuestions} ($percentage%) at ${sdf.format(Date(result.completedAt))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(viewModel: StudyViewModel, onStartStudy: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val discoveredTvs by viewModel.discoveredTvs.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = onStartStudy,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
            ) {
                Text("🎓 START STUDY NOW", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // TV Connection Card
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📡 TV CONNECTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = viewModel.manualIp,
                        onValueChange = { viewModel.manualIp = it },
                        label = { Text("TV IP Address") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (viewModel.isDiscovering) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { viewModel.startDiscovery() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }
                        }
                    )
                    
                    if (discoveredTvs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Discovered TVs:", style = MaterialTheme.typography.labelSmall)
                        discoveredTvs.forEach { tv ->
                            val ip = tv.host?.hostAddress ?: ""
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { viewModel.manualIp = ip; viewModel.selectedTvIp = ip },
                                color = if (viewModel.manualIp == ip) Color(0xFFE3F2FD) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp),
                                border = if (viewModel.manualIp == ip) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5)) else null
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Tv, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tv.serviceName, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        val statusText = if (viewModel.isDiscovering) "Searching for TVs..." else "Scan stopped. Click refresh to try again."
                        Text(statusText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp), color = Color.Gray)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚙️ INTERRUPTION SETUP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Select Mode")
                    val modes = listOf("Infinite Block", "Timed Break", "Quick Quiz (MCQ)", "Fill In The Blank")
                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(modes[viewModel.manualMode])
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            modes.forEachIndexed { index, mode ->
                                DropdownMenuItem(text = { Text(mode) }, onClick = { 
                                    viewModel.manualMode = index
                                    expanded = false 
                                })
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.manualMessage,
                        onValueChange = { viewModel.manualMessage = it },
                        label = { Text("Main Message or Question") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Dynamic Sections
                    when (viewModel.manualMode) {
                        1 -> { // Timer
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = viewModel.manualDuration,
                                    onValueChange = { viewModel.manualDuration = it },
                                    label = { Text("Duration") },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Simple Unit selector
                                var unitExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { unitExpanded = true }) {
                                        Text(viewModel.manualUnit)
                                    }
                                    DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                        listOf("Seconds", "Minutes", "Hours").forEach { unit ->
                                            DropdownMenuItem(text = { Text(unit) }, onClick = { 
                                                viewModel.manualUnit = unit
                                                unitExpanded = false 
                                            })
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // MCQ
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                viewModel.manualMcqOptions.forEachIndexed { index, option ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = viewModel.manualMcqCorrectIndex == index, onClick = { viewModel.manualMcqCorrectIndex = index })
                                        OutlinedTextField(
                                            value = option,
                                            onValueChange = { viewModel.manualMcqOptions[index] = it },
                                            label = { Text("Option ${index + 1}") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        3 -> { // FITB
                            OutlinedTextField(
                                value = viewModel.manualFitbAnswer,
                                onValueChange = { viewModel.manualFitbAnswer = it },
                                label = { Text("Correct Answer") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.sendManualCommand() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("🚀 ACTIVATE INTERRUPTER", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Button(
                onClick = { viewModel.sendManualCommand(isUnlock = true) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("🔓 EMERGENCY UNLOCK", fontWeight = FontWeight.Bold)
            }
        }
    }
    
    // Status message
    when (val state = uiState) {
        is StudyUiState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                confirmButton = { TextButton(onClick = { viewModel.resetState() }) { Text("OK") } },
                text = { Text(state.message) }
            )
        }
        is StudyUiState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                confirmButton = { TextButton(onClick = { viewModel.resetState() }) { Text("OK") } },
                text = { Text(state.message) }
            )
        }
        else -> {}
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SettingsScreen(viewModel: StudyViewModel) {
    val discoveredTvs by viewModel.discoveredTvs.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("TV Connections", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        if (discoveredTvs.isEmpty()) {
            item {
                Text("Searching for TVs...", color = Color.Gray)
            }
        }

        items(discoveredTvs) { tv ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectedTvIp = tv.host?.hostAddress },
                colors = CardDefaults.cardColors(
                    containerColor = if (viewModel.selectedTvIp == tv.host?.hostAddress) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tv, null, tint = Color(0xFF1E88E5))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(tv.serviceName, fontWeight = FontWeight.Bold)
                        Text(tv.host?.hostAddress ?: "Resolving...", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (viewModel.selectedTvIp == tv.host?.hostAddress) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1E88E5))
                    }
                }
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Text("General Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        item {
            ListItem(
                headlineContent = { Text("Parental PIN") },
                supportingContent = { Text("Require PIN to unlock manually") },
                trailingContent = { Switch(checked = true, onCheckedChange = {}) }
            )
        }
        
        item {
            ListItem(
                headlineContent = { Text("Auto-Discovery") },
                supportingContent = { Text("Search for TVs on app launch") },
                trailingContent = { Switch(checked = true, onCheckedChange = {}) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartStudyScreen(
    viewModel: StudyViewModel,
    onNavigateToScheduler: () -> Unit,
    onBack: () -> Unit
) {
    val discoveredTvs by viewModel.discoveredTvs.collectAsState()
    val scheduledSessions by viewModel.scheduledSessions.collectAsState(initial = emptyList())
    
    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.start_study_now)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "🚀", fontSize = 60.sp)
                Text(
                    text = stringResource(R.string.start_study_now),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B00)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // TV Selection Section
            item {
                Text("1. Select Target TV", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                if (discoveredTvs.isEmpty()) {
                    val statusText = if (viewModel.isDiscovering) "Searching..." else "No TV found"
                    Text(statusText, color = if (viewModel.isDiscovering) Color.Gray else Color.Red, fontSize = 14.sp)
                    Button(onClick = { viewModel.startDiscovery() }, modifier = Modifier.padding(top = 8.dp), enabled = !viewModel.isDiscovering) {
                        Text(stringResource(R.string.connect_now))
                    }
                }
            }

            items(discoveredTvs) { tv ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.selectedTvIp = tv.host?.hostAddress },
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.selectedTvIp == tv.host?.hostAddress) 
                            Color(0xFFE3F2FD) else Color.White
                    ),
                    border = if (viewModel.selectedTvIp == tv.host?.hostAddress)
                        androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1E88E5)) else null
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFF1E88E5))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(tv.serviceName, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onNavigateToScheduler,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = viewModel.selectedTvIp != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    Text("Setup Session Plan", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Scheduled Sessions Section
            if (scheduledSessions.isNotEmpty()) {
                item {
                    Text("Scheduled Sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(scheduledSessions) { session ->
                    ScheduledSessionItem(session)
                }
            }
        }
    }
}

@Composable
fun ScheduledSessionItem(session: StudySession) {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F5))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(session.content.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${session.durationMinutes} min • ${session.recurrence.name} • ${sdf.format(Date(session.startTime))}", 
                    fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(
    viewModel: StudyViewModel,
    onNavigateToContent: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_session_scheduler)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
        ) {
            Text(stringResource(R.string.duration), style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    FilterChip(
                        selected = viewModel.sessionDuration == mins,
                        onClick = { viewModel.setDuration(mins) },
                        label = { Text("$mins min") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Start Time", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                FilterChip(
                    selected = !viewModel.isScheduled,
                    onClick = { viewModel.isScheduled = false },
                    label = { Text("Start Now") },
                    leadingIcon = { if (!viewModel.isScheduled) Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = viewModel.isScheduled,
                    onClick = { viewModel.isScheduled = true },
                    label = { Text("Schedule for Later") },
                    leadingIcon = { if (viewModel.isScheduled) Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(stringResource(R.string.recurrence), style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Recurrence.entries.forEach { rec ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setRecurrenceType(rec) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = viewModel.recurrence == rec, onClick = { viewModel.setRecurrenceType(rec) })
                        Text(rec.name.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onNavigateToContent,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
            ) {
                Text("Next: Select Content", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSelectionScreen(
    viewModel: StudyViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val previewContent = viewModel.previewContent
    
    val contents = listOf(
        StudyContent(ContentType.QUIZ, "q1", "Math Quiz: Grade 4", "Math", 
            questions = listOf(
                QuizQuestion("What is 1/2 + 1/4?", listOf("1/4", "3/4", "1/2", "1"), "1"),
                QuizQuestion("What is 15 x 6?", listOf("75", "80", "90", "100"), "2"),
                QuizQuestion("Square root of 144?", listOf("10", "12", "14", "16"), "1"),
                QuizQuestion("What is 100 - 37?", listOf("53", "63", "73", "83"), "1"),
                QuizQuestion("What is 9 x 9?", listOf("72", "81", "90", "99"), "1"),
                QuizQuestion("25% of 200?", listOf("25", "50", "75", "100"), "1"),
                QuizQuestion("What is 1000 / 25?", listOf("20", "30", "40", "50"), "2"),
                QuizQuestion("Next number: 2, 4, 8, 16, ...", listOf("24", "30", "32", "40"), "2"),
                QuizQuestion("What is 7 + 8 x 2?", listOf("23", "30", "17", "25"), "0"),
                QuizQuestion("Angle of a straight line?", listOf("90", "180", "270", "360"), "1")
            )
        ),
        StudyContent(ContentType.QUIZ, "q2", "General Knowledge: Junior", "GK",
            questions = listOf(
                QuizQuestion("Capital of France?", listOf("London", "Paris", "Berlin", "Rome"), "1"),
                QuizQuestion("Largest planet?", listOf("Mars", "Venus", "Jupiter", "Saturn"), "2"),
                QuizQuestion("Who painted Mona Lisa?", listOf("Da Vinci", "Picasso", "Van Gogh", "Monet"), "0"),
                QuizQuestion("Continent with Sahara Desert?", listOf("Asia", "Africa", "Australia", "Europe"), "1"),
                QuizQuestion("Fastest land animal?", listOf("Lion", "Cheetah", "Tiger", "Horse"), "1"),
                QuizQuestion("Number of colors in rainbow?", listOf("5", "6", "7", "8"), "2"),
                QuizQuestion("Largest ocean?", listOf("Atlantic", "Indian", "Arctic", "Pacific"), "3"),
                QuizQuestion("Currency of Japan?", listOf("Yuan", "Yen", "Won", "Dollar"), "1"),
                QuizQuestion("Tallest mountain?", listOf("K2", "Mt. Everest", "Makalu", "Lhotse"), "1"),
                QuizQuestion("Smallest continent?", listOf("Europe", "Antarctica", "Australia", "South America"), "2")
            )
        ),
        StudyContent(ContentType.QUIZ, "q3", "Science: Fun Facts", "Science",
            questions = listOf(
                QuizQuestion("Symbol for Water?", listOf("H2O", "CO2", "O2", "NaCl"), "0"),
                QuizQuestion("Planet closest to the Sun?", listOf("Venus", "Earth", "Mercury", "Mars"), "2"),
                QuizQuestion("Gas we breathe in?", listOf("Nitrogen", "Oxygen", "Carbon Dioxide", "Hydrogen"), "1"),
                QuizQuestion("Part of plant that makes food?", listOf("Root", "Stem", "Leaf", "Flower"), "2"),
                QuizQuestion("Freezing point of water (°C)?", listOf("-10", "0", "10", "100"), "1"),
                QuizQuestion("Hardest natural substance?", listOf("Gold", "Iron", "Diamond", "Stone"), "2"),
                QuizQuestion("Human body has how many lungs?", listOf("1", "2", "3", "4"), "1"),
                QuizQuestion("Study of stars is called?", listOf("Astrology", "Astronomy", "Biology", "Geology"), "1"),
                QuizQuestion("Center of an atom?", listOf("Electron", "Proton", "Nucleus", "Neutron"), "2"),
                QuizQuestion("Force that pulls things to Earth?", listOf("Friction", "Magnetism", "Gravity", "Electricity"), "2")
            )
        ),
        StudyContent(ContentType.SUBJECT, null, "Science Exploration", "Science"),
        StudyContent(ContentType.RANDOM, null, "Random Mix", "General")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_content)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(contents) { content ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectContent(content) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (viewModel.selectedContent == content) Color(0xFFFFF3E0) else Color.White
                            ),
                            border = if (viewModel.selectedContent == content) 
                                androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF6B00)) else null
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(content.name, fontWeight = FontWeight.Bold)
                                    Text(content.category ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                
                                if (content.type == ContentType.QUIZ && !content.questions.isNullOrEmpty()) {
                                    IconButton(onClick = { viewModel.showPreview(content) }) {
                                        Icon(Icons.Default.Visibility, contentDescription = "Preview Questions", tint = Color(0xFF1E88E5))
                                    }
                                }

                                if (viewModel.selectedContent == content) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFFF6B00))
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.startStudySession() },
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = viewModel.selectedContent != null && uiState !is StudyUiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
                ) {
                    if (uiState is StudyUiState.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        val text = if (viewModel.isScheduled) "Schedule Session" else stringResource(R.string.start_session)
                        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Status Overlays
            when (val state = uiState) {
                is StudyUiState.Success -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.resetState() },
                        confirmButton = { Button(onClick = { viewModel.resetState() }) { Text("OK") } },
                        title = { Text("Session Confirmed") },
                        text = { Text(state.message) }
                    )
                }
                is StudyUiState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.resetState() },
                        confirmButton = { Button(onClick = { viewModel.resetState() }) { Text("OK") } },
                        title = { Text("Error") },
                        text = { Text(state.message) }
                    )
                }
                else -> {}
            }

            // Preview Dialog
            if (previewContent != null) {
                QuizPreviewDialog(
                    content = previewContent,
                    onDismiss = { viewModel.hidePreview() },
                    onLaunchNow = { viewModel.startStudySession(previewContent) }
                )
            }
        }
    }
}

@Composable
fun QuizPreviewDialog(
    content: StudyContent,
    onDismiss: () -> Unit,
    onLaunchNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFFFF6B00))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review Questions")
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Text(content.name, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(content.questions ?: emptyList()) { q ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(q.question, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(8.dp))
                                q.options.forEachIndexed { index, option ->
                                    val isCorrect = index.toString() == q.answer
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isCorrect) Color(0xFF38A169) else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(option, fontSize = 14.sp, color = if (isCorrect) Color(0xFF2E7D32) else Color.Unspecified)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("These questions will appear randomly during interruptions.", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = onLaunchNow,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B00))
            ) {
                Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Launch on TV")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
