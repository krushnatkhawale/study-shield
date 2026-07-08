package com.kaushalya.interrupter.data

import com.kaushalya.interrupter.network.RetrofitClient

class StudentRepository(private val dao: StudentDao) {

    private val api get() = RetrofitClient.getApiService()

    suspend fun listStudents(): Result<List<StudentEntity>> {
        return try {
            val response = api.listStudents()
            if (response.isSuccessful) {
                val remoteStudents = response.body().orEmpty()
                val entities = remoteStudents.map { s ->
                    StudentEntity(
                        remoteId = s.studentId,
                        name = s.name ?: "",
                        gender = s.gender,
                        birthYear = s.birthYear,
                        studentClass = s.studentClass,
                        syncPending = false
                    )
                }
                entities.forEach { dao.insert(it) }
                Result.success(dao.getAll())
            } else {
                Result.failure(Exception("Failed to fetch students"))
            }
        } catch (e: Exception) {
            val cached = dao.getAll()
            Result.success(cached)
        }
    }

    suspend fun addStudent(name: String, gender: String?, birthYear: Int?, studentClass: String?): Result<StudentEntity> {
        val local = StudentEntity(
            name = name,
            gender = gender,
            birthYear = birthYear,
            studentClass = studentClass,
            syncPending = true
        )
        dao.insert(local)
        return try {
            val response = api.addStudent(StudentRequest(name, gender, birthYear, studentClass))
            if (response.isSuccessful && response.body() != null) {
                val remote = response.body()!!
                dao.markSynced(local.localId, remote.studentId)
                val synced = local.copy(remoteId = remote.studentId, syncPending = false)
                Result.success(synced)
            } else {
                Result.success(local)
            }
        } catch (e: Exception) {
            Result.success(local)
        }
    }

    suspend fun updateStudent(localId: String, name: String, gender: String?, birthYear: Int?, studentClass: String?): Result<StudentEntity> {
        val updated = StudentEntity(
            localId = localId,
            name = name,
            gender = gender,
            birthYear = birthYear,
            studentClass = studentClass,
            syncPending = true,
            lastModified = System.currentTimeMillis()
        )
        dao.update(updated)
        val remoteId = dao.getAll().find { it.localId == localId }?.remoteId
        if (remoteId != null) {
            try {
                api.updateStudent(remoteId, StudentRequest(name, gender, birthYear, studentClass))
                dao.markSynced(localId, remoteId)
            } catch (_: Exception) {}
        }
        return Result.success(updated)
    }

    suspend fun deleteStudent(localId: String) {
        val remoteId = dao.getAll().find { it.localId == localId }?.remoteId
        if (remoteId != null) {
            try {
                api.deleteStudent(remoteId)
                dao.deleteById(localId)
                return
            } catch (_: Exception) {}
        }
        dao.markDeleted(localId)
    }

    suspend fun syncPending() {
        val pending = dao.getPendingSync()
        for (p in pending) {
            try {
                if (p.deleted) {
                    if (p.remoteId != null) api.deleteStudent(p.remoteId)
                    dao.deleteById(p.localId)
                } else if (p.remoteId != null) {
                    val resp = api.updateStudent(p.remoteId, StudentRequest(p.name, p.gender, p.birthYear, p.studentClass))
                    if (resp.isSuccessful) dao.markSynced(p.localId, p.remoteId)
                } else {
                    val resp = api.addStudent(StudentRequest(p.name, p.gender, p.birthYear, p.studentClass))
                    if (resp.isSuccessful && resp.body() != null) {
                        dao.markSynced(p.localId, resp.body()!!.studentId)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
