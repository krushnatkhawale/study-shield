package com.kaushalya.interrupter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingFeedbackDao {

    @Query("SELECT * FROM pending_feedback ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingFeedback>

    @Insert
    suspend fun insert(feedback: PendingFeedback)

    @Query("DELETE FROM pending_feedback WHERE id = :id")
    suspend fun delete(id: Long)
}
