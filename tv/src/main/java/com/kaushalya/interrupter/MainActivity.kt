package com.kaushalya.interrupter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.*
import com.kaushalya.interrupter.ui.theme.InterrupterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private var commandType by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)
    private var duration by mutableLongStateOf(10L)
    private var questionsList by mutableStateOf<List<QuizQuestion>>(emptyList())
    private var currentQuestionIndex by mutableIntStateOf(0)
    private var score by mutableIntStateOf(0)
    private var quizCompleted by mutableStateOf(false)
    private var quizPaused by mutableStateOf(false)
    private var showExitConfirm by mutableStateOf(false)
    private var exitConfirmTimestamp by mutableLongStateOf(0L)
    
    private var contentName by mutableStateOf<String?>(null)
    private var category by mutableStateOf<String?>(null)

    private var ipAddress by mutableStateOf("Fetching...")
    // Renamed to avoid clashing with the function fetchTVName()
    private var tvDisplayName by mutableStateOf("Fetching...")
    private var triggerCount by mutableIntStateOf(0)

    private lateinit var toneGenerator: ToneGenerator
    private lateinit var persistenceManager: LockPersistenceManager
    private val json = Json { ignoreUnknownKeys = true }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle notification permission result if needed
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        persistenceManager = LockPersistenceManager(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                           WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                           WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        checkAndRequestPermissions()
        startService(Intent(this, TvServerService::class.java))
        
        handleIntent(intent)
        ipAddress = getLocalIpAddress()
        tvDisplayName = fetchTVName()

        setContent {
            InterrupterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    var countdown by remember { mutableLongStateOf(10L) }

                    LaunchedEffect(triggerCount) {
                        if (commandType == null) {
                            countdown = 10L
                            while (countdown > 0) {
                                delay(1000)
                                countdown--
                            }
                            moveTaskToBack(true)
                        } else if (commandType == "TIMER") {
                            delay(duration * 1000)
                            exitApp()
                        } else if (commandType == "STUDY_SESSION") {
                            delay(duration * 60 * 1000)
                            exitApp()
                        } else if (commandType == "MCQ" || commandType == "FITB") {
                            while (true) {
                                delay(1000)
                                if (!quizPaused) {
                                    // For quizzes, we don't have a fixed duration; just keep the app alive
                                }
                            }
                        }
                    }

                    if (commandType == "UNLOCK") {
                        LaunchedEffect(Unit) {
                            exitApp()
                        }
                    }

                    Box {
                        AnimatedContent(
                            targetState = commandType,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(500)).togetherWith(fadeOut(animationSpec = tween(500)))
                            },
                            label = "modeTransition"
                        ) { targetType ->
                            MainContent(
                                targetType, message, ipAddress, tvDisplayName, countdown,
                                questionsList,
                                contentName, category, duration,
                                onWrongAnswer = {
                                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                                },
                                onExitQuiz = { exitApp() },
                                isPaused = quizPaused,
                                onTogglePause = { quizPaused = !quizPaused },
                                showExitConfirm = showExitConfirm
                            )
                        }
                    }
                }
            }
        }
    }

    private fun exitApp() {
        persistenceManager.clearLockCommand()
        moveTaskToBack(true)
        resetState()
    }

    private fun resetState() {
        commandType = null
        message = null
        questionsList = emptyList()
        currentQuestionIndex = 0
        score = 0
        quizCompleted = false
        quizPaused = false
        showExitConfirm = false
        exitConfirmTimestamp = 0L
        contentName = null
        category = null
    }

    private fun checkAndRequestPermissions() {
        // 1. Overlay Permission (Display over other apps) - Android 6.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        // 2. Notification Permission - Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) 
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        val type = intent.getStringExtra("COMMAND_TYPE")
        Log.d("InterrupterTV", "Handling intent with type: $type")
        
        if (type != null) {
            commandType = type
            message = intent.getStringExtra("MESSAGE")
            duration = intent.getLongExtra("DURATION", 10L)
            contentName = intent.getStringExtra("CONTENT_NAME")
            category = intent.getStringExtra("CATEGORY")
            
            // Priority 1: Full list from QUESTIONS_JSON
            val questionsJson = intent.getStringExtra("QUESTIONS_JSON")
            if (questionsJson != null) {
                try {
                    val parsed = json.decodeFromString<List<QuizQuestion>>(questionsJson)
                    if (parsed.isNotEmpty()) {
                        questionsList = parsed
                        currentQuestionIndex = 0
                        score = 0
                        quizCompleted = false
                    }
                } catch (e: Exception) {
                    Log.e("InterrupterTV", "Failed to parse questions JSON", e)
                }
            }
            
            // Priority 2: Legacy single-question extras
            if (questionsList.isEmpty()) {
                val q = intent.getStringExtra("QUESTION")
                val opts = intent.getStringArrayListExtra("OPTIONS")
                val ans = intent.getStringExtra("ANSWER")
                if (q != null) {
                    questionsList = listOf(QuizQuestion(q, opts ?: emptyList(), ans ?: ""))
                    currentQuestionIndex = 0
                    score = 0
                    quizCompleted = false
                }
            }
            
            val activeQ = questionsList.getOrNull(currentQuestionIndex)
            Log.d("InterrupterTV", "Quiz questions loaded: ${questionsList.size}, current: ${activeQ?.question}")

            triggerCount++ 
            
            if (::toneGenerator.isInitialized) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 300)
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return "Unknown"
    }

    private fun fetchTVName(): String {
        return Settings.Global.getString(contentResolver, "device_name")
            ?: Settings.Global.getString(contentResolver, "device_name_ext")
            ?: Build.MODEL
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (commandType == "MCQ" || commandType == "FITB") {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE) {
                val now = System.currentTimeMillis()
                if (showExitConfirm && (now - exitConfirmTimestamp) < 3000) {
                    exitApp()
                    return true
                }
                if (quizPaused && showExitConfirm) {
                    showExitConfirm = false
                }
                quizPaused = !quizPaused
                if (quizPaused) {
                    showExitConfirm = true
                    exitConfirmTimestamp = now
                } else {
                    showExitConfirm = false
                }
                return true
            }
        }
        if (commandType != null && commandType != "UNLOCK") return true 
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::toneGenerator.isInitialized) {
            toneGenerator.release()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainContent(
    type: String?, message: String?, ip: String, deviceName: String, countdown: Long,
    questionsList: List<QuizQuestion>,
    contentName: String?, category: String?, duration: Long,
    onWrongAnswer: () -> Unit,
    onExitQuiz: () -> Unit,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    showExitConfirm: Boolean = false
) {
    if (type != null && type != "UNLOCK") {
        BackHandler(enabled = true) { }
    }

    val backgroundBrush = when (type) {
        "BLOCK" -> Brush.verticalGradient(listOf(Color(0xFFFF8A80), Color(0xFFD32F2F)))
        "TIMER" -> Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFF1976D2)))
        "MCQ", "FITB" -> Brush.verticalGradient(listOf(Color(0xFFA5D6A7), Color(0xFF2E7D32)))
        "STUDY_SESSION" -> Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFFF6B00)))
        else -> Brush.verticalGradient(listOf(Color(0xFFCE93D8), Color(0xFF6A1B9A)))
    }

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        if (type == null) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(60.dp),
                contentAlignment = Alignment.Center
            ) {
                val sweepAngle = (countdown.toFloat() / 10f) * 360f
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawCircle(color = Color.White.copy(alpha = 0.3f), style = Stroke(width = 8.dp.toPx()))
                    drawArc(color = Color.White, startAngle = -90f, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 8.dp.toPx()))
                }
                Text(text = countdown.toString(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        when (type) {
            "BLOCK", "TIMER" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = if(type == "BLOCK") "Time for a break! ✋" else "See you soon! ⏳", color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(text = message ?: "Time to play!", color = Color.White, fontSize = 90.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 100.sp)
                }
            }
            "MCQ" ->                 QuizSession(type, questionsList, onWrongAnswer, onExitQuiz, isPaused, onTogglePause, showExitConfirm)
            "FITB" ->                 QuizSession(type, questionsList, onWrongAnswer, onExitQuiz, isPaused, onTogglePause, showExitConfirm)
            "STUDY_SESSION" -> StudySessionUI(contentName, category, duration)
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = deviceName, fontSize = 60.sp, color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Text(text = "Interrupter Ready! 🚀", fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Connect using IP: $ip", fontSize = 32.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuizSession(
    type: String,
    questions: List<QuizQuestion>,
    onWrong: () -> Unit,
    onExit: () -> Unit,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    showExitConfirm: Boolean = false
) {
    var currentIndex by remember(questions) { mutableIntStateOf(0) }
    var score by remember(questions) { mutableIntStateOf(0) }
    var completed by remember(questions) { mutableStateOf(questions.isEmpty()) }

    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "No questions available", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.size(width = 300.dp, height = 80.dp),
                    shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)),
                    colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.2f), focusedContainerColor = Color.White, focusedContentColor = Color.Black)
                ) {
                    Text(text = "OK", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (completed) {
        QuizResultsScreen(score, questions.size, onExit)
    } else if (currentIndex < questions.size) {
        val q = questions[currentIndex]
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Question ${currentIndex + 1} of ${questions.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${(100 * (currentIndex + 1) / questions.size)}%",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((currentIndex.toFloat() + 1f) / questions.size)
                                .height(6.dp)
                                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        )
                    }
                }

                if (type == "MCQ") {
                    if (q.options.size == 2 && (q.options.contains("True") || q.options.contains("False"))) {
                        TrueFalseUI(
                            question = q.question,
                            options = q.options,
                            correctAnswer = q.answer,
                            onCorrect = {
                                score++
                                if (currentIndex < questions.size - 1) currentIndex++
                                else completed = true
                            },
                            onWrongAnswer = onWrong
                        )
                    } else {
                        QuizUI(
                            question = q.question,
                            options = q.options,
                            correctAnswer = q.answer,
                            onCorrect = {
                                score++
                                if (currentIndex < questions.size - 1) currentIndex++
                                else completed = true
                            },
                            onWrongAnswer = onWrong
                        )
                    }
                } else {
                    FitbUI(
                        question = q.question,
                        answer = q.answer,
                        onCorrect = {
                            score++
                            if (currentIndex < questions.size - 1) currentIndex++
                            else completed = true
                        },
                        onWrongAnswer = onWrong
                    )
                }
            }
            if (isPaused) {
                PauseOverlay(onResume = onTogglePause)
            }
            if (showExitConfirm && isPaused) {
                ExitConfirmOverlay()
            }
        }
    }
}

@Composable
fun PauseOverlay(onResume: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "PAUSED", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onResume,
                modifier = Modifier
                    .size(width = 400.dp, height = 120.dp)
                    .focusRequester(focusRequester),
                shape = ButtonDefaults.shape(RoundedCornerShape(40.dp)),
                colors = ButtonDefaults.colors(containerColor = Color(0xFF4CAF50), focusedContainerColor = Color.White, focusedContentColor = Color.Black)
            ) {
                Text(text = "TAP TO RESUME", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
}

@Composable
fun ExitConfirmOverlay() {
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 180.dp).scale(scale.value)
        ) {
            Text(
                text = "Press PAUSE again to exit",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "or resume to continue",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun StudySessionUI(contentName: String?, category: String?, durationMinutes: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "✍️ DEDICATED STUDY SESSION", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = contentName ?: "General Study",
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        if (category != null) {
            Text(text = category, color = Color.Yellow, fontSize = 40.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(64.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Remaining:", color = Color.White.copy(alpha = 0.8f), fontSize = 30.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "$durationMinutes minutes", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Keep going! You are doing great! 🌟",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuizUI(question: String, options: List<String>, correctAnswer: String, onCorrect: () -> Unit, onWrongAnswer: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val correctIndex = correctAnswer.toIntOrNull() ?: 0
    var wrongAnswerTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val shakeOffset by animateDpAsState(
        targetValue = if (wrongAnswerTrigger % 2 == 1) 20.dp else 0.dp,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh),
        label = "shake"
    )

    val flashColor by animateColorAsState(
        targetValue = if (wrongAnswerTrigger > 0) Color.Red.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(100), label = "flash"
    )

    val questionFontSize = when {
        question.length <= 20 -> 48.sp
        question.length <= 40 -> 38.sp
        question.length <= 80 -> 30.sp
        else -> 24.sp
    }

    Box(modifier = Modifier.fillMaxSize().background(flashColor)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp).offset(x = shakeOffset),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = question,
                fontSize = questionFontSize,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                options.chunked(2).forEachIndexed { rowIndex, rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        rowOptions.forEachIndexed { colIndex, option ->
                            val index = rowIndex * 2 + colIndex
                            var isFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

                            val optionFont = when {
                                option.length <= 12 -> 32.sp
                                option.length <= 24 -> 26.sp
                                option.length <= 40 -> 22.sp
                                else -> 18.sp
                            }

                            Button(
                                onClick = {
                                    if (index == correctIndex) onCorrect()
                                    else {
                                        onWrongAnswer()
                                        scope.launch { repeat(6) { wrongAnswerTrigger++; delay(60) }; wrongAnswerTrigger = 0 }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .scale(scale)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                                shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)),
                                colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.2f), focusedContainerColor = Color.White, focusedContentColor = Color.Black)
                            ) {
                                Text(
                                    text = option,
                                    fontSize = optionFont,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrueFalseUI(question: String, options: List<String>, correctAnswer: String, onCorrect: () -> Unit, onWrongAnswer: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var wrongAnswerTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val shakeOffset by animateDpAsState(
        targetValue = if (wrongAnswerTrigger % 2 == 1) 25.dp else 0.dp,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh),
        label = "shake"
    )

    val flashColor by animateColorAsState(
        targetValue = if (wrongAnswerTrigger > 0) Color.Red.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(100), label = "flash"
    )

    val questionFontSize = when {
        question.length <= 20 -> 48.sp
        question.length <= 40 -> 38.sp
        question.length <= 80 -> 30.sp
        else -> 24.sp
    }

    Box(modifier = Modifier.fillMaxSize().background(flashColor)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp).offset(x = shakeOffset),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = question,
                fontSize = questionFontSize,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                options.forEachIndexed { index, option ->
                    var isFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")
                    Button(
                        onClick = {
                            if (option == correctAnswer) onCorrect()
                            else {
                                onWrongAnswer()
                                scope.launch { repeat(6) { wrongAnswerTrigger++; delay(60) }; wrongAnswerTrigger = 0 }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .scale(scale)
                            .onFocusChanged { isFocused = it.isFocused }
                            .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                        shape = ButtonDefaults.shape(RoundedCornerShape(32.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = if (option == "True") Color(0xFF4CAF50).copy(alpha = 0.3f) else Color(0xFFF44336).copy(alpha = 0.3f),
                            focusedContainerColor = if (option == "True") Color(0xFF4CAF50) else Color(0xFFF44336),
                            focusedContentColor = Color.White
                        )
                    ) {
                        Text(
                            text = option,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FitbUI(question: String, answer: String, onCorrect: () -> Unit, onWrongAnswer: () -> Unit) {
    val characters = ('A'..'Z').toList() + ('0'..'9').toList()
    val focusRequester = remember { FocusRequester() }
    var currentInput by remember { mutableStateOf("") }
    var wrongAnswerTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val shakeOffset by animateDpAsState(
        targetValue = if (wrongAnswerTrigger % 2 == 1) 25.dp else 0.dp,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessHigh),
        label = "shake"
    )

    val questionFontSize = when {
        question.length <= 20 -> 48.sp
        question.length <= 40 -> 38.sp
        question.length <= 80 -> 30.sp
        else -> 24.sp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 16.dp).offset(x = shakeOffset),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = question,
            fontSize = questionFontSize,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = currentInput.ifEmpty { "______" },
            fontSize = when {
                currentInput.length <= 8 -> 56.sp
                currentInput.length <= 15 -> 44.sp
                else -> 36.sp
            },
            color = Color.Yellow,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                .padding(horizontal = 48.dp, vertical = 8.dp),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(9),
            modifier = Modifier.fillMaxWidth().height(360.dp)
        ) {
            items(characters) { char ->
                var isFocused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "scale")
                Button(
                    onClick = {
                        currentInput += char
                        if (currentInput.equals(answer, ignoreCase = true)) onCorrect()
                        else if (currentInput.length >= answer.length) {
                            onWrongAnswer()
                            scope.launch { repeat(6) { wrongAnswerTrigger++; delay(60) }; currentInput = ""; wrongAnswerTrigger = 0 }
                        }
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                        .scale(scale)
                        .onFocusChanged { isFocused = it.isFocused }
                        .then(if (char == 'A') Modifier.focusRequester(focusRequester) else Modifier),
                    shape = ButtonDefaults.shape(RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.colors(containerColor = Color.White.copy(alpha = 0.15f), focusedContainerColor = Color.White, focusedContentColor = Color.Black)
                ) {
                    Text(text = char.toString(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Button(
            onClick = { currentInput = "" },
            modifier = Modifier.padding(top = 12.dp).size(280.dp, 70.dp),
            shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)),
            colors = ButtonDefaults.colors(containerColor = Color(0xFFFF5722))
        ) {
            Text("CLEAR", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun CelebrationOverlay(onStart: () -> Unit, onFinished: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val rotate = remember { Animatable(0f) }
    val stars = remember { List(15) { Random.nextFloat() } }
    LaunchedEffect(Unit) {
        onStart()
        launch { scale.animateTo(1.6f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        launch { rotate.animateTo(720f, tween(1500, easing = FastOutSlowInEasing)) }
        delay(4000)
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(100.dp),
        contentAlignment = Alignment.Center
    ) {
        stars.forEachIndexed { i, _ ->
            val starAnim = rememberInfiniteTransition(label = "star").animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(1000 + i * 100), RepeatMode.Reverse), label = "starScale")
            Text(
                text = if(i % 2 == 0) "⭐" else "🌟", 
                fontSize = 45.sp, 
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(((i-7)*100).dp, (Math.sin(i.toDouble()*1.5)*200).toInt().dp)
                    .scale(starAnim.value)
                    .alpha(0.7f)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(280.dp).scale(scale.value).graphicsLayer(rotationZ = rotate.value).background(Brush.radialGradient(listOf(Color.Yellow, Color(0xFFFFD700))), CircleShape).border(12.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(text = "🏆", fontSize = 160.sp) }
            Spacer(modifier = Modifier.height(80.dp))
            Text(text = "YOU DID IT!", color = Color.Cyan, fontSize = 110.sp, fontWeight = FontWeight.Black, modifier = Modifier.scale(scale.value))
            Text(text = "Unlocked! Enjoy your TV time! 🎮", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 30.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuizResultsScreen(score: Int, total: Int, onExit: () -> Unit) {
    val percentage = if (total > 0) (score * 100 / total) else 0
    val isGoodScore = percentage >= 50

    val goodMessages = remember {
        listOf(
            "Well done kid! 🏆",
            "Excellent work! 🌟",
            "Amazing job! 🎉",
            "You're a star! ⭐",
            "Fantastic! 🎊"
        )
    }
    val tryAgainMessages = remember {
        listOf(
            "Better luck next time! 💪",
            "Keep trying! You'll get it! 🌈",
            "Almost there! 😊",
            "Practice makes perfect! 📚",
            "Nice try! Keep going! 🚀"
        )
    }
    val message = remember(score, total) {
        if (isGoodScore) goodMessages.random()
        else tryAgainMessages.random()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = if (isGoodScore) "🏆" else "💪",
            fontSize = 160.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "$score out of $total correct",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "$percentage%",
            color = if (isGoodScore) Color.Yellow else Color.White.copy(alpha = 0.5f),
            fontSize = 36.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.size(width = 360.dp, height = 100.dp),
            shape = ButtonDefaults.shape(RoundedCornerShape(40.dp)),
            colors = ButtonDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.2f),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            )
        ) {
            Text(
                text = "Done",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
