package com.kaushalya.interrupter.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val historyDao = database.historyDao()

    /**
     * Gets all networks and their associated TVs, sorted by last connection time.
     * Future Sync Hook: Observe this flow and trigger remote sync when local changes detected.
     */
    fun getNetworksWithTvs(): Flow<List<NetworkWithTvs>> = historyDao.getNetworksWithTvs()

    fun getTvsForNetwork(networkId: String): Flow<List<ConnectedTV>> = historyDao.getTvsForNetwork(networkId)

    /**
     * Saves or updates a TV connection under the current WiFi identity.
     */
    @SuppressLint("HardwareIds")
    suspend fun saveTvConnection(ssid: String, tvName: String, ipAddress: String, deviceInfo: String? = null) {
        var network = historyDao.getNetworkBySsid(ssid)
        if (network == null) {
            network = WifiNetwork(ssid = ssid)
            historyDao.insertNetwork(network)
        } else {
            network = network.copy(lastConnected = System.currentTimeMillis())
            historyDao.updateNetwork(network)
        }

        val existingTv = historyDao.getTvByIp(network.id, ipAddress)
        if (existingTv == null) {
            val newTv = ConnectedTV(
                networkId = network.id,
                name = tvName,
                ipAddress = ipAddress,
                deviceInfo = deviceInfo,
                syncStatus = 0 // Local only
            )
            historyDao.insertTv(newTv)
        } else {
            val updatedTv = existingTv.copy(
                name = tvName,
                lastConnected = System.currentTimeMillis(),
                deviceInfo = deviceInfo ?: existingTv.deviceInfo,
                syncStatus = 2 // Modified
            )
            historyDao.updateTv(updatedTv)
        }
        
        // Future Sync Hook: Trigger REST API POST to /api/v1/history/sync
        // triggerBackgroundSync()
    }

    suspend fun toggleFavorite(tv: ConnectedTV) {
        historyDao.updateTv(tv.copy(isFavorite = !tv.isFavorite, syncStatus = 2))
    }

    suspend fun forgetTv(tv: ConnectedTV) {
        historyDao.deleteTv(tv)
        // Future Sync Hook: Trigger REST API DELETE for this remoteId
    }

    /**
     * Safely retrieves the current SSID. 
     * Note: Returns "<unknown ssid>" if Location services are disabled on some Android versions.
     */
    fun getCurrentSsid(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info: WifiInfo? = wifiManager.connectionInfo
        val ssid = info?.ssid?.removeSurrounding("\"")
        
        return if (ssid == null || ssid == "<unknown ssid>") {
            "Unknown WiFi"
        } else {
            ssid
        }
    }

    /**
     * Dummy data generator for testing the UI.
     */
    suspend fun insertDummyData() {
        val homeId = "home-uuid"
        historyDao.insertNetwork(WifiNetwork(id = homeId, ssid = "Home_WiFi_5G"))
        historyDao.insertTv(ConnectedTV(networkId = homeId, name = "Living Room LG", ipAddress = "192.168.1.15", isFavorite = true))
        
        val officeId = "office-uuid"
        historyDao.insertNetwork(WifiNetwork(id = officeId, ssid = "Office_Guest"))
        historyDao.insertTv(ConnectedTV(networkId = officeId, name = "Conference TV", ipAddress = "10.0.0.52"))
    }
}
