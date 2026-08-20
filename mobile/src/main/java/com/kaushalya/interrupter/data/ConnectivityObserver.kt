package com.kaushalya.interrupter.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.runBlocking

class ConnectivityObserver private constructor(context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val sessionManager = SessionManager(context)
    private val quizResultRepository = QuizResultRepository.getInstance(context)
    private val kidProfileRepository = KidProfileRepository.getInstance(context)

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var wasInOfflineMode = false

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available — checking if we need to sync")
                if (wasInOfflineMode || sessionManager.isOfflineMode) {
                    Log.d(TAG, "Was in offline mode, attempting to go online")
                    sessionManager.isOfflineMode = false
                    wasInOfflineMode = false
                    ToastHelper.show("Back online — syncing...")
                    retryPendingSyncs()
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost — entering offline mode")
                sessionManager.isOfflineMode = true
                wasInOfflineMode = true
                ToastHelper.show("Offline — changes will sync when connected")
            }
        }

        connectivityManager.registerNetworkCallback(request, callback!!)
    }

    fun stop() {
        callback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister callback", e)
            }
        }
        callback = null
    }

    private fun retryPendingSyncs() {
        Thread {
            try {
                Log.d(TAG, "Retrying pending quiz result syncs...")
                runBlocking { quizResultRepository.retrySyncFailed() }
                Log.d(TAG, "Retrying pending kid profile syncs...")
                runBlocking { kidProfileRepository.retrySyncFailed() }
                Log.d(TAG, "All pending syncs completed")
                ToastHelper.show("Sync complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error during retry", e)
                ToastHelper.show("Sync failed — will retry later")
            }
        }.start()
    }

    companion object {
        private const val TAG = "ConnectivityObserver"

        @Volatile
        private var INSTANCE: ConnectivityObserver? = null

        fun getInstance(context: Context): ConnectivityObserver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectivityObserver(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
