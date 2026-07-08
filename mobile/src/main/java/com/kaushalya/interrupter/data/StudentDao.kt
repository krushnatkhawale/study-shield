package com.kaushalya.interrupter.data

import androidx.room.*

@Dao
interface StudentDao {
    @Query("SELECT * FROM pending_students WHERE deleted = 0 ORDER BY lastModified DESC")
    suspend fun getAll(): List<StudentEntity>

    @Query("SELECT * FROM pending_students WHERE syncPending = 1 AND deleted = 0")
    suspend fun getPendingSync(): List<StudentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: StudentEntity)

    @Update
    suspend fun update(student: StudentEntity)

    @Query("DELETE FROM pending_students WHERE localId = :localId")
    suspend fun deleteById(localId: String)

    @Query("UPDATE pending_students SET deleted = 1, syncPending = 1 WHERE localId = :localId")
    suspend fun markDeleted(localId: String)

    @Query("UPDATE pending_students SET syncPending = 1 WHERE localId = :localId")
    suspend fun markSyncPending(localId: String)

    @Query("UPDATE pending_students SET syncPending = 0, remoteId = :remoteId WHERE localId = :localId")
    suspend fun markSynced(localId: String, remoteId: String?)
}
