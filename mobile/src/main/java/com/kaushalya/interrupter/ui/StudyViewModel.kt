package com.kaushalya.interrupter.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.kaushalya.interrupter.data.*
import com.kaushalya.interrupter.worker.StudySessionWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository.getInstance(application)
    private val historyRepository = HistoryRepository(application)
    private val workManager = WorkManager.getInstance(application)
    
    var sessionDuration by mutableStateOf(15)
    var recurrence by mutableStateOf(Recurrence.ONE_TIME)
    var isScheduled by mutableStateOf(false)
    var selectedContent by mutableStateOf<StudyContent?>(null)
    
    // Preview State
    var previewContent by mutableStateOf<StudyContent?>(null)

    private val _uiState = MutableStateFlow<StudyUiState>(StudyUiState.Idle)
    val uiState: StateFlow<StudyUiState> = _uiState

    val discoveredTvs = repository.discoveredTvs
    var selectedTvIp by mutableStateOf<String?>(null)

    val scheduledSessions = repository.getActiveSessions()

    // Discovery State
    var isDiscovering by mutableStateOf(false)
    private var discoveryJob: Job? = null

    // Manual Control State (for Library/Control Screen)
    var manualIp by mutableStateOf("")
    var manualMessage by mutableStateOf("")
    var manualMode by mutableIntStateOf(0) // 0: Block, 1: Timer, 2: MCQ, 3: FITB
    var manualDuration by mutableStateOf("10")
    var manualUnit by mutableStateOf("Seconds")
    val manualMcqOptions = mutableStateListOf("", "", "", "")
    var manualMcqCorrectIndex by mutableIntStateOf(0)
    var manualFitbAnswer by mutableStateOf("")

    fun startDiscovery() {
        isDiscovering = true
        repository.startDiscovery()
        
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            delay(20000) 
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        isDiscovering = false
        repository.stopDiscovery()
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun setDuration(minutes: Int) {
        sessionDuration = minutes
    }

    fun setRecurrenceType(type: Recurrence) {
        recurrence = type
    }

    fun selectContent(content: StudyContent) {
        selectedContent = content
    }
    
    fun showPreview(content: StudyContent) {
        previewContent = content
    }
    
    fun hidePreview() {
        previewContent = null
    }

    fun startStudySession(contentOverride: StudyContent? = null) {
        val ip = selectedTvIp ?: manualIp
        val content = contentOverride ?: selectedContent
        
        if (ip.isEmpty()) {
            _uiState.value = StudyUiState.Error("No TV selected")
            return
        }
        
        if (content == null) {
            _uiState.value = StudyUiState.Error("No content selected")
            return
        }

        viewModelScope.launch {
            _uiState.value = StudyUiState.Loading
            
            val commandType = when (content.type) {
                ContentType.QUIZ -> if (!content.questions.isNullOrEmpty()) "MCQ" else "FITB"
                else -> "STUDY_SESSION"
            }
            
            // Populating both the questions list AND the legacy fields for redundancy
            val firstQ = content.questions?.firstOrNull()
            val command = InterruptionCommand(
                type = commandType,
                duration = if (commandType == "STUDY_SESSION") sessionDuration.toLong() else sessionDuration.toLong() * 60,
                contentName = content.name,
                category = content.category,
                questions = content.questions,
                // Fallback fields populated with first question
                question = firstQ?.question ?: content.question,
                options = firstQ?.options ?: content.options,
                answer = firstQ?.answer ?: content.answer
            )

            Log.d("StudyViewModel", "Sending command: type=$commandType, questionsCount=${content.questions?.size ?: 0}")

            if (isScheduled && contentOverride == null) {
                scheduleSession(ip, command, content)
            } else {
                val result = repository.sendCommand(ip, command)
                if (result.isSuccess) {
                    saveToHistory(ip)
                    
                    repository.saveSession(
                        StudySession(
                            durationMinutes = sessionDuration,
                            startTime = System.currentTimeMillis(),
                            recurrence = recurrence,
                            content = content,
                            isActive = false
                        )
                    )
                    _uiState.value = StudyUiState.Success("Study session started on TV")
                    hidePreview()
                } else {
                    _uiState.value = StudyUiState.Error("Failed to start session: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun saveToHistory(ip: String) {
        viewModelScope.launch {
            val tvName = discoveredTvs.value.find { it.host?.hostAddress == ip }?.serviceName ?: "StudyShield TV"
            historyRepository.saveTvConnection(
                ssid = historyRepository.getCurrentSsid(),
                tvName = tvName,
                ipAddress = ip
            )
        }
    }

    fun sendManualCommand(isUnlock: Boolean = false) {
        val ip = if (manualIp.isNotEmpty()) manualIp else selectedTvIp
        
        if (ip.isNullOrEmpty()) {
            _uiState.value = StudyUiState.Error("Please select a TV or enter an IP address")
            return
        }

        val command = if (isUnlock) {
            InterruptionCommand(type = "UNLOCK")
        } else {
            when (manualMode) {
                0 -> InterruptionCommand(type = "BLOCK", message = manualMessage)
                1 -> {
                    val durationValue = manualDuration.toLongOrNull() ?: 10L
                    val seconds = when (manualUnit) {
                        "Minutes" -> durationValue * 60
                        "Hours" -> durationValue * 3600
                        else -> durationValue
                    }
                    InterruptionCommand(type = "TIMER", message = manualMessage, duration = seconds)
                }
                2 -> {
                    // Wrap manual MCQ into a QuizQuestion list
                    InterruptionCommand(
                        type = "MCQ", 
                        questions = listOf(QuizQuestion(manualMessage, manualMcqOptions.toList(), manualMcqCorrectIndex.toString())),
                        question = manualMessage,
                        options = manualMcqOptions.toList(),
                        answer = manualMcqCorrectIndex.toString()
                    )
                }
                3 -> {
                    // Wrap manual FITB into a QuizQuestion list
                    InterruptionCommand(
                        type = "FITB", 
                        questions = listOf(QuizQuestion(manualMessage, emptyList(), manualFitbAnswer)),
                        question = manualMessage,
                        answer = manualFitbAnswer
                    )
                }
                else -> null
            }
        }

        if (command != null) {
            viewModelScope.launch {
                val result = repository.sendCommand(ip, command)
                if (result.isSuccess) {
                    if (!isUnlock) saveToHistory(ip)
                    _uiState.value = StudyUiState.Success(if (isUnlock) "TV Unlocked!" else "Interruption Activated!")
                } else {
                    _uiState.value = StudyUiState.Error("Connection failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private suspend fun scheduleSession(ip: String, command: InterruptionCommand, content: StudyContent) {
        val sessionId = java.util.UUID.randomUUID().toString()
        val session = StudySession(
            id = sessionId,
            durationMinutes = sessionDuration,
            startTime = System.currentTimeMillis() + 60000, 
            recurrence = recurrence,
            content = content,
            isActive = true
        )
        
        repository.saveSession(session)

        val inputData = workDataOf(
            "TARGET_IP" to ip,
            "COMMAND_JSON" to Json.encodeToString(command),
            "SESSION_ID" to sessionId
        )

        val workRequest = when (recurrence) {
            Recurrence.ONE_TIME -> {
                OneTimeWorkRequestBuilder<StudySessionWorker>()
                    .setInitialDelay(1, TimeUnit.MINUTES) 
                    .setInputData(inputData)
                    .build()
            }
            Recurrence.DAILY -> {
                PeriodicWorkRequestBuilder<StudySessionWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .setInputData(inputData)
                    .build()
            }
            Recurrence.WEEKLY -> {
                PeriodicWorkRequestBuilder<StudySessionWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(7, TimeUnit.DAYS)
                    .setInputData(inputData)
                    .build()
            }
        }
        
        if (workRequest is OneTimeWorkRequest) {
            workManager.enqueueUniqueWork(sessionId, ExistingWorkPolicy.REPLACE, workRequest)
        } else if (workRequest is PeriodicWorkRequest) {
            workManager.enqueueUniquePeriodicWork(sessionId, ExistingPeriodicWorkPolicy.UPDATE, workRequest)
        }

        _uiState.value = StudyUiState.Success("Session scheduled successfully!")
    }
    
    fun resetState() {
        _uiState.value = StudyUiState.Idle
    }
}
