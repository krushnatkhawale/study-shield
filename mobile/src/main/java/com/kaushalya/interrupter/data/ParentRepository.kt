package com.kaushalya.interrupter.data

import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient

class ParentRepository {

    private val api get() = RetrofitClient.getApiService()

    suspend fun listParents(): Result<List<ParentSummary>> {
        Log.d(TAG, "listParents: sessionId=${RetrofitClient.sessionManager?.sessionId}")
        return try {
            val response = api.listParents()
            if (response.isSuccessful) {
                val parents = response.body()?.map { ParentSummary(it.parentId, it.name) } ?: emptyList()
                Result.success(parents)
            } else {
                Result.failure(Exception("Failed to fetch parents: " + response))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addParent(name: String, gender: String? = null, relation: String? = null): Result<Unit> {
        return try {
            val response = api.addParent(ParentRequest(name, gender, relation))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to add parent"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMyName(name: String): Result<Unit> {
        return try {
            val response = api.updateMyName(ParentRequest(name))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update name"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteParent(id: String): Result<Unit> {
        Log.d(TAG, "deleteParent: id=$id")
        return try {
            val response = api.deleteParent(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete parent: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ParentRepository"
    }
}
