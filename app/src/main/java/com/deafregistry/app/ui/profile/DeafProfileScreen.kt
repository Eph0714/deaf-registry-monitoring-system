package com.deafregistry.app.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.BuildConfig
import com.deafregistry.app.data.local.entity.VisitEntity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.util.LocationHelper
import com.deafregistry.app.util.MapsUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeafProfileScreen(
    uuid: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val viewModel: DeafProfileViewModel = viewModel(
        key = "profile_$uuid",
        factory = GenericViewModelFactory {
            DeafProfileViewModel(
                uuid,
                ServiceLocator.deafIndividualRepository,
                ServiceLocator.visitRepository,
                ServiceLocator.remarkRepository,
                ServiceLocator.sessionManager,
                ServiceLocator.referenceDataRepository
            )
        }
    )
    val state by viewModel.uiState.collectAsState()
    val assignmentHistory by viewModel.assignmentHistory.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddVisitDialog by remember { mutableStateOf(false) }
    var pendingVisit by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            val point = if (granted) LocationHelper.getCurrentLocation(context) else null
            pendingVisit?.let { (dateIso, publisher, remarks) ->
                viewModel.addManualVisit(point?.latitude, point?.longitude, dateIso, publisher, remarks)
            }
            pendingVisit = null
        }
    }

    fun saveManualVisit(dateIso: String, publisher: String, remarks: String) {
        pendingVisit = Triple(dateIso, publisher, remarks)
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            scope.launch {
                val point = LocationHelper.getCurrentLocation(context)
                viewModel.addManualVisit(point?.latitude, point?.longitude, dateIso, publisher, remarks)
                pendingVisit = null
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val individual = state.individual

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = individual?.fullName ?: "Profile",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { onEdit(uuid) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            )
        }
    ) { padding: PaddingValues ->
        if (individual == null) {
            EmptyState("Loading...")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                item {
                    ProfileHeader(individual.fullName, calculateAge(individual.birthDate), individual.gender, individual.photoUrl ?: individual.localPhotoPath)
                    Spacer(Modifier.height(16.dp))

                    SectionCard("Address") {
                        InfoRow("Barangay", individual.barangayName)
                        InfoRow("Municipality", individual.municipalityName)
                    }

                    if (!individual.contactNumber.isNullOrBlank() || !individual.email.isNullOrBlank() || !individual.maritalStatus.isNullOrBlank()) {
                        SectionCard("Contact Information") {
                            InfoRow("Contact Number", individual.contactNumber ?: "—")
                            InfoRow("Email", individual.email ?: "—")
                            InfoRow("Marital Status", individual.maritalStatus ?: "—")
                        }
                    }

                    if (!individual.emergencyContactName.isNullOrBlank() || !individual.emergencyContactNumber.isNullOrBlank()) {
                        SectionCard("Emergency Contact") {
                            InfoRow("Person", individual.emergencyContactName ?: "—")
                            InfoRow("Contact Number", individual.emergencyContactNumber ?: "—")
                        }
                    }

                    SectionCard("Location") {
                        if (individual.latitude != null && individual.longitude != null) {
                            InfoRow("Latitude", individual.latitude.toString())
                            InfoRow("Longitude", individual.longitude.toString())
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                MapsUtil.openInMaps(context, individual.latitude, individual.longitude, individual.fullName)
                            }) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open in Google Maps")
                            }
                        } else {
                            Text("No GPS location recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    SectionCard("Monitoring") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text("Skill: ${individual.skillLevel}") })
                            AssistChip(onClick = {}, label = { Text("Status: ${individual.monitoringStatus}") })
                        }
                    }

                    SectionCard("Assigned BS Conductor") {
                        if (individual.assignedTeacherName != null) {
                            InfoRow("Name", individual.assignedTeacherName)
                            InfoRow("Contact", individual.assignedTeacherContact ?: "—")
                        } else {
                            Text("No teacher assigned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (assignmentHistory.isNotEmpty()) {
                        SectionCard("Assignment History") {
                            assignmentHistory.forEach { entry ->
                                Text(
                                    "${entry.oldTeacherName ?: "Unassigned"} → ${entry.newTeacherName ?: "Unassigned"} " +
                                        "on ${entry.changedAt} by ${entry.changedByName ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    if (!individual.notes.isNullOrBlank()) {
                        SectionCard("Remarks") { Text(individual.notes) }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddVisitDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add Visit")
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Visit History", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }

                if (state.visits.isEmpty()) {
                    item { Text("No visits recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(state.visits, key = { it.uuid }) { visit ->
                        VisitCard(visit, viewModel)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete record?") },
            text = { Text("This will remove ${individual?.fullName ?: "this individual"} from the registry. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteIndividual(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showAddVisitDialog) {
        AddVisitDialog(
            initialPublisher = ServiceLocator.sessionManager.session.value?.name ?: "",
            onDismiss = { showAddVisitDialog = false },
            onConfirm = { dateIso, publisher, remarks ->
                showAddVisitDialog = false
                saveManualVisit(dateIso, publisher, remarks)
            }
        )
    }
}

@Composable
private fun AddVisitDialog(
    initialPublisher: String,
    onDismiss: () -> Unit,
    onConfirm: (dateIso: String, publisher: String, remarks: String) -> Unit
) {
    val context = LocalContext.current
    var publisher by remember { mutableStateOf(initialPublisher) }
    var remarks by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now()) }
    var selectedTime by remember { mutableStateOf(java.time.LocalTime.now().withSecond(0).withNano(0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Visit") },
        text = {
            Column {
                OutlinedTextField(
                    value = selectedDate.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = {
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, day -> selectedDate = java.time.LocalDate.of(year, month + 1, day) },
                                selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
                            ).show()
                        }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = selectedTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Time") },
                    trailingIcon = {
                        IconButton(onClick = {
                            android.app.TimePickerDialog(
                                context,
                                { _, hour, minute -> selectedTime = java.time.LocalTime.of(hour, minute) },
                                selectedTime.hour, selectedTime.minute, false
                            ).show()
                        }) {
                            Icon(Icons.Default.Schedule, contentDescription = "Pick time")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text("Who visited (Publisher)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dateIso = java.time.LocalDateTime.of(selectedDate, selectedTime)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                onConfirm(dateIso, publisher.trim(), remarks.trim())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun calculateAge(birthDate: String?): Int? {
    if (birthDate.isNullOrBlank()) return null
    return runCatching {
        java.time.Period.between(java.time.LocalDate.parse(birthDate), java.time.LocalDate.now()).years
    }.getOrNull()
}

@Composable
private fun ProfileHeader(name: String, age: Int?, gender: String, photo: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        val photoUrl = photo?.let { if (it.startsWith("/uploads")) BuildConfig.API_BASE_URL.removeSuffix("/api/") + it else it }
        AsyncImage(
            model = photoUrl,
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(160.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(12.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text("${age ?: "—"} years old • $gender", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun VisitCard(visit: VisitEntity, viewModel: DeafProfileViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var remarkText by remember { mutableStateOf("") }
    var editingUuid by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var deletingUuid by remember { mutableStateOf<String?>(null) }
    val remarks by viewModel.remarksFor(visit.uuid).collectAsState(initial = emptyList())

    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(visit.visitDateTime, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Details") }
            }
            Text("Conductor: ${visit.conductorName ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (visit.latitude != null && visit.longitude != null) {
                    Text("Location: ${visit.latitude}, ${visit.longitude}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text("Remarks", style = MaterialTheme.typography.labelLarge)
                remarks.forEach { remark ->
                    if (editingUuid == remark.uuid) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedTextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                viewModel.editRemark(remark.uuid, editText)
                                editingUuid = null
                            }) { Text("Save") }
                            TextButton(onClick = { editingUuid = null }) { Text("Cancel") }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "• ${remark.remarkText} — ${remark.userName ?: ""} (${remark.createdAt})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (viewModel.canModifyRemark(remark)) {
                                IconButton(onClick = { editingUuid = remark.uuid; editText = remark.remarkText }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit remark", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { deletingUuid = remark.uuid }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete remark", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = remarkText,
                        onValueChange = { remarkText = it },
                        label = { Text("Add remark") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        viewModel.addRemark(visit.uuid, remarkText)
                        remarkText = ""
                    }) { Text("Add") }
                }
            }
        }
    }

    deletingUuid?.let { uuid ->
        AlertDialog(
            onDismissRequest = { deletingUuid = null },
            title = { Text("Delete remark?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRemark(uuid)
                    deletingUuid = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingUuid = null }) { Text("Cancel") } }
        )
    }
}
