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
}
