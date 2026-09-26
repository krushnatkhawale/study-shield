package com.kaushalya.interrupter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.kaushalya.interrupter.network.RetrofitClient
import com.kaushalya.interrupter.ui.auth.AuthViewModel
import com.kaushalya.interrupter.ui.auth.GuestSignUpState
import com.kaushalya.interrupter.ui.auth.SignUpScreen
import com.kaushalya.interrupter.ui.parents.ParentManagementScreen
import com.kaushalya.interrupter.ui.quiz.QuizReviewScreen
import com.kaushalya.interrupter.ui.quiz.QuizSetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Home2 : Screen("home2", "Home2 (experimental)", Icons.Default.Science)
    object QuickActions : Screen("quick_actions", "Quick Actions", Icons.Default.Bolt)
    object Option1 : Screen("control", "Library", Icons.AutoMirrored.Filled.LibraryBooks)
    object ConnectedTvs : Screen("connected_tvs", "Connected TVs", Icons.Default.Tv)
    object TvVoiceHelp : Screen("tv_voice_help", "TV Voice Help", Icons.Default.RecordVoiceOver)
    object Kids : Screen("kids", "Kids", Icons.Default.ChildCare)
    object QuizSetup : Screen("quiz_setup", "Quiz Setup", Icons.Default.Quiz)
    object Parents : Screen("parents", "Parents", Icons.Default.People)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object SessionResults : Screen("session_results", "Results", Icons.Default.Assessment)

    // Dev / Debug (not in drawer by default)
    object ProfData : Screen("profdata", "ProfData", Icons.Default.Info)

    // Study Flow (not in drawer)
    object ContentSelection : Screen("content", "Select Content", Icons.AutoMirrored.Filled.List)

    // Kid Form (not in drawer)
    object KidForm : Screen("kid_form", "Kid Profile", Icons.Default.ChildCare)

    // Kid Detail (full-screen profile + performance; not in drawer)
    object KidDetail : Screen("kid_detail", "Kid Profile", Icons.Default.ChildCare)

    // Quiz Review (not in drawer)
    object QuizReview : Screen("quiz_review", "Quiz Review", Icons.Default.Visibility)

    object QuizAnalytics : Screen("quiz_analytics", "Quiz Stats", Icons.AutoMirrored.Filled.TrendingUp)
}

// Guest-only flow (not in the drawer item list; reached from the guest drawer actions)
private val GuestSignUpRoute = "guest_signup"

/** Transient holder for the pack being reviewed, passed between nav destinations. */
object QuizReviewTarget {
    var pack: StudyContent? = null
}

/** Transient holder for the kid whose full profile/detail page is being viewed. */
object KidDetailTarget {
    var kid: KidProfile? = null
}

/** Transient holder for the quiz + kid whose analytics screen is being viewed. */
object QuizAnalyticsTarget {
    var packName: String? = null
    var kidName: String? = null
    var kidPhotoUri: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    studyViewModel: StudyViewModel,
    sessionManager: SessionManager,
    onSignOut: () -> Unit,
    onGuestLogout: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isGuest = sessionManager.isGuest

    val items = buildList {
        add(Screen.Home)
        add(Screen.Home2)
        add(Screen.QuickActions)
        add(Screen.Option1)
        add(Screen.ConnectedTvs)
        add(Screen.TvVoiceHelp)
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
                                // Only reset the stack when going Home; otherwise
                                // preserve history so system/app back returns
                                // to the previous screen, not Home.
                                if (screen.route == Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                }
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
                } else {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                        label = { Text("Create Account") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(GuestSignUpRoute) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        label = { Text("Log Out") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onGuestLogout()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        val inDrawerScreen = items.any { it.route == currentRoute }
        Scaffold(
            topBar = {
                if (inDrawerScreen) {
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
            },
            // Full-screen flows (Select Content, Kid Form/Detail, Quiz Review) render their own
            // top bar; suppress the outer scaffold insets so their bar sits at the top and the
            // double-title blank space is removed.
            contentWindowInsets = if (inDrawerScreen) ScaffoldDefaults.contentWindowInsets else WindowInsets(0)
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Home.route) {
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    StatsDashboardScreen(
                        sessionManager = sessionManager,
                        onEditKid = { kid ->
                            kidViewModel.editingKid = kid
                            navController.navigate(Screen.KidForm.route)
                        },
                        onStartQuiz = { navController.navigate(Screen.ContentSelection.route) },
                        onPlayAgain = {
                            if (!studyViewModel.replayLastSession()) {
                                navController.navigate(Screen.ConnectedTvs.route)
                            }
                        }
                    )
                }
                composable(Screen.Home2.route) {
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    Home2Screen(
                        sessionManager = sessionManager,
                        studyViewModel = studyViewModel,
                        kidViewModel = kidViewModel,
                        navController = navController
                    )
                }
                composable(Screen.QuickActions.route) {
                    QuickActionsScreen(studyViewModel = studyViewModel)
                }
                composable(Screen.Option1.route) {
                    // Start Study Now always goes straight to Select Content —
                    // no intermediate kid/TV selection screen. TV selection lives
                    // in Connected TVs; the kid is picked when a pack card is tapped.
                    ControlScreen(
                        studyViewModel,
                        onStartStudy = {
                            navController.navigate(Screen.ContentSelection.route)
                        }
                    )
                }
                composable(Screen.ConnectedTvs.route) { TvManagementScreen() }
                composable(Screen.TvVoiceHelp.route) { TvVoiceHelpScreen() }
                composable(Screen.SessionResults.route) {
                    val resultViewModel: SessionResultViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    SessionResultScreen(
                        viewModel = resultViewModel,
                        kidViewModel = kidViewModel,
                        sessionManager = sessionManager,
                        onEditKid = { kid ->
                            kidViewModel.editingKid = kid
                            navController.navigate(Screen.KidForm.route)
                        },
                        onPlayAgain = {
                            if (!studyViewModel.replayLastSession()) {
                                navController.navigate(Screen.ConnectedTvs.route)
                            }
                        },
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
                        onSelectKid = { kid ->
                            KidDetailTarget.kid = kid
                            navController.navigate(Screen.KidDetail.route)
                        }
                    )
                }
                composable(Screen.KidDetail.route) {
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    val resultViewModel: SessionResultViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    KidDetailScreen(
                        kid = KidDetailTarget.kid,
                        kidViewModel = kidViewModel,
                        resultViewModel = resultViewModel,
                        sessionManager = sessionManager,
                        onEditProfile = { kid ->
                            kidViewModel.editingKid = kid
                            navController.navigate(Screen.KidForm.route)
                        },
                        onStartQuiz = { navController.navigate(Screen.ContentSelection.route) },
                        onBack = { navController.popBackStack() }
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
                    composable(Screen.QuizSetup.route) {
                        val kidViewModel: KidProfileViewModel = viewModel(
                            viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                        )
                        QuizSetupScreen(
                            onBack = { navController.popBackStack() },
                            kidViewModel = kidViewModel,
                            sessionManager = sessionManager
                        )
                    }
                    composable(Screen.Parents.route) { ParentManagementScreen(sessionManager = sessionManager, onBack = { navController.popBackStack() }) }
                }
                composable(Screen.Settings.route) { SettingsScreen(studyViewModel) }
                composable(GuestSignUpRoute) {
                    val activity = LocalContext.current as androidx.activity.ComponentActivity
                    val authViewModel: AuthViewModel = viewModel(viewModelStoreOwner = activity)
                    val guestSignUpState by authViewModel.guestSignUpState.collectAsState()
                    DisposableEffect(Unit) {
                        onDispose { authViewModel.resetGuestSignUp() }
                    }
                    LaunchedEffect(guestSignUpState) {
                        if (guestSignUpState is GuestSignUpState.Success) {
                            authViewModel.resetGuestSignUp()
                            navController.popBackStack()
                        }
                    }
                    SignUpScreen(
                        onSignUp = { loginId, password, name ->
                            authViewModel.resetGuestSignUp()
                            authViewModel.signUpFromGuest(loginId, password, name)
                        },
                        onBack = {
                            authViewModel.resetGuestSignUp()
                            navController.popBackStack()
                        },
                        isLoading = guestSignUpState is GuestSignUpState.Loading,
                        error = (guestSignUpState as? GuestSignUpState.Error)?.message,
                        notice = "Your guest progress (kids, scores, sessions) will be saved to your new account.",
                        backLabel = "Cancel"
                    )
                }
                composable(Screen.ProfData.route) { ProfDataScreen() }

                // Study Flow
                composable(Screen.ContentSelection.route) {
                    val kidViewModel: KidProfileViewModel = viewModel(
                        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity
                    )
                    ContentSelectionScreen(
                        viewModel = studyViewModel,
                        sessionManager = sessionManager,
                        kidViewModel = kidViewModel,
                        onReviewPack = { pack ->
                            QuizReviewTarget.pack = pack
                            navController.navigate(Screen.QuizReview.route)
                        },
                        onOpenAnalytics = { packName, kidName, kidPhotoUri ->
                            QuizAnalyticsTarget.packName = packName
                            QuizAnalyticsTarget.kidName = kidName
                            QuizAnalyticsTarget.kidPhotoUri = kidPhotoUri
                            navController.navigate(Screen.QuizAnalytics.route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.QuizReview.route) {
                    QuizReviewScreen(
                        pack = QuizReviewTarget.pack,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.QuizAnalytics.route) {
                    val packName = QuizAnalyticsTarget.packName
                    val kidName = QuizAnalyticsTarget.kidName
                    if (packName == null || kidName == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        QuizAnalyticsScreen(
                            packName = packName,
                            kidName = kidName,
                            kidPhotoUri = QuizAnalyticsTarget.kidPhotoUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

/** Parent-hub home: greeting + kid switcher, then dynamic cards only + top-3 parent jobs. */
private val Home2Accent = Color(0xFFFF6B00)

@Composable
fun Home2Screen(
    sessionManager: SessionManager,
    studyViewModel: StudyViewModel,
    kidViewModel: KidProfileViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val resultViewModel: SessionResultViewModel = viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity
    )
    val kidProfiles by kidViewModel.kidProfiles.collectAsState()
    val recentResults by resultViewModel.recentResults.collectAsState()
    var activeKidTick by remember { mutableStateOf(0) }

    val activeKid = remember(kidProfiles, sessionManager.selectedKidId, activeKidTick) {
        kidProfiles.firstOrNull { it.id == sessionManager.selectedKidId } ?: kidProfiles.firstOrNull()
    }
    val kidResults = remember(recentResults, activeKid) {
        if (activeKid == null) emptyList() else recentResults.filter { it.childName == activeKid.name }
    }
    val lastResult = remember(kidResults) { kidResults.maxByOrNull { it.completedAt } }
    val attentionItems = remember(kidResults) {
        kidResults.filter {
            val t = it.totalQuestions
            t > 0 && (it.score * 100 / t) < 30
        }.distinctBy { it.contentName ?: "" }.take(3)
    }
    val greetingWord = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val parentDisplayName = if (sessionManager.isGuest) null
        else sessionManager.parentName ?: sessionManager.loginId
    val greeting = if (parentDisplayName.isNullOrBlank()) greetingWord
        else "$greetingWord, $parentDisplayName"

    var goalProgress by remember { mutableStateOf<List<GoalProgressDto>>(emptyList()) }
    LaunchedEffect(activeKid?.name) {
        val kidName = activeKid?.name
        if (kidName.isNullOrBlank()) {
            goalProgress = emptyList()
        } else {
            goalProgress = try {
                withContext(Dispatchers.IO) {
                    val resp = RetrofitClient.getApiService().getGoalsProgress(kidName)
                    if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun playAgain() {
        if (!studyViewModel.replayLastSession()) {
            navController.navigate(Screen.ConnectedTvs.route)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        if (kidProfiles.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Screen.Kids.route) },
                    colors = CardDefaults.cardColors(containerColor = Home2Accent)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ChildCare, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Add your first kid", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Create a kid profile to get started", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                    }
                }
            }
        } else {
            item {
                if (activeKid != null) {
                    Text(
                        "Showing for ${activeKid.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        if (lastResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Home2Accent)
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continue learning", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                            Text(
                                lastResult.contentName ?: "Last quiz",
                                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { playAgain() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Home2Accent)
                        ) { Text("Resume", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (attentionItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Needs attention", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        Spacer(modifier = Modifier.height(8.dp))
                        attentionItems.forEach { r ->
                            val pct = if (r.totalQuestions > 0) (r.score * 100 / r.totalQuestions) else 0
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    QuizAnalyticsTarget.packName = r.contentName
                                    QuizAnalyticsTarget.kidName = r.childName
                                    QuizAnalyticsTarget.kidPhotoUri = activeKid?.photoUri
                                    navController.navigate(Screen.QuizAnalytics.route)
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${r.contentName ?: "Quiz"} · $pct%",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }

        if (activeKid != null && goalProgress.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Goals", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        goalProgress.forEach { goal ->
                            if (goal.achieved) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Home2Accent.copy(alpha = 0.12f))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Home2Accent, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${activeKid.name} hit ${goal.target} ${goalTypeLabel(goal.type)} this week!",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                val remaining = (goal.target - goal.current).coerceAtLeast(0)
                                val fraction = if (goal.target > 0) (goal.current.toFloat() / goal.target).coerceIn(0f, 1f) else 0f
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(
                                        "${goal.current} of ${goal.target} ${goalTypeLabel(goal.type)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$remaining more to go",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeKid != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recent activity", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val recent = kidResults.sortedByDescending { it.completedAt }.take(5)
                        if (recent.isEmpty()) {
                            Text("No quizzes yet — start one to see progress here.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            recent.forEach { result ->
                                val percentage = if (result.totalQuestions > 0) (result.score * 100 / result.totalQuestions) else 0
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    KidAvatar(photoUri = activeKid.photoUri, name = result.childName, size = 24.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "• ${result.score}/${result.totalQuestions} ($percentage%) · ${friendlyDay(result.completedAt)}, ${friendlyTime(result.completedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Home2JobCard("Start Quiz", Icons.Default.PlayArrow, Modifier.weight(1f)) {
                        navController.navigate(Screen.ContentSelection.route)
                    }
                    Home2JobCard("Results", Icons.Default.Assessment, Modifier.weight(1f)) {
                        navController.navigate(Screen.SessionResults.route)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Home2JobCard("Kids", Icons.Default.ChildCare, Modifier.weight(1f)) {
                        navController.navigate(Screen.Kids.route)
                    }
                    Home2JobCard("Quick Actions", Icons.Default.Bolt, Modifier.weight(1f)) {
                        navController.navigate(Screen.QuickActions.route)
                    }
                }
            }
        }
    }
}

private fun goalTypeLabel(type: String): String = when (type) {
    "WEEKLY_BEST" -> "Best-score quizzes"
    else -> type.lowercase().replace('_', ' ')
}

@Composable
fun QuickActionsScreen(studyViewModel: StudyViewModel) {
    val uiState by studyViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var triggerType by remember { mutableStateOf(0) } // 0 infinite block, 1 timed break, 2 scheduled
    var durationSecs by remember { mutableStateOf("15") }
    var pendingJob by remember { mutableStateOf<Job?>(null) }
    var pendingInfo by remember { mutableStateOf<String?>(null) }
    var tvExpanded by remember { mutableStateOf(false) }
    val discoveredTvs by studyViewModel.discoveredTvs.collectAsState()
    val selectedTv = discoveredTvs.find { it.host?.hostAddress == studyViewModel.selectedTvIp }

    fun fireNow() {
        when (triggerType) {
            0 -> { studyViewModel.manualMode = 0; studyViewModel.sendManualCommand() }
            1 -> {
                studyViewModel.manualMode = 1
                studyViewModel.manualUnit = "Seconds"
                studyViewModel.manualDuration = durationSecs.ifBlank { "10" }
                studyViewModel.sendManualCommand()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val msg = when (val s = uiState) {
                is StudyUiState.Success -> s.message
                is StudyUiState.Error -> s.message
                else -> null
            }
            if (msg != null) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState is StudyUiState.Error) Color(0xFFC62828) else Color(0xFF2E7D32)
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TV", fontWeight = FontWeight.Bold, color = Home2Accent)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { tvExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    selectedTv?.serviceName
                                        ?: selectedTv?.host?.hostAddress
                                        ?: "No TV selected",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = tvExpanded, onDismissRequest = { tvExpanded = false }) {
                                if (discoveredTvs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No TVs found — tap refresh", color = Color.Gray) },
                                        onClick = { tvExpanded = false }
                                    )
                                } else {
                                    discoveredTvs.forEach { tv ->
                                        val ip = tv.host?.hostAddress
                                        DropdownMenuItem(
                                            text = { Text(tv.serviceName ?: ip ?: "Unknown TV") },
                                            trailingIcon = {
                                                if (ip != null && ip == studyViewModel.selectedTvIp) {
                                                    Icon(Icons.Default.CheckCircle, null, tint = Home2Accent)
                                                }
                                            },
                                            onClick = {
                                                studyViewModel.selectedTvIp = ip
                                                tvExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { studyViewModel.startDiscovery() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Find TVs")
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Action", fontWeight = FontWeight.Bold, color = Home2Accent)
                    Spacer(modifier = Modifier.height(4.dp))
                    val types = listOf("Infinite block", "Timed break", "Scheduled message")
                    types.forEachIndexed { index, label ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { triggerType = index }
                        ) {
                            RadioButton(selected = triggerType == index, onClick = { triggerType = index })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = studyViewModel.manualMessage,
                        onValueChange = { studyViewModel.manualMessage = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (triggerType != 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = durationSecs,
                            onValueChange = { if (it.all { c -> c.isDigit() }) durationSecs = it },
                            label = { Text(if (triggerType == 1) "Break length (seconds)" else "Delay (seconds)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                if (triggerType == 2) {
                                    val secs = durationSecs.toLongOrNull() ?: 15L
                                    pendingJob?.cancel()
                                    pendingJob = scope.launch {
                                        pendingInfo = "Sending in ${secs}s…"
                                        delay(secs * 1000)
                                        studyViewModel.manualMode = 0
                                        studyViewModel.sendManualCommand()
                                        pendingInfo = null
                                        pendingJob = null
                                    }
                                } else {
                                    fireNow()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Home2Accent),
                            enabled = pendingJob == null
                        ) {
                            Text(
                                when (triggerType) {
                                    0 -> "Block now"
                                    1 -> "Start break"
                                    else -> "Schedule"
                                }
                            )
                        }
                        OutlinedButton(onClick = { studyViewModel.sendManualCommand(isUnlock = true) }) {
                            Text("Unblock")
                        }
                        if (pendingJob != null) {
                            Text(pendingInfo ?: "pending…", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            TextButton(onClick = { pendingJob?.cancel(); pendingJob = null; pendingInfo = null }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Home2JobCard(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Home2Accent)
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
fun StatsDashboardScreen(
    sessionManager: SessionManager,
    onEditKid: (KidProfile) -> Unit = {},
    onStartQuiz: () -> Unit = {},
    onPlayAgain: () -> Unit = {}
) {
    val context = LocalContext.current
    val resultViewModel: SessionResultViewModel = viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity
    )
    val recentResults by resultViewModel.recentResults.collectAsState()

    var selectedKidFilter by remember { mutableStateOf<String?>(null) }

    val filteredResults = remember(recentResults, selectedKidFilter) {
        if (selectedKidFilter == null) {
            recentResults
        } else {
            recentResults.filter { it.childName == selectedKidFilter }
        }
    }

    val totalSessions = filteredResults.size
    val totalCorrect = filteredResults.sumOf { it.score }
    val totalQuestions = filteredResults.sumOf { it.totalQuestions }
    val avgPercentage = if (totalQuestions > 0) (totalCorrect * 100 / totalQuestions) else 0
    val totalTimeMinutes = filteredResults.sumOf { it.timeSpentSeconds } / 60

    val kids = sessionManager.profile.kids
    // Local photo map (name -> photoUri) for avatars; ProfileKid has no photo field.
    var kidPhotos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    LaunchedEffect(Unit) {
        try {
            val dao = com.kaushalya.interrupter.data.AppDatabase.getDatabase(context).kidProfileDao()
            kidPhotos = dao.getAllKidsOnce().associate { it.name to it.photoUri }
        } catch (_: Exception) {}
    }
    val hasKid = kids.isNotEmpty()
    val hasTv = sessionManager.lastTvIp != null

    // One-time offer: after the default Trial kid finishes a test, invite the parent
    // to update the kid profile to unlock class-based tests.
    val expUpgradeKid by resultViewModel.expUpgradeKid.collectAsState()
    val expKid = expUpgradeKid
    if (expKid != null) {
        ExpUpgradePromptDialog(
            kidName = expKid.name,
            onUpdateKidInfo = {
                resultViewModel.markExpPromptHandled(expKid)
                onEditKid(expKid)
            },
            onDismiss = {
                resultViewModel.markExpPromptHandled(expKid)
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.home_statistics), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

if (hasKid && hasTv) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onStartQuiz),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6B00))
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.start_quiz), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.play_next_quiz_on_tv, kids.firstOrNull()?.name ?: stringResource(R.string.your_child)),
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        // SS-EXP-08: one-tap play again for the same child after a recent result
        val lastPlayAgainName = recentResults.maxByOrNull { it.completedAt }?.childName
        if (hasTv && lastPlayAgainName != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPlayAgain),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.play_again_for, lastPlayAgainName),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.play_next_quiz_on_tv, lastPlayAgainName),
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        if (kids.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedKidFilter == null,
                        onClick = { selectedKidFilter = null },
                        label = { Text(stringResource(R.string.all)) }
                    )
                    kids.forEach { kid ->
                        FilterChip(
                            selected = selectedKidFilter == kid.name,
                            onClick = { selectedKidFilter = kid.name },
                            leadingIcon = { KidAvatar(photoUri = kidPhotos[kid.name], name = kid.name, size = 24.dp) },
                            label = { Text(kid.name) }
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(stringResource(R.string.study_minutes), "$totalTimeMinutes", Icons.Default.Timer, Modifier.weight(1f), Color(0xFF1E88E5))
                StatCard(stringResource(R.string.sessions), "$totalSessions", Icons.Default.CheckCircle, Modifier.weight(1f), Color(0xFF43A047))
            }
        }
        item {
            StatCard(stringResource(R.string.correct_answers), "$avgPercentage%", Icons.AutoMirrored.Filled.TrendingUp, Modifier.fillMaxWidth(), Color(0xFFFF6B00))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.recent_activity), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredResults.isEmpty()) {
                        Text(stringResource(R.string.no_sessions_yet), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        filteredResults.take(5).forEach { result ->
                            val percentage = if (result.totalQuestions > 0) (result.score * 100 / result.totalQuestions) else 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                KidAvatar(photoUri = kidPhotos[result.childName], name = result.childName, size = 24.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "• ${result.score}/${result.totalQuestions} ($percentage%) · ${friendlyDay(result.completedAt)}, ${friendlyTime(result.completedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
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
                    Text(stringResource(R.string.manual_setup_title), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Select Mode")
                    val modes = listOf("Infinite Block", "Timed Break", "Quick Quiz (MCQ)", stringResource(R.string.manual_fill_blank))
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
                Text(stringResource(R.string.manual_activate), fontWeight = FontWeight.Bold)
            }
        }

        item {
            Button(
                onClick = { viewModel.sendManualCommand(isUnlock = true) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text(stringResource(R.string.manual_unlock), fontWeight = FontWeight.Bold)
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ContentSelectionScreen(
    viewModel: StudyViewModel,
    sessionManager: SessionManager,
    kidViewModel: KidProfileViewModel,
    onReviewPack: (StudyContent) -> Unit,
    onOpenAnalytics: (String, String, String?) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val kidProfiles by kidViewModel.kidProfiles.collectAsState()

    // Freemium packs segregated per kid: each kid gets their own section,
    // populated from packs matching that kid's class. Packs are cached locally
    // per user, so the backend is only hit on first download (or cache miss).
    val packCache = remember { PackCache(context) }
    var attemptsByPack by remember {
        mutableStateOf<Map<String, Triple<Int, QuizResult, Int>>>(emptyMap())
    }
    var packsByKid by remember {
        mutableStateOf<List<Pair<KidProfile, List<StudyContent>>>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }

    suspend fun loadPacksFor(kid: KidProfile, forceRemote: Boolean = false): List<StudyContent> {
        if (!forceRemote) {
            packCache.get(packCache.userKey(sessionManager.loginId), kid.grade)?.let { return it }
        }
        val packs = try {
            QuizLoader(context).loadQuizzesForGradeRemoteFirst(kid.grade)
        } catch (_: Exception) {
            emptyList()
        }
        if (packs.isNotEmpty()) {
            packCache.put(packCache.userKey(sessionManager.loginId), kid.grade, packs)
        }
        return packs
    }

    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Manual refresh: pull missing results/quizzes from the backend first, then re-read
    // local data so packs and attempt stats reflect quiz results that came in after the
    // phone's LAN listener was started (or while the app was backgrounded).
    fun refreshContent() {
        scope.launch {
            runCatching { QuizResultRepository.getInstance(context).syncFromBackend() }
            refreshKey++
        }
    }

    LaunchedEffect(kidProfiles, refreshKey) {
        loading = true
        packsByKid = kidProfiles.map { kid -> kid to loadPacksFor(kid, forceRemote = refreshKey > 0) }
        val dao = AppDatabase.getDatabase(context).quizResultDao()
        attemptsByPack = packsByKid.flatMap { (kid, packs) ->
            packs.mapNotNull { pack ->
                val results = dao.getResultsForContent(pack.name, kid.name)
                if (results.isEmpty()) null else {
                    val avg = results.map { if (it.totalQuestions > 0) it.score * 100 / it.totalQuestions else 0 }.average().toInt()
                    "${kid.id}_${pack.name}" to Triple(results.size, results.first(), avg)
                }
            }
        }.toMap()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_content)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshContent() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                when {
                    kidProfiles.isEmpty() -> {
                        EmptyContentState(
                            icon = { Icon(Icons.Default.ChildCare, null, modifier = Modifier.size(64.dp), tint = Color.Gray) },
                            title = "No kid profiles found",
                            subtitle = "Add a kid profile first — content is picked based on the kid's class."
                        )
                    }
                    loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    packsByKid.all { it.second.isEmpty() } -> {
                        EmptyContentState(
                            icon = { Icon(Icons.Default.Quiz, null, modifier = Modifier.size(64.dp), tint = Color.Gray) },
                            title = "No packs available",
                            subtitle = "Update the kids' class info to get matching freemium packs."
                        )
                    }
                    else -> {
                        // TV selector: dropdown of discovered TVs above the tab pane.
                        // Defaults to "No TV selected"; refresh re-runs NSD discovery.
                        val discoveredTvs by viewModel.discoveredTvs.collectAsState()
                        var tvDropdownExpanded by remember { mutableStateOf(false) }
                        val selectedTv = discoveredTvs.find { it.host?.hostAddress == viewModel.selectedTvIp }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { tvDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color(0xFFFF6B00))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        selectedTv?.serviceName ?: "No TV selected",
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (selectedTv != null) Color.Unspecified else Color.Gray
                                    )
                                }
                                DropdownMenu(
                                    expanded = tvDropdownExpanded,
                                    onDismissRequest = { tvDropdownExpanded = false }
                                ) {
                                    if (discoveredTvs.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No TVs found — tap refresh", color = Color.Gray) },
                                            onClick = { tvDropdownExpanded = false }
                                        )
                                    } else {
                                        discoveredTvs.forEach { tv ->
                                            DropdownMenuItem(
                                                text = { Text(tv.serviceName ?: tv.host?.hostAddress ?: "Unknown TV") },
                                                trailingIcon = {
                                                    if (tv.host?.hostAddress == viewModel.selectedTvIp) {
                                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFFF6B00))
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.selectedTvIp = tv.host?.hostAddress
                                                    tvDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.startDiscovery() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh TVs")
                            }
                        }

                        // Tabbed view: one tab per kid, showing only that kid's packs.
                        var selectedTab by remember(packsByKid) { mutableStateOf(0) }
                        Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            TabRow(selectedTabIndex = selectedTab.coerceIn(0, packsByKid.lastIndex)) {
                                packsByKid.forEachIndexed { index, (kid, _) ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                KidAvatar(photoUri = kid.photoUri, name = kid.name, size = 24.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    kid.name,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            val (kid, packs) = packsByKid[selectedTab.coerceIn(0, packsByKid.lastIndex)]
                            if (packs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "No packs for ${kid.name} • Class: ${kid.grade}",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                val subjects = packs.map { it.category ?: "General" }.distinct()
                                var subjectTab by remember(kid.id, subjects) { mutableStateOf(0) }
                                val filteredPacks = if (subjects.size <= 1) packs else packs.filter { (it.category ?: "General") == subjects[subjectTab.coerceIn(0, subjects.lastIndex)] }
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Text("Class: ${kid.grade}", style = MaterialTheme.typography.titleSmall, color = Color.Gray, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                    if (subjects.size > 1) {
                                        ScrollableTabRow(selectedTabIndex = subjectTab.coerceIn(0, subjects.lastIndex), edgePadding = 0.dp) {
                                            subjects.forEachIndexed { idx, subj -> Tab(selected = subjectTab == idx, onClick = { subjectTab = idx }, text = { Text(subj, maxLines = 1) }) }
                                        }
                                    }
                                    LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(filteredPacks, key = { "${kid.id}_${it.id ?: it.name}" }) { pack ->
                                    val isSelected = viewModel.selectedContent == pack &&
                                        sessionManager.selectedKidId == kid.id
                                    val attempts = attemptsByPack["${kid.id}_${pack.name}"]
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                            sessionManager.selectedKidId = kid.id
                                            viewModel.selectContent(pack)
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) Color(0xFFFFF3E0) else Color.White
                                        ),
                                        border = if (isSelected)
                                            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF6B00)) else null
                                    ) {
                                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFFFF6B00),
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                val quizShort = pack.name.split("·").lastOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: pack.name
                                                Text(quizShort, fontWeight = FontWeight.Bold)
                                                Text(
                                                    pack.category ?: stringResource(R.string.pack_subtitle_quiz),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                                attempts?.let { (count, last, avg) ->
                                                    val pct = if (last.totalQuestions > 0) (last.score * 100 / last.totalQuestions) else 0
                                                    Text(
                                                        "Attempts: $count, Last: $pct%, Avg: $avg%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF2E7D32),
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            if (isSelected) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFFFF6B00))
                                            }
                                            IconButton(
                                                onClick = { onOpenAnalytics(pack.name, kid.name, kid.photoUri) },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.TrendingUp,
                                                    contentDescription = "Quiz stats",
                                                    tint = Color(0xFFFF6B00)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onReviewPack(pack) },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = "Review ${pack.name}",
                                                    tint = Color(0xFFFF6B00)
                                                )
                                            }
                                        }
                                    }
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
                                Text(stringResource(R.string.start_session), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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
                is StudyUiState.ConfirmGreetingFallback -> {
                    val langLabel = GreetingLanguages.labelOf(state.command.greetingLanguage ?: "en")
                    AlertDialog(
                        onDismissRequest = { viewModel.resetState() },
                        title = { Text("Greeting language not supported") },
                        text = {
                            Text(
                                "This TV can't speak $langLabel for the end-of-quiz greeting, " +
                                    "so it would fall back to English."
                            )
                        },
                        confirmButton = {
                            Button(onClick = { viewModel.confirmGreetingFallback(state, useEnglish = true) }) {
                                Text("Use English instead")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.confirmGreetingFallback(state, useEnglish = false) }) {
                                Text("Start anyway")
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun QuizPresentationConfigCard(
    kid: KidProfile,
    sessionManager: SessionManager
) {
    var config by remember(kid.id) { mutableStateOf(sessionManager.getKidQuizConfig(kid.id)) }
    fun persist(updated: KidQuizConfig) {
        config = updated
        sessionManager.setKidQuizConfig(kid.id, updated)
    }

    // Quick TTS support check against the family TV (the last one used) after picking a language.
    val context = LocalContext.current
    val repository = remember { StudyRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    var verifyingLanguage by remember { mutableStateOf(false) }
    var greetingNotice by remember { mutableStateOf<String?>(null) }

    fun onGreetingPicked(tag: String) {
        persist(config.copy(greetingLanguage = tag))
        greetingNotice = null
        if (tag == "en") return
        val tvIp = sessionManager.lastTvIp
        if (tvIp.isNullOrBlank()) {
            greetingNotice =
                "No TV saved yet. When you start a quiz, the app checks whether this TV can speak the language."
            return
        }
        verifyingLanguage = true
        scope.launch {
            val result = repository.probeTtsLanguages(tvIp)
            verifyingLanguage = false
            val label = GreetingLanguages.labelOf(tag)
            val supported = result.getOrNull()
            val error = result.exceptionOrNull()
            greetingNotice = when {
                result.isSuccess && tag in (supported ?: emptyList()) ->
                    "Your TV can speak $label."
                error?.message == "NOT_ON_WIFI" ->
                    "You're not on Wi-Fi, so I could only save the setting. It's checked again when you " +
                        "start a quiz; your TV falls back to English automatically if it can't speak $label."
                error != null ->
                    "Couldn't reach your TV to verify (setting still saved). If it can't speak $label, " +
                        "it will use English when you connect."
                tag !in (supported ?: emptyList()) ->
                    "Your TV can't speak $label yet - the greeting will fall back to English until you pick another."
                else ->
                    "Your TV can speak $label."
            }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "Quiz presentation",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lock answers until read",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = config.revealReadLock,
                    onCheckedChange = { persist(config.copy(revealReadLock = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Read questions aloud (TTS)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = config.autoDictation,
                    onCheckedChange = { persist(config.copy(autoDictation = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Fast-answer threshold",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${config.fastAnswerThresholdMs / 1000}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = config.fastAnswerThresholdMs.toFloat(),
                onValueChange = { persist(config.copy(fastAnswerThresholdMs = (it / 100f).toLong() * 100L)) },
                valueRange = KidQuizConfig.MIN_FAST_ANSWER_THRESHOLD_MS.toFloat()..5000f
            )
            Text(
                "Answers given faster than this time are flagged for the parent to review.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Greeting message language",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (verifyingLanguage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                var langMenuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { langMenuOpen = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            GreetingLanguages.labelOf(config.greetingLanguage),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DropdownMenu(
                        expanded = langMenuOpen,
                        onDismissRequest = { langMenuOpen = false }
                    ) {
                        GreetingLanguages.options.forEach { (tag, label) ->
                            DropdownMenuItem(
                                text = {
                                    if (tag == config.greetingLanguage) CheckedTextLabel(label) else Text(label)
                                },
                                onClick = {
                                    onGreetingPicked(tag)
                                    langMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
            Text(
                "Language of the message the TV plays when the quiz ends. Your TV is checked " +
                    "after you pick one; unsupported languages fall back to English.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (greetingNotice != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        greetingNotice.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (greetingNotice.orEmpty().contains("can't") || greetingNotice.orEmpty().contains("Couldn't"))
                            MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    if (config.greetingLanguage != "en") {
                        TextButton(onClick = { onGreetingPicked("en") }) {
                            Text("Back to English", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckedTextLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun EmptyContentState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, fontSize = 18.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
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
                                // Picture cards carry answer-revealing metadata in brackets —
                                // never show the bracket; show the card + dictation instead.
                                val card = q.question.replace(Regex("\\[[^\\]]*\\]"), " ")
                                    .replace(Regex("\\s+"), " ").trim()
                                Text(
                                    if (q.description != null && card.isNotBlank()) "$card\n${q.description}"
                                    else q.description ?: card.ifBlank { q.question },
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                q.options.forEachIndexed { index, option ->
                                    val isCorrect = q.answer == index.toString() ||
                                            q.answer.equals(option, ignoreCase = true)
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
