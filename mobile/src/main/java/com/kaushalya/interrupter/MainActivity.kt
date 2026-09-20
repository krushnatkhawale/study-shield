package com.kaushalya.interrupter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kaushalya.interrupter.data.ConnectivityObserver
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.ToastHelper
import com.kaushalya.interrupter.network.AuthEvents
import com.kaushalya.interrupter.network.RetrofitClient
import com.kaushalya.interrupter.ui.*
import com.kaushalya.interrupter.ui.auth.AuthViewModel
import com.kaushalya.interrupter.ui.auth.*
import com.kaushalya.interrupter.ui.theme.InterrupterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {

    private val studyViewModel: StudyViewModel by viewModels()
    private val sessionManager: SessionManager by lazy { SessionManager(applicationContext) }
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(sessionManager, applicationContext)
    }
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: starting StudyShield")

        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("InterrupterDiscoveryLock").apply {
            setReferenceCounted(true)
        }

        RetrofitClient.init(sessionManager)
        ToastHelper.init(applicationContext)
        ConnectivityObserver.getInstance(applicationContext).start()

        setContent {
            InterrupterTheme {
                val context = LocalContext.current
                var hasLocationPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasLocationPermission = isGranted
                    Log.d(TAG, "Location permission result: $isGranted")
                }

                LaunchedEffect(Unit) {
                    if (!hasLocationPermission) {
                        Log.d(TAG, "Requesting location permission")
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }

                // Splash stays until BOTH the minimum show-time elapsed AND routing
                // resolved (main/welcome/carousel) — logged-in users never see a
                // flash of sign-in/sign-up/guest options while validating.
                var splashMinDone by remember { mutableStateOf(false) }
                var routingResolved by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(1800)
                    splashMinDone = true
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        sessionManager = sessionManager,
                        authViewModel = authViewModel,
                        studyViewModel = studyViewModel,
                        onRoutingResolved = { routingResolved = true }
                    )
                    if (!splashMinDone || !routingResolved) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ConnectivityObserver.getInstance(applicationContext).stop()
        multicastLock?.release()
    }

    @Composable
    fun SplashScreen() {
        val pulse = rememberInfiniteTransition(label = "splash")
        val scale by pulse.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shield-scale"
        )
        val glow by pulse.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shield-glow"
        )

        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00).copy(alpha = 0.18f * glow),
                        modifier = Modifier.size(148.dp).graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(96.dp).graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "StudyShield",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B00)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Turn TV Ads into Learning Time",
                    fontSize = 16.sp,
                    color = Color(0xFF1E88E5),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    fun AppNavigation(
        sessionManager: SessionManager,
        authViewModel: AuthViewModel,
        studyViewModel: StudyViewModel,
        onRoutingResolved: () -> Unit = {}
    ) {
        val authState by authViewModel.authState.collectAsState()
        val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

        // Single synchronous routing decision from persisted SharedPreferences
        var screen by rememberSaveable { mutableStateOf(
            when {
                sessionManager.isLoggedIn() -> "validating"
                !sessionManager.hasSeenCarousel -> "carousel"
                else -> "welcome"
            }
        ) }

        Log.d(TAG, "AppNavigation: screen=$screen authState=${authState::class.simpleName} isCheckingSession=$isCheckingSession")

        // Trigger API validation only for returning users
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            if (sessionManager.isLoggedIn()) {
                Log.d(TAG, "AppNavigation: stored session found, validating")
                if (!sessionManager.isGuest) {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val activeNetwork = cm.activeNetwork
                    val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }
                    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    if (!hasInternet) {
                        Toast.makeText(context, "No network connection. Using offline mode.", Toast.LENGTH_LONG).show()
                        authViewModel.skipSessionValidation()
                        // Stay in the app: the account is valid locally, only the
                        // network check was skipped. Routing to welcome here would
                        // flash sign-in options at a logged-in user.
                        screen = "main"
                        return@LaunchedEffect
                    }
                    Toast.makeText(context, "Validating session...", Toast.LENGTH_SHORT).show()
                }
                authViewModel.checkExistingSession()
            }
        }

        // React to auth state changes — keyed on BOTH so we don't
        // prematurely transition from "validating" before the API call completes
        LaunchedEffect(authState, isCheckingSession) {
            Log.d(TAG, "AppNavigation: effect fired authState=${authState::class.simpleName} isCheckingSession=$isCheckingSession screen=$screen")
            when {
                authState is AuthState.Success -> {
                    Log.d(TAG, "AppNavigation: navigating to main")
                    screen = "main"
                }
                !isCheckingSession && authState is AuthState.Idle && screen == "validating" -> {
                    Log.d(TAG, "AppNavigation: navigating to welcome")
                    screen = "welcome"
                }
            }
        }

        // Tell the splash overlay it can lift once routing landed somewhere final.
        // "validating" is intentionally excluded so auth options never flash.
        LaunchedEffect(screen) {
            if (screen == "main" || screen == "welcome" || screen == "carousel") {
                onRoutingResolved()
            }
        }

        // Force a re-login (drop session, go to welcome) whenever any authenticated
        // call reports the stored token as expired/rejected (401/403).
        LaunchedEffect(Unit) {
            AuthEvents.sessionExpired.collect { count ->
                if (count > 0) {
                    Log.d(TAG, "AppNavigation: session expired signal received")
                    authViewModel.forceReLogin()
                    screen = "welcome"
                }
            }
        }

        when (screen) {
            "validating" -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }"carousel" -> {
                FeatureCarouselScreen(onFinished = {
                    Log.d(TAG, "AppNavigation: carousel finished")
                    sessionManager.hasSeenCarousel = true
                    screen = "welcome"
                })
            }
            "main" -> {
                MainScreen(
                    studyViewModel = studyViewModel,
                    sessionManager = sessionManager,
                    onSignOut = { authViewModel.signOut(); screen = "welcome" },
                    onGuestLogout = { authViewModel.guestLogout() }
                )
            }
            "welcome" -> {
                WelcomeNavigation(
                    authViewModel = authViewModel,
                    sessionManager = sessionManager
                )
            }
        }
    }

    @Composable
    fun WelcomeNavigation(
        authViewModel: AuthViewModel,
        sessionManager: SessionManager
    ) {
        val authState by authViewModel.authState.collectAsState()

        var screen by rememberSaveable { mutableStateOf("welcome") }

        LaunchedEffect(screen) {
            Log.d(TAG, "WelcomeNavigation: navigating to screen=$screen")
        }

        when (screen) {
            "welcome" -> {
                WelcomeScreen(
                    onSignUp = { Log.d(TAG, "WelcomeNavigation: user tapped Sign Up"); screen = "signup" },
                    onSignIn = { Log.d(TAG, "WelcomeNavigation: user tapped Sign In"); screen = "signin" },
                    onGuest = { Log.d(TAG, "WelcomeNavigation: user tapped Guest Login"); authViewModel.guestLogin() }
                )
            }
            "signup" -> {
                SignUpScreen(
                    onSignUp = { loginId, password, name ->
                        Log.d(TAG, "WelcomeNavigation: SignUp submitted (loginId=$loginId)")
                        authViewModel.signUp(loginId, password, name)
                    },
                    onBack = { screen = "welcome"; authViewModel.resetError() },
                    isLoading = authState is AuthState.Loading,
                    error = (authState as? AuthState.Error)?.message
                )
            }
            "signin" -> {
                SignInScreen(
                    onSignIn = { loginId, password ->
                        Log.d(TAG, "WelcomeNavigation: SignIn submitted (loginId=$loginId)")
                        authViewModel.signIn(loginId, password)
                    },
                    onBack = { screen = "welcome"; authViewModel.resetError() },
                    isLoading = authState is AuthState.Loading,
                    error = (authState as? AuthState.Error)?.message
                )
            }
        }

        // Handle parent selection
        val psState = authState
        if (psState is AuthState.ParentSelectionRequired) {
            Log.d(TAG, "WelcomeNavigation: parent selection dialog shown")
            ParentSelectionScreen(
                parents = psState.parents,
                onParentSelected = { parentId, parentName ->
                    Log.d(TAG, "WelcomeNavigation: parent selected (parentId=$parentId)")
                    authViewModel.handleParentSelection(parentId, parentName)
                },
                onAddNewParent = { Log.d(TAG, "WelcomeNavigation: add new parent (TODO)") },
                onSkip = {
                    Log.d(TAG, "WelcomeNavigation: parent selection skipped")
                    sessionManager.sessionId = psState.sessionId
                    authViewModel.handleAuthResponse(
                        com.kaushalya.interrupter.data.AuthResponse(
                            sessionId = psState.sessionId,
                            requiresParentSelection = false
                        )
                    )
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: acquiring multicast lock")
        multicastLock?.acquire()
    }

    override fun onPause() {
        super.onPause()
        if (multicastLock?.isHeld == true) {
            Log.d(TAG, "onPause: releasing multicast lock")
            multicastLock?.release()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
