package com.kaushalya.interrupter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions WHERE isActive = 1 ORDER BY startTime ASC")
    fun getAllActiveSessions(): Flow<List<StudySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession)

    @Update
    suspend fun updateSession(session: StudySession)

    @Delete
    suspend fun deleteSession(session: StudySession)

    @Query("UPDATE study_sessions SET isActive = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: String)

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAll()
}
