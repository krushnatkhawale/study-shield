package com.kaushalya.interrupter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizResultDao {
    @Query("SELECT * FROM quiz_results ORDER BY completedAt DESC")
    fun getAllResults(): Flow<List<QuizResult>>

    @Query("SELECT * FROM quiz_results ORDER BY completedAt DESC LIMIT :limit")
    fun getRecentResults(limit: Int): Flow<List<QuizResult>>

    @Query("SELECT * FROM quiz_results WHERE syncStatus != 1 ORDER BY completedAt ASC")
    suspend fun getUnsyncedResults(): List<QuizResult>

    @Query("SELECT * FROM quiz_results WHERE id = :id")
    suspend fun getResultById(id: String): QuizResult?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: QuizResult)

    @Update
    suspend fun updateResult(result: QuizResult)

    @Delete
    suspend fun deleteResult(result: QuizResult)

    @Query("UPDATE quiz_results SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int)

    @Query("UPDATE quiz_results SET syncStatus = :status, backendId = :backendId WHERE id = :id")
    suspend fun updateSyncStatusAndBackendId(id: String, status: Int, backendId: Long)

    @Query("SELECT * FROM quiz_results WHERE backendId = :backendId LIMIT 1")
    suspend fun getByBackendId(backendId: Long): QuizResult?
}
