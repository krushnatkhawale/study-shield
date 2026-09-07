package com.kaushalya.interrupter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class TvServerService : Service() {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val CHANNEL_ID = "TvServerChannel"
    private val NOTIFICATION_ID = 1
    private var wakeLock: PowerManager.WakeLock? = null
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var persistenceManager: LockPersistenceManager

    private lateinit var nsdManager: NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        persistenceManager = LockPersistenceManager(this)
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        createNotificationChannel()
        val notification = createNotification("Listening for commands...")
        startForeground(NOTIFICATION_ID, notification)
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Interrupter::WakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startServer()
            checkSavedLock()
            registerService(8888)
        }
        return START_STICKY
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, "device_name")
            ?: Settings.Global.getString(contentResolver, "device_name_ext")
            ?: Build.MODEL
    }

    private fun registerService(port: Int) {
        val deviceName = getDeviceName()
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Interrupter-$deviceName"
            serviceType = "_interrupter._tcp"
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("TvServerService", "NSD Service registered: ${NsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("TvServerService", "NSD Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d("TvServerService", "NSD Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("TvServerService", "NSD Unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun checkSavedLock() {
        val savedCommand = persistenceManager.getSavedLockCommand()
        if (savedCommand != null && savedCommand.type != "UNLOCK") {
            // Replaying a persisted command: there is no live mobile waiting on this callback.
            // The saved mobileIp/resultCallbackPort are from the device that last sent it and may
            // be stale (e.g. after the account switched on the mobile). Strip them so the TV does
            // not attempt to deliver the result to a dead/old mobile address and silently lose it.
            handleCommand(savedCommand.copy(mobileIp = null, resultCallbackPort = null), save = false)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TV Lock Listener Service",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String, fullScreenIntent: PendingIntent? = null): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("StudyShield Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        if (fullScreenIntent != null) {
            builder.setFullScreenIntent(fullScreenIntent, true)
        }

        return builder.build()
    }

    private fun startServer() {
        thread {
            try {
                serverSocket = ServerSocket(8888)
                while (isRunning) {
                    val client: Socket = serverSocket?.accept() ?: break
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val receivedData = reader.readLine()
                    
                    if (receivedData != null) {
                        try {
                            val command = json.decodeFromString<InterruptionCommand>(receivedData)
                            handleCommand(command, save = true)
                        } catch (e: Exception) {
                            Log.e("TvServerService", "Failed to parse JSON", e)
                        }
                    }
                    client.close()
                }
            } catch (e: Exception) {
                Log.e("TvServerService", "Server error", e)
                if (isRunning) {
                    Thread.sleep(5000)
                    startServer()
                }
            }
        }
    }

    private fun handleCommand(command: InterruptionCommand, save: Boolean) {
        if (save) {
            if (command.type == "UNLOCK") {
                persistenceManager.clearLockCommand()
            } else if (command.type != "TTS_CAP_CHECK") {
                persistenceManager.saveLockCommand(command)
            }
        }

        // TTS capability probe — answered directly, no UI.
        if (command.type == "TTS_CAP_CHECK") {
            respondTtsCapabilities(command)
            return
        }

        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10000L)
            }
        } catch (e: Exception) {}

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("COMMAND_TYPE", command.type)
            putExtra("MESSAGE", command.message)
            putExtra("DURATION", command.duration ?: 0L)
            putExtra("CONTENT_NAME", command.contentName)
            putExtra("CATEGORY", command.category)
            putExtra("MOBILE_IP", command.mobileIp)
            putExtra("RESULT_CALLBACK_PORT", command.resultCallbackPort ?: 0)
            putExtra("KID_NAME", command.kidName)
            putExtra("GREETING_LANGUAGE", command.greetingLanguage ?: "en")
            putExtra("AVATAR_ID", command.avatarId ?: "hero")
            putExtra("REVEAL_READ_LOCK", command.revealReadLock ?: false)
            putExtra("AUTO_DICTATION", command.autoDictation ?: false)
            putExtra("FAST_ANSWER_THRESHOLD_MS", command.fastAnswerThresholdMs ?: 1500L)
            
            // Pass all questions as JSON for multi-question support
            val questions = command.questions
            if (!questions.isNullOrEmpty()) {
                val questionsJson = json.encodeToString(ListSerializer(QuizQuestion.serializer()), questions)
                putExtra("QUESTIONS_JSON", questionsJson)
                // Also pass first question as fallback
                val firstQ = questions.first()
                putExtra("QUESTION", firstQ.question)
                putStringArrayListExtra("OPTIONS", ArrayList(firstQ.options))
                putExtra("ANSWER", firstQ.answer)
            } else {
                // Fallback for manual commands
                putExtra("QUESTION", command.question)
                command.options?.let { putStringArrayListExtra("OPTIONS", ArrayList(it)) }
                putExtra("ANSWER", command.answer)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, createNotification("Incoming: ${command.type}", pendingIntent))
        
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("TvServerService", "Direct launch failed", e)
        }
    }

    /** Answers a mobile `TTS_CAP_CHECK` with the engine's speakable locale tags. */
    private fun respondTtsCapabilities(command: InterruptionCommand) {
        val mobileIp = command.mobileIp
        val port = command.resultCallbackPort
        if (mobileIp.isNullOrBlank() || port == null || port <= 0) {
            Log.w("TvServerService", "TTS_CAP_CHECK ignored: missing mobileIp/resultCallbackPort")
            return
        }
        thread {
            try {
                val locales = TtsCapabilities.supportedLocales.ifEmpty { probeEngineLanguages() }
                val reply = TtsCapabilitiesMessage(supportedLanguages = locales)
                val socket = Socket(mobileIp, port)
                val out = java.io.PrintWriter(socket.getOutputStream(), true)
                out.println(json.encodeToString(TtsCapabilitiesMessage.serializer(), reply))
                socket.close()
                Log.d("TvServerService", "TTS_CAP_CHECK reply sent: ${locales.size} locales")
            } catch (e: Exception) {
                Log.e("TvServerService", "TTS_CAP_CHECK reply failed", e)
            }
        }
    }

    /**
     * Best-effort: if MainActivity hasn't initialised its TTS yet, spin up a throwaway engine
     * to enumerate the voices, then shut it down. Caches the result for future probes.
     */
    private fun probeEngineLanguages(): List<String> {
        if (TtsCapabilities.supportedLocales.isNotEmpty()) return TtsCapabilities.supportedLocales
        val latch = CountDownLatch(1)
        var tts: TextToSpeech? = null
        var locales: List<String> = emptyList()
        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    locales = tts?.voices.orEmpty()
                        .map { it.locale.toLanguageTag() }
                        .distinct()
                        .sorted()
                }
                latch.countDown()
            }
            latch.await(3, TimeUnit.SECONDS)
            TtsCapabilities.supportedLocales = locales
        } catch (e: Exception) {
            Log.e("TvServerService", "TTS engine probe failed", e)
        } finally {
            try { tts?.shutdown() } catch (_: Exception) {}
        }
        return locales
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            serverSocket?.close()
        } catch (e: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
