package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.CreateUserRequest
import com.deafregistry.app.data.remote.dto.ResetPasswordRequest
import com.deafregistry.app.data.remote.dto.ResolvePasswordResetRequest
import com.deafregistry.app.data.remote.dto.UpdateUserRequest

class UserRepository(private val api: ApiService) {
    suspend fun list() = api.getUsers()

    suspend fun create(name: String, email: String, username: String, password: String, role: String, teacherId: Int?) =
        api.createUser(CreateUserRequest(name, email, username, password, role, teacherId))

    suspend fun update(id: Int, name: String, username: String, role: String, teacherId: Int?, isActive: Boolean) =
        api.updateUser(id, UpdateUserRequest(name, username, role, teacherId, isActive))

    suspend fun resetPassword(id: Int, newPassword: String) =
        api.resetUserPassword(id, ResetPasswordRequest(newPassword))

    suspend fun deactivate(id: Int) = api.deactivateUser(id)
    suspend fun permanentlyDelete(id: Int) = api.permanentlyDeleteUser(id)

    suspend fun auditLogs(limit: Int = 100) = api.getAuditLogs(limit)
    suspend fun deleteAllAuditLogs() = api.deleteAllAuditLogs()

    suspend fun pendingSignups() = api.getPendingSignups()
    suspend fun approveSignup(id: Int) = api.approveSignup(id)
    suspend fun rejectSignup(id: Int) = api.rejectSignup(id)

    suspend fun passwordResetRequests() = api.getPasswordResetRequests()
    suspend fun resolvePasswordResetRequest(id: Int, newPassword: String?) =
        api.resolvePasswordResetRequest(id, ResolvePasswordResetRequest(newPassword))
}
