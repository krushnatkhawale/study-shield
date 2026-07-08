package com.kaushalya.interrupter.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kaushalya.interrupter.data.InterruptionCommand
import com.kaushalya.interrupter.data.StudyRepository
import kotlinx.serialization.json.Json

class StudySessionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val ip = inputData.getString("TARGET_IP") ?: return Result.failure()
        val commandJson = inputData.getString("COMMAND_JSON") ?: return Result.failure()
        val sessionId = inputData.getString("SESSION_ID") ?: return Result.failure()

        // Fixed: Using getInstance instead of constructor due to private access
        val repository = StudyRepository.getInstance(applicationContext)
        val json = Json { ignoreUnknownKeys = true }
        
        return try {
            val command = json.decodeFromString<InterruptionCommand>(commandJson)
            val result = repository.sendCommand(ip, command)
            
            if (result.isSuccess) {
                // If it's not a recurring session, we might want to mark it as inactive
                // But recurrence is handled by WorkManager's PeriodicWorkRequest if we chose that.
                // For simplicity in this implementation, we'll just return success.
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
