package com.kaushalya.interrupter.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TvManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRepository = HistoryRepository(application)
    private val studyRepository = StudyRepository.getInstance(application)

    val currentSsid = mutableStateOf(historyRepository.getCurrentSsid())
    
    val networksWithTvs: StateFlow<List<NetworkWithTvs>> = historyRepository.getNetworksWithTvs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentNetworkTvs: StateFlow<List<ConnectedTV>> = networksWithTvs.map { networks ->
        networks.find { it.network.ssid == currentSsid.value }?.tvs ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discoveredTvs = studyRepository.discoveredTvs

    var isDiscovering by mutableStateOf(false)
    private var discoveryJob: Job? = null

    fun refreshCurrentSsid() {
        currentSsid.value = historyRepository.getCurrentSsid()
    }

    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        studyRepository.startDiscovery()
        
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            delay(20000) // 20 second window
            stopDiscovery()
        }
    }

    fun stopDiscovery() {
        isDiscovering = false
        studyRepository.stopDiscovery()
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun saveConnection(tvName: String, ipAddress: String) {
        viewModelScope.launch {
            historyRepository.saveTvConnection(
                ssid = currentSsid.value,
                tvName = tvName,
                ipAddress = ipAddress
            )
            // Visual feedback for the parent
            Toast.makeText(getApplication(), "TV profile remembered for ${currentSsid.value}", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleFavorite(tv: ConnectedTV) {
        viewModelScope.launch {
            historyRepository.toggleFavorite(tv)
        }
    }

    fun forgetTv(tv: ConnectedTV) {
        viewModelScope.launch {
            historyRepository.forgetTv(tv)
            Toast.makeText(getApplication(), "TV profile removed", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}
