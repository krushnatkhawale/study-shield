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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.serialization.json.Json
import android.speech.tts.TextToSpeech
import java.net.Inet4Address
import java.util.Locale
import java.net.NetworkInterface
import java.net.Socket
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val INTERRUPTOR_COUNTDOWN_SECONDS = 15L

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
    private var mobileIp by mutableStateOf<String?>(null)
    private var resultCallbackPort by mutableIntStateOf(0)
    private var kidName by mutableStateOf<String?>(null)

    // Per-kid quiz presentation config (Features 4/5 + configurable threshold).
    private var revealReadLock by mutableStateOf(false)
    private var autoDictation by mutableStateOf(false)
    private var fastAnswerThresholdMs by mutableLongStateOf(SafeQuizConfig.DEFAULT_FAST_ANSWER_THRESHOLD_MS)

    // Parent-selected locale tag for the post-quiz greeting message + TTS (default: English).
    private var greetingLanguage by mutableStateOf("en")
    private var avatarId by mutableStateOf("hero")

    private var ipAddress by mutableStateOf("Fetching...")
    // Renamed to avoid clashing with the function fetchTVName()
    private var tvDisplayName by mutableStateOf("Fetching...")
    private var triggerCount by mutableIntStateOf(0)

    private lateinit var toneGenerator: ToneGenerator
    private lateinit var persistenceManager: LockPersistenceManager
    private var textToSpeech: TextToSpeech? = null
    // True once the speech engine has finished initialising (and is configured), so the first
    // quiz question isn't dropped while it's still starting up.
    private var ttsReady by mutableStateOf(false)
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

        ttsReady = false
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureKidTts(textToSpeech)
                TtsCapabilities.refresh(textToSpeech)
                ttsReady = true
            }
        }

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
                    var countdown by remember { mutableLongStateOf(INTERRUPTOR_COUNTDOWN_SECONDS) }

                    LaunchedEffect(triggerCount) {
                        if (commandType == null) {
                            countdown = INTERRUPTOR_COUNTDOWN_SECONDS
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
                                mobileIp, resultCallbackPort, kidName,
                                revealReadLock, autoDictation, fastAnswerThresholdMs, textToSpeech,
                                greetingLanguage, avatarId, ttsReady,
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
        kidName = null
    }

    // Slow the dictation down a bit so kids have time to listen and understand. English (the
    // default greeting) is always spoken with a British accent (en-GB); only an explicit
    // non-English parent choice (e.g. mr-IN / hi-IN) switches the voice. Male/female depends
    // on the engine's available voices — we best-effort pick a female voice in the target locale.
    private fun configureKidTts(tts: TextToSpeech?) {
        if (tts == null) return
        try {
            tts.setSpeechRate(0.8f)
            val chosen = runCatching { Locale.forLanguageTag(greetingLanguage) }.getOrNull()
            val target = if (chosen == null || chosen.language == "en") Locale.UK else chosen
            val usable = arrayOf(target, Locale.UK, Locale.getDefault())
                .firstOrNull {
                    it != null && tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
                }
                ?: Locale.getDefault()
            tts.language = usable
            val localeVoices = tts.voices?.filter { v ->
                v.locale.language == usable.language &&
                    (usable.country.isBlank() || v.locale.country == usable.country)
            }
            val preferredVoice = localeVoices?.firstOrNull {
                it.name.contains("female", ignoreCase = true)
            } ?: localeVoices?.firstOrNull()
            preferredVoice?.let { tts.voice = it }
        } catch (e: Exception) {
            Log.e("InterrupterTV", "TTS configuration failed", e)
        }
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
            mobileIp = intent.getStringExtra("MOBILE_IP")
            resultCallbackPort = intent.getIntExtra("RESULT_CALLBACK_PORT", 0)
            kidName = intent.getStringExtra("KID_NAME")
            revealReadLock = intent.getBooleanExtra("REVEAL_READ_LOCK", false)
            autoDictation = intent.getBooleanExtra("AUTO_DICTATION", false)
            fastAnswerThresholdMs = SafeQuizConfig.clampThreshold(
                if (intent.hasExtra("FAST_ANSWER_THRESHOLD_MS")) intent.getLongExtra("FAST_ANSWER_THRESHOLD_MS", 1500L) else null
            )
            greetingLanguage = intent.getStringExtra("GREETING_LANGUAGE") ?: "en"
            avatarId = intent.getStringExtra("AVATAR_ID") ?: "hero"
            configureKidTts(textToSpeech)
            
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
        textToSpeech?.let {
            it.stop()
            it.shutdown()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainContent(
    type: String?, message: String?, ip: String, deviceName: String, countdown: Long,
    questionsList: List<QuizQuestion>,
    contentName: String?, category: String?, duration: Long,
    mobileIp: String?, resultCallbackPort: Int, kidName: String?,
    revealReadLock: Boolean,
    autoDictation: Boolean,
    fastAnswerThresholdMs: Long,
    textToSpeech: TextToSpeech?,
    greetingLanguage: String = "en",
    avatarId: String = "hero",
    ttsReady: Boolean = false,
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
                val sweepAngle = (countdown.toFloat() / INTERRUPTOR_COUNTDOWN_SECONDS.toFloat()) * 360f
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
            "MCQ" ->                 QuizSession(type, questionsList, onWrongAnswer, onExitQuiz, isPaused, onTogglePause, showExitConfirm, contentName, category, mobileIp, resultCallbackPort, kidName, revealReadLock, autoDictation, fastAnswerThresholdMs, textToSpeech, greetingLanguage, avatarId, ttsReady)
            "FITB" ->                 QuizSession(type, questionsList, onWrongAnswer, onExitQuiz, isPaused, onTogglePause, showExitConfirm, contentName, category, mobileIp, resultCallbackPort, kidName, revealReadLock, autoDictation, fastAnswerThresholdMs, textToSpeech, greetingLanguage, avatarId, ttsReady)
            "STUDY_SESSION" -> StudySessionUI(contentName, category, duration)
            else -> {
                val context = LocalContext.current
                val pairCode = remember(context) { PairCodeStore.get(context) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = deviceName, fontSize = 56.sp, color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Ready to play!", fontSize = 44.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(
                        modifier = Modifier.background(
                            Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(24.dp)
                        ).padding(horizontal = 40.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = pairCode,
                            fontSize = 120.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 20.sp
                        )
                    }
                    Text(text = "Pairing code", fontSize = 24.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "On your phone, tap this TV or enter the code above",
                        fontSize = 26.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(text = "IP: $ip", fontSize = 22.sp, color = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// Quiz-presentation safety config, mirroring the mobile `KidQuizConfig` defaults so the TV can
// behave sensibly even when a command omits them.
object SafeQuizConfig {
    const val DEFAULT_FAST_ANSWER_THRESHOLD_MS: Long = 1500L
    const val MIN_FAST_ANSWER_THRESHOLD_MS: Long = 0L
    const val MAX_FAST_ANSWER_THRESHOLD_MS: Long = 10000L

    /** Sanitize a threshold sent by the parent app; clamps invalid values to a sane range. */
    fun clampThreshold(value: Long?): Long {
        if (value == null) return DEFAULT_FAST_ANSWER_THRESHOLD_MS
        return value.coerceIn(MIN_FAST_ANSWER_THRESHOLD_MS, MAX_FAST_ANSWER_THRESHOLD_MS)
    }

    /** Minimum ms the question must be on screen before a reveal/read-lock answer is allowed. */
    fun readLockDelayMs(thresholdMs: Long): Long = thresholdMs.coerceAtLeast(DEFAULT_FAST_ANSWER_THRESHOLD_MS)
}

/**
 * Post-quiz praise/encouragement messages, keyed by the parent-selected language tag.
 * Fallback is always English. Messages take the kid's name as the %s argument.
 */
object CompletionMessages {
    private data class MessageSets(val good: List<String>, val tryAgain: List<String>)

    private val byTag: Map<String, MessageSets> = mapOf(
        "en" to MessageSets(
            good = listOf(
                "Well done, %s! 🏆",
                "Excellent work, %s! 🌟",
                "Amazing job, %s! 🎉",
                "You're a star, %s! ⭐",
                "Fantastic, %s! 🎊"
            ),
            tryAgain = listOf(
                "Better luck next time, %s! 💪",
                "Keep trying, %s! You'll get it! 🌈",
                "Almost there, %s! 😊",
                "Practice makes perfect, %s! 📚",
                "Nice try, %s! Keep going! 🚀"
            )
        ),
        "mr-IN" to MessageSets(
            good = listOf(
                "छान केले, %s! 🏆",
                "उत्तम काम, %s! 🌟",
                "कमाल केले, %s! 🎉",
                "तू चतुर आहेस, %s! ⭐",
                "अप्रतिम, %s! 🎊"
            ),
            tryAgain = listOf(
                "पुढच्या वेळी नक्की, %s! 💪",
                "प्रयत्न करत राहा, %s! तुला जमेल! 🌈",
                "जवळपास पोहोचलास, %s! 😊",
                "सरावाने सर्व काही साध्य होते, %s! 📚",
                "चांगला प्रयत्न, %s! चालू ठेव! 🚀"
            )
        ),
        "hi-IN" to MessageSets(
            good = listOf(
                "बहुत बढ़िया, %s! 🏆",
                "शानदार काम, %s! 🌟",
                "कमाल किया, %s! 🎉",
                "तुम स्टार हो, %s! ⭐",
                "जबरदस्त, %s! 🎊"
            ),
            tryAgain = listOf(
                "अगली बार ज़रूर, %s! 💪",
                "कोशिश करते रहो, %s! मिल जाएगा! 🌈",
                "बस थोड़ा सा रह गया, %s! 😊",
                "अभ्यास से सब ठीक हो जाता है, %s! 📚",
                "अच्छी कोशिश, %s! चलते रहो! 🚀"
            )
        )
    )

    fun messageFor(language: String, goodScore: Boolean, kidName: String?): String {
        val sets = byTag[language] ?: byTag.getValue("en")
        val template = (if (goodScore) sets.good else sets.tryAgain).random()
        val name = kidName?.takeIf { it.isNotBlank() }
            ?: when (language) {
                "mr-IN" -> "मित्रा"
                "hi-IN" -> "दोस्त"
                else -> "champ"
            }
        return String.format(template, name)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QuizSession(
    type: String,
    questions: List<QuizQuestion>,    onWrong: () -> Unit,
    onExit: () -> Unit,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    showExitConfirm: Boolean = false,
    contentName: String? = null,
    category: String? = null,
    mobileIp: String? = null,
    resultCallbackPort: Int = 0,
    kidName: String? = null,
    revealReadLock: Boolean = false,
    autoDictation: Boolean = false,
    fastAnswerThresholdMs: Long = SafeQuizConfig.DEFAULT_FAST_ANSWER_THRESHOLD_MS,
    textToSpeech: TextToSpeech? = null,
    greetingLanguage: String = "en",
    avatarId: String = "hero",
    ttsReady: Boolean = false
) {
    var currentIndex by remember(questions) { mutableIntStateOf(0) }
    var score by remember(questions) { mutableIntStateOf(0) }
    var completed by remember(questions) { mutableStateOf(questions.isEmpty()) }
    // Per-question timing so we can detect "too fast" answers without showing the kid anything.
    var questionShownAt by remember(questions) { mutableLongStateOf(0L) }
    var fastAnswerCount by remember(questions) { mutableIntStateOf(0) }

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
        QuizResultsScreen(score, questions.size, contentName, category, mobileIp, resultCallbackPort, fastAnswerCount, kidName, greetingLanguage, textToSpeech, avatarId, onExit)
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

                val advance: (Boolean) -> Unit = { isCorrect ->
                    val answeredIn = System.currentTimeMillis() - questionShownAt
                    // Quietly record answers given faster than the reading threshold.
                    if (answeredIn in 0..fastAnswerThresholdMs) fastAnswerCount++
                    if (isCorrect) score++
                    if (currentIndex < questions.size - 1) currentIndex++
                    else completed = true
                }
                LaunchedEffect(currentIndex) {
                    questionShownAt = System.currentTimeMillis()
                }
                // Feature 5: auto-dictation — read the current question aloud when enabled.
                // Gated on ttsReady so the very first question isn't dropped while the speech
                // engine is still initialising (it re-fires the moment the engine is ready).
                LaunchedEffect(currentIndex, autoDictation, textToSpeech, ttsReady) {
                    if (autoDictation) {
                        val tts = textToSpeech
                        val questionText = questions.getOrNull(currentIndex)?.question
                        if (tts != null && questionText != null) {
                            try {
                                tts.speak(questionText, TextToSpeech.QUEUE_FLUSH, null, "question_$currentIndex")
                            } catch (_: Exception) {}
                        }
                    }
                }
                val readLockMs = if (revealReadLock) SafeQuizConfig.readLockDelayMs(fastAnswerThresholdMs) else 0L
                if (type == "MCQ") {
                    if (q.options.size == 2 && (q.options.contains("True") || q.options.contains("False"))) {
                        TrueFalseUI(
                            question = q.question,
                            options = q.options,
                            correctAnswer = q.answer,
                            onAnswer = advance,
                            onWrongAnswer = onWrong,
                            readLockMs = readLockMs
                        )
                    } else {
                        QuizUI(
                            question = q.question,
                            options = q.options,
                            correctAnswer = q.answer,
                            onAnswer = advance,
                            onWrongAnswer = onWrong,
                            readLockMs = readLockMs
                        )
                    }
                } else {
                    FitbUI(
                        question = q.question,
                        answer = q.answer,
                        onAnswer = advance,
                        onWrongAnswer = onWrong,
                        readLockMs = readLockMs
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
private fun remainingReadLockMs(readLockMs: Long): Long {
    if (readLockMs <= 0L) return 0L
    val shownAt = remember { System.currentTimeMillis() }
    var remaining by remember { mutableLongStateOf(readLockMs) }
    LaunchedEffect(readLockMs) {
        while (remaining > 0) {
            val elapsed = System.currentTimeMillis() - shownAt
            remaining = (readLockMs - elapsed).coerceAtLeast(0L)
            delay(100)
        }
    }
    return remaining
}

@Composable
fun QuizUI(question: String, options: List<String>, correctAnswer: String, onAnswer: (Boolean) -> Unit, onWrongAnswer: () -> Unit, readLockMs: Long = 0L) {
    val focusRequester = remember { FocusRequester() }
    // Accept both legacy numeric-index answers ("2") and option-text answers ("Mango")
    val correctIndex = correctAnswer.toIntOrNull()
        ?.takeIf { it in options.indices }
        ?: options.indexOfFirst { it.equals(correctAnswer, ignoreCase = true) }
    var wrongAnswerTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val lockRemaining = remainingReadLockMs(readLockMs)

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
            if (lockRemaining > 0) {
                Text(
                    text = "📖 Read the question — answers unlock in ${(lockRemaining / 1000).coerceAtLeast(1)}s",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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
                                    if (lockRemaining > 0) return@Button
                                    val isCorrect = index == correctIndex
                                    if (!isCorrect) {
                                        onWrongAnswer()
                                        scope.launch { repeat(6) { wrongAnswerTrigger++; delay(60) }; wrongAnswerTrigger = 0 }
                                    }
                                    scope.launch { delay(if (isCorrect) 0 else 350); onAnswer(isCorrect) }
                                },
                                enabled = lockRemaining <= 0,
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
fun TrueFalseUI(question: String, options: List<String>, correctAnswer: String, onAnswer: (Boolean) -> Unit, onWrongAnswer: () -> Unit, readLockMs: Long = 0L) {
    val focusRequester = remember { FocusRequester() }
    var wrongAnswerTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val lockRemaining = remainingReadLockMs(readLockMs)

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
            if (lockRemaining > 0) {
                Text(
                    text = "📖 Read the question — answers unlock in ${(lockRemaining / 1000).coerceAtLeast(1)}s",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                options.forEachIndexed { index, option ->
                    var isFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")
                    Button(
                        onClick = {
                            if (lockRemaining > 0) return@Button
                            val isCorrect = option.equals(correctAnswer, ignoreCase = true)
                            if (!isCorrect) {
                                onWrongAnswer()
                                scope.launch { repeat(6) { wrongAnswerTrigger++; delay(60) }; wrongAnswerTrigger = 0 }
                            }
                            scope.launch { delay(if (isCorrect) 0 else 350); onAnswer(isCorrect) }
                        },
                        enabled = lockRemaining <= 0,
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
fun FitbUI(question: String, answer: String, onAnswer: (Boolean) -> Unit, onWrongAnswer: () -> Unit, readLockMs: Long = 0L) {
    val characters = ('A'..'Z').toList() + ('0'..'9').toList()
    val focusRequester = remember { FocusRequester() }
    var currentInput by remember { mutableStateOf("") }
    var wrongAnswerTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val lockRemaining = remainingReadLockMs(readLockMs)

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
        if (lockRemaining > 0) {
            Text(
                text = "📖 Read the question — typing unlocks in ${(lockRemaining / 1000).coerceAtLeast(1)}s",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(9),
            modifier = Modifier.fillMaxWidth().height(360.dp)
        ) {
            items(characters) { char ->
                var isFocused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "scale")
                Button(
                    onClick = {
                        if (lockRemaining > 0) return@Button
                        currentInput += char
                        if (currentInput.equals(answer, ignoreCase = true)) {
                            onAnswer(true)
                        } else if (currentInput.length >= answer.length) {
                            onWrongAnswer()
                            scope.launch { repeat(6) { wrongAnswerTrigger++; delay(60) }; wrongAnswerTrigger = 0; onAnswer(false) }
                        }
                    },
                    enabled = lockRemaining <= 0,
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
fun QuizResultsScreen(score: Int, total: Int, contentName: String?, category: String?, mobileIp: String?, callbackPort: Int, fastAnswerCount: Int = 0, kidName: String? = null, greetingLanguage: String = "en", textToSpeech: TextToSpeech? = null, avatarId: String = "hero", onExit: () -> Unit) {
    val percentage = if (total > 0) (score * 100 / total) else 0
    val isGoodScore = percentage >= 50

    // Parent-chosen language drives both the on-screen text and the spoken greeting.
    val message = remember(score, total, greetingLanguage, kidName) {
        CompletionMessages.messageFor(greetingLanguage, isGoodScore, kidName)
    }

    val coroutineScope = rememberCoroutineScope()

    val sendResult = {
        if (mobileIp != null && callbackPort > 0) {
            coroutineScope.launch {
                try {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val resultMessage = QuizResultMessage(
                            score = score,
                            totalQuestions = total,
                            contentName = contentName,
                            category = category,
                            timeSpentSeconds = 0,
                            completedAt = System.currentTimeMillis(),
                            fastAnswerCount = fastAnswerCount,
                            kidName = kidName
                        )
                        val socket = java.net.Socket(mobileIp, callbackPort)
                        val out = java.io.PrintWriter(socket.getOutputStream(), true)
                        out.println(Json.encodeToString(QuizResultMessage.serializer(), resultMessage))
                        socket.close()
                    }
                    Log.d("InterrupterTV", "Quiz result sent to mobile: $score/$total")
                } catch (e: Exception) {
                    Log.e("InterrupterTV", "Failed to send result to mobile", e)
                }
            }
        }
    }

    // Send the result, speak the greeting, show the score, then close automatically.
    LaunchedEffect(Unit) {
        try {
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "greeting")
        } catch (_: Exception) {}
        sendResult()
        delay(4000)
        onExit()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        LiveMascot(avatarId = avatarId, modifier = Modifier.size(340.dp))
        Spacer(modifier = Modifier.height(20.dp))
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
        if (!kidName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Quiz by $kidName",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Code-drawn "living" mascot for the quiz-completion screen: blinking + wandering eyes,
 * a waving hand, and a gentle bounce. All pure Compose Canvas — no image assets.
 */
private enum class EarKind { ROUND, LONG, POINTED, PIG, NONE }
private enum class HairKind { CURLS, SPIKY, TOPKNOT, TUFT, ANTENNA, SPIKES, NONE }
private enum class MuzzleKind { NONE, SNOUT, MUZZLE, PANDA_PATCH, STRIPES, WHISKERS }

/** Visual recipe for one completion-mascot avatar (friendly, inspired-by archetypes). */
private data class AvatarStyle(
    val face: Long,
    val outline: Long,
    val belly: Long,
    val dark: Long,
    val inner: Long,
    val ears: EarKind,
    val hair: HairKind,
    val muzzle: MuzzleKind,
    val earsBig: Boolean = false
)

private fun avatarStyle(id: String): AvatarStyle = when (id) {
    "warrior" -> AvatarStyle(0xFFFFE0BD, 0xFFB06A2D, 0xFFFF6F00, 0xFF2B2B2B, 0xFFFFC89A, EarKind.NONE, HairKind.SPIKY, MuzzleKind.NONE)
    "pig" -> AvatarStyle(0xFFFFB6C1, 0xFFE57393, 0xFFFF9FB0, 0xFFC2185B, 0xFFF8C9D0, EarKind.PIG, HairKind.NONE, MuzzleKind.SNOUT)
    "panda" -> AvatarStyle(0xFFFFFEFB, 0xFF616161, 0xFFECECEC, 0xFF424242, 0xFF9E9E9E, EarKind.ROUND, HairKind.NONE, MuzzleKind.PANDA_PATCH)
    "bear" -> AvatarStyle(0xFFC89A6B, 0xFF8D6E63, 0xFFD9B48C, 0xFF5D4037, 0xFFE3C9A8, EarKind.ROUND, HairKind.NONE, MuzzleKind.MUZZLE)
    "bunny" -> AvatarStyle(0xFFF7F7FF, 0xFF9A9AC2, 0xFFEFEFF7, 0xFF6A6AA0, 0xFFFFC0CB, EarKind.LONG, HairKind.NONE, MuzzleKind.NONE)
    "tiger" -> AvatarStyle(0xFFF6A15D, 0xFFC7692A, 0xFFFFD9A0, 0xFF3E2723, 0xFFFCE2C4, EarKind.POINTED, HairKind.NONE, MuzzleKind.STRIPES)
    "monkey" -> AvatarStyle(0xFFC88B53, 0xFF8D5A2B, 0xFFD7A26E, 0xFF5D3A1A, 0xFFE6C39A, EarKind.ROUND, HairKind.TOPKNOT, MuzzleKind.MUZZLE)
    "cat" -> AvatarStyle(0xFFFEE3A6, 0xFFC9A063, 0xFFFCE9C8, 0xFF6D4C41, 0xFFF6D9C2, EarKind.POINTED, HairKind.NONE, MuzzleKind.WHISKERS)
    "fox" -> AvatarStyle(0xFFF08A5D, 0xFFBC5A33, 0xFFFFC39B, 0xFFF5EBDD, 0xFFFFE3CD, EarKind.POINTED, HairKind.NONE, MuzzleKind.MUZZLE, earsBig = true)
    "dino" -> AvatarStyle(0xFF9FD68C, 0xFF66A84F, 0xFFCDE8A8, 0xFF3E7C28, 0xFFBEE8A5, EarKind.NONE, HairKind.SPIKES, MuzzleKind.NONE)
    "robot" -> AvatarStyle(0xFFB0BEC5, 0xFF546E7A, 0xFF90A4AE, 0xFF263238, 0xFFFFE082, EarKind.NONE, HairKind.ANTENNA, MuzzleKind.NONE)
    else -> AvatarStyle(0xFFFFB74D, 0xFFEF6C00, 0xFFFF7043, 0xFF4E342E, 0xFFFFBB8A, EarKind.ROUND, HairKind.CURLS, MuzzleKind.NONE)
}

@Composable
fun LiveMascot(avatarId: String = "hero", modifier: Modifier = Modifier) {
    val style = avatarStyle(avatarId)
    val transition = rememberInfiniteTransition(label = "mascot")
    val loop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "loop"
    )
    val wave by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "wave"
    )
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bounce"
    )

    // Blink: eyes stay closed for the first ~130ms of every loop cycle.
    val blinking = loop < 0.04f
    // Playful eye-scan so the eyes appear to look around the room.
    val lookX = sin(loop * 2f * PI.toFloat())
    val lookY = sin(loop * 4f * PI.toFloat()) * 0.6f

    Canvas(modifier = modifier) {
        val headR = size.minDimension * 0.27f
        val cx = size.width / 2f
        val cy = size.height * 0.50f + bounce * size.height * 0.015f
        val outlineW = size.minDimension * 0.012f
        val face = Color(style.face)
        val dark = Color(style.dark)

        // Body
        drawRoundRect(
            color = Color(style.belly),
            topLeft = Offset(cx - headR * 0.55f, cy + headR * 0.60f),
            size = Size(headR * 1.10f, headR * 0.85f),
            cornerRadius = CornerRadius(headR * 0.30f, headR * 0.30f)
        )
        drawRoundRect(
            color = Color(style.outline),
            topLeft = Offset(cx - headR * 0.55f, cy + headR * 0.60f),
            size = Size(headR * 1.10f, headR * 0.85f),
            cornerRadius = CornerRadius(headR * 0.30f, headR * 0.30f),
            style = Stroke(width = outlineW)
        )

        // Ears (behind the head)
        when (style.ears) {
            EarKind.ROUND -> {
                for (side in listOf(-1f, 1f)) {
                    val earC = Offset(cx + side * headR * 1.04f, cy - headR * 0.10f)
                    val earFill = if (avatarId == "panda") dark else face
                    val earR = if (style.earsBig) headR * 0.22f else headR * 0.18f
                    drawCircle(earFill, radius = earR, center = earC)
                    if (avatarId == "panda") {
                        drawCircle(
                            Color(style.outline),
                            radius = earR,
                            center = earC,
                            style = Stroke(width = outlineW * 0.9f)
                        )
                    } else {
                        drawCircle(
                            Color(style.inner),
                            radius = earR * 0.5f,
                            center = earC + Offset(0f, earR * 0.12f)
                        )
                    }
                }
            }
            EarKind.LONG -> {
                for (side in listOf(-1f, 1f)) {
                    val base = Offset(cx + side * headR * 0.62f, cy - headR * 0.55f)
                    drawOval(
                        face,
                        topLeft = base - Offset(headR * 0.11f, headR * 0.34f),
                        size = Size(headR * 0.22f, headR * 0.68f)
                    )
                    drawOval(
                        Color(style.inner),
                        topLeft = base - Offset(headR * 0.05f, headR * 0.28f),
                        size = Size(headR * 0.10f, headR * 0.50f)
                    )
                }
            }
            EarKind.POINTED, EarKind.PIG -> {
                for (side in listOf(-1f, 1f)) {
                    val baseY = cy - headR * 0.28f
                    val baseX = cx + side * headR * (if (style.earsBig) 1.10f else 0.96f)
                    val tipX = baseX + side * headR * (if (style.earsBig) 0.38f else 0.28f)
                    val tipY = baseY - headR * 0.46f
                    val path = Path().apply {
                        moveTo(baseX, baseY)
                        lineTo(tipX, tipY)
                        lineTo(baseX + side * headR * 0.05f, baseY)
                        close()
                    }
                    drawPath(path, face)
                    val innerPath = Path().apply {
                        moveTo(baseX - side * headR * 0.04f, baseY - headR * 0.06f)
                        lineTo(tipX - side * headR * 0.10f, tipY + headR * 0.10f)
                        lineTo(baseX + side * headR * 0.01f, baseY - headR * 0.06f)
                        close()
                    }
                    drawPath(innerPath, Color(style.inner))
                }
            }
            EarKind.NONE -> {}
        }

        // Head
        drawCircle(color = face, radius = headR, center = Offset(cx, cy))
        drawCircle(
            color = Color(style.outline),
            radius = headR,
            center = Offset(cx, cy),
            style = Stroke(width = outlineW)
        )

        // Facial patterns drawn under the features: panda eye patches, tiger stripes
        if (style.muzzle == MuzzleKind.PANDA_PATCH) {
            for (side in listOf(-1f, 1f)) {
                val eyeY = cy - headR * 0.18f
                drawOval(
                    dark,
                    topLeft = Offset(cx + side * headR * 0.34f - headR * 0.24f, eyeY - headR * 0.26f),
                    size = Size(headR * 0.48f, headR * 0.52f)
                )
            }
        }
        if (style.muzzle == MuzzleKind.STRIPES) {
            for (side in listOf(-1f, 1f)) {
                drawLine(
                    dark,
                    start = Offset(cx + side * headR * 0.16f, cy - headR * 0.95f),
                    end = Offset(cx + side * headR * 0.20f, cy - headR * 0.62f),
                    strokeWidth = headR * 0.10f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Hair shape on the crown
        when (style.hair) {
            HairKind.CURLS -> {
                val spots = listOf(-0.55f to -0.18f, 0.05f to -0.34f, 0.62f to -0.12f)
                spots.forEach { (sx, sy) ->
                    drawCircle(
                        dark,
                        radius = headR * 0.17f,
                        center = Offset(cx + sx * headR, cy + sy * headR)
                    )
                }
                drawCircle(
                    dark,
                    radius = headR * 0.12f,
                    center = Offset(cx + 0.02f * headR, cy - 0.40f * headR)
                )
            }
            HairKind.SPIKY -> {
                val spikes = listOf(-0.60f, -0.30f, 0.02f, 0.34f, 0.62f)
                spikes.forEach { sx ->
                    val a = (sx * PI.toFloat() * 0.9f)
                    val bx = cx + sx * headR * 0.9f
                    val by = cy - headR * (0.92f + cos(a) * 0.10f)
                    val w = headR * 0.14f
                    val h = headR * 0.38f
                    val p = Path().apply {
                        moveTo(bx - w, by)
                        lineTo(bx, by - h)
                        lineTo(bx + w, by)
                        close()
                    }
                    drawPath(p, dark)
                }
            }
            HairKind.TOPKNOT -> {
                drawCircle(dark, radius = headR * 0.16f, center = Offset(cx + headR * 0.30f, cy - headR * 0.44f))
                drawCircle(dark, radius = headR * 0.10f, center = Offset(cx - headR * 0.02f, cy - headR * 0.98f))
            }
            HairKind.TUFT -> {
                val p = Path().apply {
                    moveTo(cx - headR * 0.10f, cy - headR * 0.94f)
                    lineTo(cx + headR * 0.02f, cy - headR * 1.22f)
                    lineTo(cx + headR * 0.14f, cy - headR * 0.90f)
                    close()
                }
                drawPath(p, dark)
            }
            HairKind.SPIKES -> {
                val spikes = listOf(-0.50f, -0.05f, 0.42f)
                spikes.forEachIndexed { i, sx ->
                    val p = Path().apply {
                        moveTo(cx + sx * headR - headR * 0.12f, cy - headR * 0.86f - headR * 0.04f * i)
                        lineTo(cx + sx * headR, cy - headR * (1.28f + 0.06f * i))
                        lineTo(cx + sx * headR + headR * 0.12f, cy - headR * 0.86f - headR * 0.04f * i)
                        close()
                    }
                    drawPath(p, Color(style.dark))
                }
            }
            HairKind.ANTENNA -> {
                val top = Offset(cx, cy - headR * 0.98f)
                drawLine(
                    Color(style.outline),
                    start = Offset(cx, cy - headR * 0.90f),
                    end = Offset(cx, cy - headR * 1.28f),
                    strokeWidth = headR * 0.06f,
                    cap = StrokeCap.Round
                )
                drawCircle(Color(style.inner), radius = headR * 0.12f, center = top - Offset(0f, headR * 0.30f))
                drawCircle(
                    Color(style.outline),
                    radius = headR * 0.12f,
                    center = top - Offset(0f, headR * 0.30f),
                    style = Stroke(width = outlineW)
                )
            }
            HairKind.NONE -> {}
        }

        // Waving arm (rotates around the shoulder)
        val shoulder = Offset(cx + headR * 0.75f, cy + headR * 0.80f)
        rotate(degrees = wave * 28f, pivot = shoulder) {
            val hand = Offset(shoulder.x + headR * 0.90f, shoulder.y - headR * 0.90f)
            drawLine(
                color = face,
                start = shoulder,
                end = hand,
                strokeWidth = size.minDimension * 0.035f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(style.outline),
                start = shoulder,
                end = hand,
                strokeWidth = size.minDimension * 0.040f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = face,
                radius = size.minDimension * 0.045f,
                center = hand
            )
            drawCircle(
                color = Color(style.outline),
                radius = size.minDimension * 0.045f,
                center = hand,
                style = Stroke(width = outlineW)
            )
        }

        // Eyes (blinking + tracking) — sit on top of panda patches
        val eyeY = cy - headR * 0.18f
        val eyeDX = headR * 0.34f
        val eyeW = headR * 0.30f
        for (side in listOf(-1f, 1f)) {
            val eyeCx = cx + side * eyeDX
            if (blinking) {
                drawArc(
                    color = dark,
                    startAngle = 10f,
                    sweepAngle = 160f,
                    useCenter = false,
                    topLeft = Offset(eyeCx - eyeW / 2f, eyeY - headR * 0.012f),
                    size = Size(eyeW, headR * 0.03f),
                    style = Stroke(width = size.minDimension * 0.012f, cap = StrokeCap.Round)
                )
            } else {
                val eyeH = headR * 0.34f
                drawOval(
                    color = Color.White,
                    topLeft = Offset(eyeCx - eyeW / 2f, eyeY - eyeH / 2f),
                    size = Size(eyeW, eyeH)
                )
                val pupil = Offset(
                    eyeCx + lookX * eyeW * 0.22f,
                    eyeY + lookY * eyeH * 0.25f
                )
                drawCircle(color = dark, radius = eyeW * 0.28f, center = pupil)
                drawCircle(
                    color = Color.White,
                    radius = eyeW * 0.09f,
                    center = pupil + Offset(eyeW * 0.08f, -eyeH * 0.12f)
                )
            }
        }

        // Muzzle / snout / nose area
        if (style.muzzle == MuzzleKind.SNOUT) {
            val snout = Offset(cx, cy + headR * 0.16f)
            drawOval(
                Color(style.inner),
                topLeft = snout - Offset(headR * 0.26f, headR * 0.17f),
                size = Size(headR * 0.52f, headR * 0.34f)
            )
            drawOval(
                Color(style.outline),
                topLeft = snout - Offset(headR * 0.26f, headR * 0.17f),
                size = Size(headR * 0.52f, headR * 0.34f),
                style = Stroke(width = outlineW * 0.8f)
            )
            for (side in listOf(-1f, 1f)) {
                drawCircle(
                    Color(style.outline),
                    radius = headR * 0.035f,
                    center = snout + Offset(side * headR * 0.10f, 0f)
                )
            }
        } else if (style.muzzle == MuzzleKind.MUZZLE) {
            val snout = Offset(cx, cy + headR * 0.18f)
            drawOval(
                Color(style.inner),
                topLeft = snout - Offset(headR * 0.25f, headR * 0.16f),
                size = Size(headR * 0.50f, headR * 0.32f)
            )
            drawOval(
                Color(style.outline),
                topLeft = snout - Offset(headR * 0.25f, headR * 0.16f),
                size = Size(headR * 0.50f, headR * 0.32f),
                style = Stroke(width = outlineW * 0.7f)
            )
            drawCircle(Color(style.dark), radius = headR * 0.07f, center = snout - Offset(0f, headR * 0.06f))
        } else {
            // Nose — small rounded button
            drawCircle(
                color = Color(style.dark),
                radius = headR * 0.06f,
                center = Offset(cx, cy + headR * 0.03f)
            )
        }

        // Cheeks
        for (side in listOf(-1f, 1f)) {
            drawCircle(
                color = Color(0xFFFF8A80).copy(alpha = 0.7f),
                radius = headR * 0.13f,
                center = Offset(cx + side * headR * 0.62f, cy + headR * 0.26f)
            )
        }

        // Whiskers (cat)
        if (style.muzzle == MuzzleKind.WHISKERS) {
            for (side in listOf(-1f, 1f)) {
                val startX = cx + side * headR * 0.62f
                val startY = cy + headR * 0.10f
                repeat(3) { i ->
                    drawLine(
                        dark,
                        start = Offset(startX, startY - headR * 0.05f * i),
                        end = Offset(startX + side * headR * 0.42f, startY - headR * (0.06f + 0.05f * i)),
                        strokeWidth = headR * 0.03f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Eyebrows — outer ends lifted for a happy expression
        val browStroke = size.minDimension * 0.014f
        val browCenter = eyeDX
        val browY = cy - headR * 0.44f
        for (side in listOf(-1f, 1f)) {
            // Inner tip (towards the nose) sits lower; outer tip is raised → happy look.
            val inner = Offset(cx + side * (browCenter - headR * 0.24f), browY + headR * 0.06f)
            val outer = Offset(cx + side * (browCenter + headR * 0.24f), browY - headR * 0.06f)
            drawLine(
                color = dark,
                start = inner,
                end = outer,
                strokeWidth = browStroke,
                cap = StrokeCap.Round
            )
        }

        // Smile — a happy downward bulge (the old arc bowed upward, which read as a frown)
        drawArc(
            color = dark,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - headR * 0.38f, cy + headR * 0.10f),
            size = Size(headR * 0.76f, headR * 0.60f),
            style = Stroke(width = size.minDimension * 0.016f, cap = StrokeCap.Round)
        )
    }
}
