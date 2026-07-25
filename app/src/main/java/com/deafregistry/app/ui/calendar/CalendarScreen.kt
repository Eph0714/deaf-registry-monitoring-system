package com.deafregistry.app.ui.calendar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.CalendarEventDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dedicated Calendar screen, reached from the Dashboard's Quick Access tile (kept off the main
 * form itself so the Dashboard doesn't grow too tall - see the bell-badged tile in
 * DashboardScreen.kt's DashboardQuickActionsRow). Self-contained: fetches and mutates its own
 * event list directly via ServiceLocator, same as AllIndividualsScreen/MunicipalityDirectoryScreen.
 * Anyone can browse months and view events; only admin/super_admin get Add/Edit/Delete (also
 * enforced server-side - see calendarEvents.routes.js).
 */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAdmin = ServiceLocator.sessionManager.isAdmin()

    var events by remember { mutableStateOf<List<CalendarEventDto>>(emptyList()) }
    fun loadEvents() {
        scope.launch {
            runCatching { ServiceLocator.calendarEventRepository.list() }
                .onSuccess { events = it }
                .onFailure { Toast.makeText(context, "Failed to load calendar: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(Unit) { loadEvents() }

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDayDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEventDto?>(null) }

    val eventsByDate = remember(events) {
        events.mapNotNull { e -> runCatching { LocalDate.parse(e.eventDate.take(10)) }.getOrNull()?.let { it to e } }
            .groupBy({ it.first }, { it.second })
    }
    val today = LocalDate.now()
    val todaysEvents = eventsByDate[today].orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Calendar", onBack = onBack) },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(onClick = {
                    editingEvent = null
                    selectedDate = selectedDate ?: today
                    showEditor = true
                }) { Icon(Icons.Default.Add, contentDescription = "Add Event") }
            }
        }
    ) { padding: PaddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (todaysEvents.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Text(
                                "Today's Event",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                        todaysEvents.forEach { event ->
                            Text(
                                event.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val firstDay = currentMonth.atDay(1)
            val daysInMonth = currentMonth.lengthOfMonth()
            val startOffset = firstDay.dayOfWeek.value % 7 // Sunday-first grid
            val totalRows = (startOffset + daysInMonth + 6) / 7

            for (row in 0 until totalRows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = row * 7 + col - startOffset + 1
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..daysInMonth) {
                                val date = currentMonth.atDay(dayNum)
                                val isToday = date == today
                                val hasEvents = eventsByDate[date]?.isNotEmpty() == true
                                Column(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            selectedDate = date
                                            showDayDialog = true
                                        }
                                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        dayNum.toString(),
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(
                                                if (hasEvents) {
                                                    if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                                } else Color.Transparent,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
    }

    val dayForDialog = selectedDate
    if (showDayDialog && dayForDialog != null) {
        val dayEvents = eventsByDate[dayForDialog].orEmpty()
        AlertDialog(
            onDismissRequest = { showDayDialog = false },
            title = { Text(dayForDialog.toString()) },
            text = {
                Column {
                    if (dayEvents.isEmpty()) {
                        Text("No events scheduled for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    dayEvents.forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(event.title, fontWeight = FontWeight.Bold)
                                if (!event.description.isNullOrBlank()) {
                                    Text(event.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!event.createdByName.isNullOrBlank()) {
                                    Text(
                                        "Added by ${event.createdByName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isAdmin) {
                                IconButton(onClick = {
                                    editingEvent = event
                                    showDayDialog = false
                                    showEditor = true
                                }) { Icon(Icons.Default.Edit, contentDescription = "Edit event") }
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { ServiceLocator.calendarEventRepository.delete(event.id) }
                                            .onSuccess { loadEvents() }
                                            .onFailure { Toast.makeText(context, "Failed to delete event: ${it.message}", Toast.LENGTH_LONG).show() }
                                    }
                                    showDayDialog = false
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete event") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isAdmin) {
                    TextButton(onClick = {
                        editingEvent = null
                        showDayDialog = false
                        showEditor = true
                    }) { Text("Add Event") }
                }
            },
            dismissButton = { TextButton(onClick = { showDayDialog = false }) { Text("Close") } }
        )
    }

    if (showEditor) {
        val editDate = selectedDate ?: today
        var titleText by remember(editingEvent) { mutableStateOf(editingEvent?.title ?: "") }
        var descriptionText by remember(editingEvent) { mutableStateOf(editingEvent?.description ?: "") }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingEvent == null) "Add Event" else "Edit Event") },
            text = {
                Column {
                    Text(editDate.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Description (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (titleText.isNotBlank()) {
                            val existing = editingEvent
                            showEditor = false
                            scope.launch {
                                runCatching {
                                    if (existing == null) {
                                        ServiceLocator.calendarEventRepository.create(titleText.trim(), descriptionText.trim().ifBlank { null }, editDate.toString())
                                    } else {
                                        ServiceLocator.calendarEventRepository.update(existing.id, titleText.trim(), descriptionText.trim().ifBlank { null }, editDate.toString())
                                    }
                                }.onSuccess { loadEvents() }
                                    .onFailure { Toast.makeText(context, "Failed to save event: ${it.message}", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text("Cancel") } }
        )
    }
}
