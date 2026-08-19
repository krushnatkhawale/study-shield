package com.kaushalya.interrupter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KidProfileDao {
    @Query("SELECT * FROM kid_profiles ORDER BY lastModified DESC")
    fun getAllKids(): Flow<List<KidProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKid(kid: KidProfile)

    @Update
    suspend fun updateKid(kid: KidProfile)

    @Delete
    suspend fun deleteKid(kid: KidProfile)

    @Query("SELECT * FROM kid_profiles WHERE id = :id LIMIT 1")
    suspend fun getKidById(id: String): KidProfile?

    @Query("UPDATE kid_profiles SET syncStatus = :status, remoteId = :remoteId WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: Int, remoteId: String?)

    @Query("SELECT * FROM kid_profiles WHERE syncStatus != 1")
    suspend fun getUnsyncedKids(): List<KidProfile>

    @Query("SELECT * FROM kid_profiles WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): KidProfile?
}
