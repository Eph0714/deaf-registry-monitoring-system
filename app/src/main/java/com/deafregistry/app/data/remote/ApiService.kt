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

    @POST("auth/logout")
    suspend fun logout(): ResponseBody

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): MessageResponse

    @Multipart
    @POST("auth/photo")
    suspend fun uploadUserPhoto(@Part photo: MultipartBody.Part): PhotoUploadResponse

    @PUT("auth/share-location")
    suspend fun shareLocation(@Body request: ShareLocationRequest): ShareLocationResponse

    @GET("users/locations")
    suspend fun getUserLocations(): List<UserLocationDto>

    // Public reference data - unauthenticated, used only by the pre-login Sign Up form's dropdowns
    @GET("municipalities/public")
    suspend fun getPublicMunicipalities(): List<PublicMunicipalityDto>

    @GET("barangays/public")
    suspend fun getPublicBarangays(@Query("municipality_id") municipalityId: Int): List<PublicBarangayDto>

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

    // Password reset requests (admin) - the Forgot Password queue
    @GET("users/password-reset-requests")
    suspend fun getPasswordResetRequests(): List<PasswordResetRequestDto>

    @POST("users/password-reset-requests/{id}/resolve")
    suspend fun resolvePasswordResetRequest(@Path("id") id: Int, @Body request: ResolvePasswordResetRequest): ResponseBody

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

    // Calendar events - GET is open to any authenticated user, writes are admin/super_admin only.
    @GET("calendar-events")
    suspend fun getCalendarEvents(): List<CalendarEventDto>

    @POST("calendar-events")
    suspend fun createCalendarEvent(@Body request: CalendarEventRequest): CalendarEventDto

    @PUT("calendar-events/{id}")
    suspend fun updateCalendarEvent(@Path("id") id: Int, @Body request: CalendarEventRequest): CalendarEventDto

    @DELETE("calendar-events/{id}")
    suspend fun deleteCalendarEvent(@Path("id") id: Int): Response<Unit>

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

    // Chat - sessions (admin management + the single "active" room)
    @GET("chat/sessions")
    suspend fun getChatSessions(): List<ChatSessionDto>

    @GET("chat/sessions/active")
    suspend fun getActiveChatSession(): Response<ChatSessionDto>

    @POST("chat/sessions")
    suspend fun createChatSession(@Body request: ChatSessionRequest): ChatSessionDto

    @PUT("chat/sessions/{id}")
    suspend fun updateChatSession(@Path("id") id: Int, @Body request: ChatSessionRequest): ChatSessionDto

    @POST("chat/sessions/{id}/open")
    suspend fun openChatSession(@Path("id") id: Int): ChatSessionDto

    @POST("chat/sessions/{id}/close")
    suspend fun closeChatSession(@Path("id") id: Int): ChatSessionDto

    @DELETE("chat/sessions/{id}")
    suspend fun deleteChatSession(@Path("id") id: Int): Response<Unit>

    @POST("chat/sessions/{id}/clear-messages")
    suspend fun clearChatMessages(@Path("id") id: Int): ResponseBody

    // Chat - recurring schedules
    @GET("chat/recurring")
    suspend fun getChatRecurringSchedules(): List<ChatRecurringScheduleDto>

    @POST("chat/recurring")
    suspend fun createChatRecurringSchedule(@Body request: ChatRecurringScheduleRequest): ChatRecurringScheduleDto

    @PUT("chat/recurring/{id}")
    suspend fun updateChatRecurringSchedule(@Path("id") id: Int, @Body request: ChatRecurringScheduleRequest): ChatRecurringScheduleDto

    @DELETE("chat/recurring/{id}")
    suspend fun deleteChatRecurringSchedule(@Path("id") id: Int): Response<Unit>

    // Chat - messages
    @GET("chat/sessions/{id}/messages")
    suspend fun getChatMessages(@Path("id") id: Int, @Query("after_id") afterId: Int? = null, @Query("limit") limit: Int = 200): List<ChatMessageDto>

    @POST("chat/sessions/{id}/messages")
    suspend fun sendChatMessage(@Path("id") id: Int, @Body request: SendChatMessageRequest): ChatMessageDto

    @DELETE("chat/sessions/{id}/messages/{messageId}")
    suspend fun deleteChatMessage(@Path("id") id: Int, @Path("messageId") messageId: Int): Response<Unit>

    @POST("chat/sessions/{id}/messages/{messageId}/pin")
    suspend fun pinChatMessage(@Path("id") id: Int, @Path("messageId") messageId: Int): Response<Unit>

    @POST("chat/sessions/{id}/messages/{messageId}/unpin")
    suspend fun unpinChatMessage(@Path("id") id: Int, @Path("messageId") messageId: Int): Response<Unit>

    @GET("chat/sessions/{id}/search")
    suspend fun searchChatMessages(@Path("id") id: Int, @Query("query") query: String? = null, @Query("date") date: String? = null): List<ChatMessageDto>

    // Chat - participants
    @GET("chat/sessions/{id}/participants")
    suspend fun getChatParticipants(@Path("id") id: Int): List<ChatParticipantDto>

    @POST("chat/sessions/{id}/join")
    suspend fun joinChatSession(@Path("id") id: Int): Response<Unit>

    @POST("chat/sessions/{id}/leave")
    suspend fun leaveChatSession(@Path("id") id: Int): Response<Unit>

    @POST("chat/sessions/{id}/participants/{userId}/mute")
    suspend fun muteChatParticipant(@Path("id") id: Int, @Path("userId") userId: Int): Response<Unit>

    @POST("chat/sessions/{id}/participants/{userId}/unmute")
    suspend fun unmuteChatParticipant(@Path("id") id: Int, @Path("userId") userId: Int): Response<Unit>

    @POST("chat/sessions/{id}/participants/{userId}/remove")
    suspend fun removeChatParticipant(@Path("id") id: Int, @Path("userId") userId: Int): Response<Unit>

    // Chat - notifications
    @GET("chat/notifications")
    suspend fun getChatNotifications(): List<ChatNotificationDto>

    @POST("chat/notifications/read")
    suspend fun markChatNotificationsRead(@Body request: MarkChatNotificationsReadRequest): Response<Unit>
}
