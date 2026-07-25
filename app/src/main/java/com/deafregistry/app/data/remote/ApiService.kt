package com.deafregistry.app.data.remote

import com.deafregistry.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): MessageResponse

    @GET("auth/me")
    suspend fun me(): UserDto

    @POST("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): ResponseBody

    @Multipart
    @POST("auth/photo")
    suspend fun uploadUserPhoto(@Part photo: MultipartBody.Part): PhotoUploadResponse

    @PUT("auth/share-location")
    suspend fun shareLocation(@Body request: ShareLocationRequest): ShareLocationResponse

    @GET("users/locations")
    suspend fun getUserLocations(): List<UserLocationDto>

    // Municipalities
    @GET("municipalities")
    suspend fun getMunicipalities(): List<MunicipalityDto>

    @POST("municipalities")
    suspend fun createMunicipality(@Body request: NameRequest): MunicipalityDto

    @PUT("municipalities/{id}")
    suspend fun updateMunicipality(@Path("id") id: Int, @Body request: NameRequest): MunicipalityDto

    @DELETE("municipalities/{id}")
    suspend fun deleteMunicipality(@Path("id") id: Int): Response<Unit>

    // Barangays
    @GET("barangays")
    suspend fun getBarangays(@Query("municipality_id") municipalityId: Int? = null): List<BarangayDto>

    @POST("barangays")
    suspend fun createBarangay(@Body request: BarangayRequest): BarangayDto

    @PUT("barangays/{id}")
    suspend fun updateBarangay(@Path("id") id: Int, @Body request: BarangayRequest): BarangayDto

    @DELETE("barangays/{id}")
    suspend fun deleteBarangay(@Path("id") id: Int): Response<Unit>

    // Teachers (BS Conductors)
    @GET("teachers")
    suspend fun getTeachers(): List<TeacherDto>

    @POST("teachers")
    suspend fun createTeacher(@Body request: TeacherRequest): TeacherDto

    @PUT("teachers/{id}")
    suspend fun updateTeacher(@Path("id") id: Int, @Body request: TeacherRequest): TeacherDto

    @DELETE("teachers/{id}")
    suspend fun deleteTeacher(@Path("id") id: Int): Response<Unit>

    // Users (admin)
    @GET("users")
    suspend fun getUsers(): List<UserDto>

    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): UserDto

    @PUT("users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body request: UpdateUserRequest): UserDto

    @POST("users/{id}/reset-password")
    suspend fun resetUserPassword(@Path("id") id: Int, @Body request: ResetPasswordRequest): ResponseBody

    @DELETE("users/{id}")
    suspend fun deactivateUser(@Path("id") id: Int): Response<Unit>

    @DELETE("users/{id}/permanent")
    suspend fun permanentlyDeleteUser(@Path("id") id: Int): Response<Unit>

    // Signup approvals (admin)
    @GET("users/pending-signups")
    suspend fun getPendingSignups(): List<PendingSignupDto>

    @POST("users/{id}/approve-signup")
    suspend fun approveSignup(@Path("id") id: Int): ResponseBody

    @POST("users/{id}/reject-signup")
    suspend fun rejectSignup(@Path("id") id: Int): ResponseBody

    // Deaf individuals
    @GET("deaf-individuals")
    suspend fun getDeafIndividuals(
        @Query("municipality_id") municipalityId: Int? = null,
        @Query("updated_since") updatedSince: String? = null
    ): List<DeafIndividualDto>

    @GET("deaf-individuals/{id}")
    suspend fun getDeafIndividual(@Path("id") id: Int): DeafIndividualDto

    @POST("deaf-individuals")
    suspend fun createDeafIndividual(@Body request: DeafIndividualRequest): CreateResponse

    @PUT("deaf-individuals/{id}")
    suspend fun updateDeafIndividual(@Path("id") id: Int, @Body request: DeafIndividualRequest): Response<Unit>

    @DELETE("deaf-individuals/{id}")
    suspend fun deleteDeafIndividual(@Path("id") id: Int): Response<Unit>

    @Multipart
    @POST("deaf-individuals/{id}/photo")
    suspend fun uploadPhoto(@Path("id") id: Int, @Part photo: MultipartBody.Part): PhotoUploadResponse

    @GET("deaf-individuals/{id}/assignment-history")
    suspend fun getAssignmentHistory(@Path("id") id: Int): List<AssignmentHistoryDto>

    // Teacher bulk reassignment
    @POST("teachers/bulk-reassign")
    suspend fun bulkReassignTeacher(@Body request: BulkReassignRequest): BulkReassignResponse

    // Settings
    @GET("settings/overdue-days")
    suspend fun getOverdueDays(): OverdueDaysDto

    @PUT("settings/overdue-days")
    suspend fun updateOverdueDays(@Body request: OverdueDaysDto): OverdueDaysDto

    @GET("settings/app-version")
    suspend fun getAppVersion(): AppVersionDto

    @PUT("settings/app-version")
    suspend fun updateAppVersion(@Body request: AppVersionDto): AppVersionDto

    @GET("settings/theme")
    suspend fun getTheme(): ThemeDto

    @PUT("settings/theme")
    suspend fun updateTheme(@Body request: ThemeDto): ThemeDto

    @GET("settings/location-share-ttl")
    suspend fun getLocationShareTtl(): LocationShareTtlDto

    @PUT("settings/location-share-ttl")
    suspend fun updateLocationShareTtl(@Body request: LocationShareTtlDto): LocationShareTtlDto

    // Visits
    @GET("deaf-individuals/{deafId}/visits")
    suspend fun getVisits(@Path("deafId") deafId: Int): List<VisitDto>

    @POST("deaf-individuals/{deafId}/visits")
    suspend fun createVisit(@Path("deafId") deafId: Int, @Body request: VisitRequest): CreateResponse

    // Remarks
    @GET("visits/{visitId}/remarks")
    suspend fun getRemarks(@Path("visitId") visitId: Int): List<RemarkDto>

    @POST("visits/{visitId}/remarks")
    suspend fun createRemark(@Path("visitId") visitId: Int, @Body request: RemarkRequest): CreateResponse

    @PUT("visits/{visitId}/remarks/{id}")
    suspend fun updateRemark(@Path("visitId") visitId: Int, @Path("id") id: Int, @Body request: RemarkUpdateRequest): Response<Unit>

    @DELETE("visits/{visitId}/remarks/{id}")
    suspend fun deleteRemark(@Path("visitId") visitId: Int, @Path("id") id: Int): Response<Unit>

    // Reports
    @GET("reports/summary")
    suspend fun reportSummary(): SummaryDto

    @GET("reports/by-municipality")
    suspend fun reportByMunicipality(): List<ByMunicipalityDto>

    @GET("reports/by-municipality-status")
    suspend fun reportByMunicipalityStatus(): List<ByMunicipalityStatusDto>

    @GET("reports/by-barangay")
    suspend fun reportByBarangay(): List<ByBarangayDto>

    @GET("reports/by-gender")
    suspend fun reportByGender(): List<ByGenderDto>

    @GET("reports/by-skill")
    suspend fun reportBySkill(): List<BySkillDto>

    @GET("reports/by-status")
    suspend fun reportByStatus(): List<ByStatusDto>

    @GET("reports/by-conductor")
    suspend fun reportByConductor(): List<ByConductorDto>

    @GET("reports/recent-visits")
    suspend fun reportRecentVisits(@Query("limit") limit: Int = 20): List<RecentVisitDto>

    @GET("reports/not-visited")
    suspend fun reportNotVisited(@Query("days") days: Int = 30): List<NotVisitedDto>

    // Admin backup
    @POST("admin/backup")
    suspend fun createBackup(): ResponseBody

    @GET("admin/backups")
    suspend fun listBackups(): List<String>

    @GET("admin/audit-logs")
    suspend fun getAuditLogs(@Query("limit") limit: Int = 100): List<AuditLogDto>

    @DELETE("admin/audit-logs")
    suspend fun deleteAllAuditLogs(): Response<Unit>

    // Super Admin only
    @POST("admin/reset-all")
    suspend fun resetAllData(): Response<Unit>
}
