package com.kaushalya.interrupter.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM wifi_networks ORDER BY lastConnected DESC")
    fun getAllNetworks(): Flow<List<WifiNetwork>>

    @Query("SELECT * FROM wifi_networks WHERE ssid = :ssid LIMIT 1")
    suspend fun getNetworkBySsid(ssid: String): WifiNetwork?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetwork(network: WifiNetwork)

    @Update
    suspend fun updateNetwork(network: WifiNetwork)

    @Query("SELECT * FROM connected_tvs WHERE networkId = :networkId ORDER BY lastConnected DESC")
    fun getTvsForNetwork(networkId: String): Flow<List<ConnectedTV>>

    @Query("SELECT * FROM connected_tvs WHERE networkId = :networkId AND ipAddress = :ip LIMIT 1")
    suspend fun getTvByIp(networkId: String, ip: String): ConnectedTV?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTv(tv: ConnectedTV)

    @Update
    suspend fun updateTv(tv: ConnectedTV)

    @Delete
    suspend fun deleteTv(tv: ConnectedTV)

    @Transaction
    @Query("SELECT * FROM wifi_networks ORDER BY lastConnected DESC")
    fun getNetworksWithTvs(): Flow<List<NetworkWithTvs>>
}

data class NetworkWithTvs(
    @Embedded val network: WifiNetwork,
    @Relation(
        parentColumn = "id",
        entityColumn = "networkId"
    )
    val tvs: List<ConnectedTV>
)
