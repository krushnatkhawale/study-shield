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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kaushalya.interrupter.data.ConnectivityObserver
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.ToastHelper
import com.kaushalya.interrupter.network.RetrofitClient
import com.kaushalya.interrupter.ui.*
import com.kaushalya.interrupter.ui.auth.AuthViewModel
import com.kaushalya.interrupter.ui.auth.*
import com.kaushalya.interrupter.ui.theme.InterrupterTheme
import kotlinx.coroutines.delay

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

                var splashDone by remember { mutableStateOf(false) }

                if (!splashDone) {
                    SplashScreen(onFinished = { splashDone = true })
                } else {
                    AppNavigation(
                        sessionManager = sessionManager,
                        authViewModel = authViewModel,
                        studyViewModel = studyViewModel
                    )
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
    fun SplashScreen(onFinished: () -> Unit) {
        LaunchedEffect(Unit) {
            Log.d(TAG, "SplashScreen: showing for 2s")
            delay(2000)
            Log.d(TAG, "SplashScreen: done")
            onFinished()
        }

        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "StudyShield",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B00)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Turn TV Ads into Learning Time",
                    fontSize = 18.sp,
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
        studyViewModel: StudyViewModel
    ) {
        val authState by authViewModel.authState.collectAsState()
        val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

        // Single synchronous routing decision from persisted SharedPreferences
        var screen by remember { mutableStateOf(
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
                !isCheckingSession && authState is AuthState.Idle && (screen == "validating" || screen == "main") -> {
                    Log.d(TAG, "AppNavigation: navigating to welcome")
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
            }
            "carousel" -> {
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
                    onSignOut = { authViewModel.signOut() }
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
