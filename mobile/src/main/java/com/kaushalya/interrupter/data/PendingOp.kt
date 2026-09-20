package com.kaushalya.interrupter.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "pending_ops")
data class PendingOp(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val opType: String,
    val targetRemoteId: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DELETE_KID = "DELETE_KID"
    }
}

@Dao
interface PendingOpDao {
    @Query("SELECT * FROM pending_ops ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingOp>

    @Query("SELECT * FROM pending_ops WHERE opType = :opType ORDER BY createdAt ASC")
    suspend fun getByType(opType: String): List<PendingOp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(op: PendingOp)

    @Query("DELETE FROM pending_ops WHERE id = :id")
    suspend fun deleteById(id: String)
}
