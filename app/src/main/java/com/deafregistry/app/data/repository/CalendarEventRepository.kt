package com.deafregistry.app.data.repository

import com.deafregistry.app.data.remote.ApiService
import com.deafregistry.app.data.remote.dto.CalendarEventDto
import com.deafregistry.app.data.remote.dto.CalendarEventRequest

class CalendarEventRepository(private val api: ApiService) {
    suspend fun list(): List<CalendarEventDto> = api.getCalendarEvents()

    suspend fun create(title: String, description: String?, eventDate: String): CalendarEventDto =
        api.createCalendarEvent(CalendarEventRequest(title, description, eventDate))

    suspend fun update(id: Int, title: String, description: String?, eventDate: String): CalendarEventDto =
        api.updateCalendarEvent(id, CalendarEventRequest(title, description, eventDate))

    suspend fun delete(id: Int) {
        val response = api.deleteCalendarEvent(id)
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }
}
