package com.kaushalya.interrupter.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PrintWriter
import java.net.Socket

class StudyRepository private constructor(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val database = AppDatabase.getDatabase(context)
    private val studySessionDao = database.studySessionDao()
    
    private val _discoveredTvs = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredTvs: StateFlow<List<NsdServiceInfo>> = _discoveredTvs

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val json = Json { ignoreUnknownKeys = true }

    private var isResolving = false
    private val resolveQueue = mutableListOf<NsdServiceInfo>()

    fun startDiscovery() {
        Log.d("StudyRepository", "Starting NSD discovery for '_interrupter._tcp'...")
        stopDiscovery()
        
        _discoveredTvs.value = emptyList()
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("StudyRepository", "Discovery successfully started: $regType")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("StudyRepository", "Service found: Name='${service.serviceName}', Type='${service.serviceType}'")
                
                if (service.serviceType.contains("_interrupter")) {
                    synchronized(resolveQueue) {
                        if (resolveQueue.none { it.serviceName == service.serviceName } && 
                            _discoveredTvs.value.none { it.serviceName == service.serviceName }) {
                            resolveQueue.add(service)
                            processNextInResolveQueue()
                        }
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredTvs.value = _discoveredTvs.value.filter { it.serviceName != service.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        
        try {
            nsdManager.discoverServices("_interrupter._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("StudyRepository", "Exception starting discoverServices", e)
        }
    }

    private fun processNextInResolveQueue() {
        synchronized(resolveQueue) {
            if (isResolving || resolveQueue.isEmpty()) return
            
            val nextService = resolveQueue.removeAt(0)
            isResolving = true
            
            nsdManager.resolveService(nextService, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                    isResolving = false
                    processNextInResolveQueue()
                }

                override fun onServiceResolved(si: NsdServiceInfo) {
                    val ip = si.host?.hostAddress
                    if (ip != null) {
                        val currentList = _discoveredTvs.value.toMutableList()
                        if (currentList.none { it.serviceName == si.serviceName }) {
                            currentList.add(si)
                            _discoveredTvs.value = currentList
                        }
                    }
                    isResolving = false
                    processNextInResolveQueue()
                }
            })
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { 
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {}
        }
        discoveryListener = null
        synchronized(resolveQueue) {
            resolveQueue.clear()
            isResolving = false
        }
    }

    suspend fun sendCommand(ip: String, command: InterruptionCommand): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ip, 8888)
            val out = PrintWriter(socket.getOutputStream(), true)
            out.println(json.encodeToString(command))
            socket.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveSessions(): Flow<List<StudySession>> = studySessionDao.getAllActiveSessions()
    
    suspend fun saveSession(session: StudySession) {
        studySessionDao.insertSession(session)
    }

    suspend fun deactivateSession(sessionId: String) {
        studySessionDao.deactivateSession(sessionId)
    }

    companion object {
        @Volatile
        private var INSTANCE: StudyRepository? = null

        fun getInstance(context: Context): StudyRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StudyRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
