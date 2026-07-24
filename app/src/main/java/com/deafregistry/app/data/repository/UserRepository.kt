package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.CreateUserRequest
import com.deafregistry.app.data.remote.dto.ResetPasswordRequest
import com.deafregistry.app.data.remote.dto.UpdateUserRequest

class UserRepository(private val api: ApiService) {
    suspend fun list() = api.getUsers()

    suspend fun create(name: String, email: String, password: String, role: String, teacherId: Int?) =
        api.createUser(CreateUserRequest(name, email, password, role, teacherId))

    suspend fun update(id: Int, name: String, role: String, teacherId: Int?, isActive: Boolean) =
        api.updateUser(id, UpdateUserRequest(name, role, teacherId, isActive))

    suspend fun resetPassword(id: Int, newPassword: String) =
        api.resetUserPassword(id, ResetPasswordRequest(newPassword))

    suspend fun deactivate(id: Int) = api.deactivateUser(id)

    suspend fun pendingDevices() = api.getPendingDevices()
    suspend fun approveDevice(id: Int) = api.approveDevice(id)
    suspend fun rejectDevice(id: Int) = api.rejectDevice(id)

    suspend fun pendingSignups() = api.getPendingSignups()
    suspend fun approveSignup(id: Int) = api.approveSignup(id)
    suspend fun rejectSignup(id: Int) = api.rejectSignup(id)
}
