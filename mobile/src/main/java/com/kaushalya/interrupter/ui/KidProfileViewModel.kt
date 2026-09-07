package com.kaushalya.interrupter.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.data.KidProfileRepository
import com.kaushalya.interrupter.data.KidProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KidProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KidProfileRepository.getInstance(application)

    val kidProfiles: StateFlow<List<KidProfile>> = repository.getAllKids()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var showAddDialog by mutableStateOf(false)
    var editingKid by mutableStateOf<KidProfile?>(null)

    fun saveKid(name: String, gender: String, birthYear: Int, dob: Long?, grade: String, syllabus: String?, avatar: String = "hero") {
        viewModelScope.launch {
            val kid = editingKid?.copy(
                name = name,
                gender = gender,
                birthYear = birthYear,
                dateOfBirth = dob,
                grade = grade,
                syllabus = syllabus,
                avatar = avatar,
                lastModified = System.currentTimeMillis(),
                syncStatus = 2 // Mark as modified
            ) ?: KidProfile(
                name = name,
                gender = gender,
                birthYear = birthYear,
                dateOfBirth = dob,
                grade = grade,
                syllabus = syllabus,
                avatar = avatar,
                syncStatus = 0 // New local record
            )
            
            repository.saveKid(kid)
            Log.d(TAG, "Saved Kid (JSON): ${Json.encodeToString(kid)}")
            repository.refreshProfileKids()

            showAddDialog = false
            editingKid = null
        }
    }

    fun deleteKid(kid: KidProfile) {
        viewModelScope.launch {
            repository.deleteKid(kid)
            Log.d(TAG, "Deleted Kid (JSON): ${Json.encodeToString(kid)}")
            repository.refreshProfileKids()
        }
    }

    /** Re-syncs kid profiles from the backend (manual refresh). */
    fun refresh() {
        viewModelScope.launch {
            repository.refreshProfileKids()
        }
    }

    private companion object {
        const val TAG = "KidProfile"
    }
}
