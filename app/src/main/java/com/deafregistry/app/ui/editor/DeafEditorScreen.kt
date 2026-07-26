package com.deafregistry.app.ui.editor

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.util.LocationHelper
import kotlinx.coroutines.launch
import java.io.File

private val GENDER_OPTIONS = listOf("Male", "Female", "Other")
private val SKILL_OPTIONS = listOf("Skilled", "Semi-skilled", "Natural")
private val STATUS_OPTIONS = listOf("RV", "BS", "Transferred", "Unlocated")
private val MARITAL_STATUS_OPTIONS = listOf("Single", "Married", "Widowed", "Separated", "Divorced")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeafEditorScreen(
    uuid: String?,
    municipalityId: Int?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: DeafEditorViewModel = viewModel(
        key = "editor_${uuid ?: "new"}",
        factory = GenericViewModelFactory {
            DeafEditorViewModel(uuid, municipalityId, ServiceLocator.deafIndividualRepository, ServiceLocator.referenceDataRepository, ServiceLocator.syncManager)
        }
    )
    val form by viewModel.form.collectAsState()
    val municipalities by viewModel.municipalities.collectAsState()
    val barangays by viewModel.barangays.collectAsState()
    val teachers by viewModel.teachers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(form.saved) {
        if (form.saved) onSaved()
    }

    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingPhotoFile?.let { viewModel.onPhotoSelected(it.absolutePath) }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "picked_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            viewModel.onPhotoSelected(file.absolutePath)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            pendingPhotoFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            takePictureLauncher.launch(uri)
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                LocationHelper.getCurrentLocation(context)?.let { viewModel.onLocationCaptured(it.latitude, it.longitude) }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = if (uuid == null) "Add Individual" else "Edit Individual", onBack = onBack)
        },
        bottomBar = {
            if (!form.isLoading) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        form.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = viewModel::save, enabled = !form.isSaving, modifier = Modifier.fillMaxWidth()) {
                            if (form.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Save")
                        }
                    }
                }
            }
        }
    ) { padding: PaddingValues ->
        if (form.isLoading) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(64.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // imePadding() must come before verticalScroll() - it needs to shrink the
                // available height first so the scroll viewport itself accounts for the
                // keyboard. Applied after verticalScroll() (the previous order), it only adds
                // padding at the tail of the scrollable content instead, so a field near the
                // bottom - like Remarks/Notes - doesn't reliably get scrolled into view above
                // the keyboard when focused.
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val photoModel = form.localPhotoPath ?: form.existingPhotoUrl
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = photoModel,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                )
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                        pendingPhotoFile = file
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        takePictureLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Camera")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) { Text("Gallery") }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = form.fullName,
                onValueChange = { v -> viewModel.update { it.copy(fullName = v) } },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row {
                OutlinedTextField(
                    value = form.birthDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Birthday") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val initial = runCatching { java.time.LocalDate.parse(form.birthDate) }
                                .getOrElse { java.time.LocalDate.now().minusYears(20) }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    viewModel.update { it.copy(birthDate = java.time.LocalDate.of(year, month + 1, day).toString()) }
                                },
                                initial.year, initial.monthValue - 1, initial.dayOfMonth
                            ).show()
                        }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick birthday")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                DropdownField(
                    label = "Gender",
                    options = GENDER_OPTIONS,
                    selected = form.gender,
                    onSelect = { v -> viewModel.update { it.copy(gender = v) } },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))

            DropdownField(
                label = "Municipality",
                options = municipalities.map { it.name },
                selected = municipalities.firstOrNull { it.id == form.municipalityId }?.name ?: "",
                onSelect = { name -> municipalities.firstOrNull { it.name == name }?.let { viewModel.onMunicipalitySelected(it.id) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            DropdownField(
                label = "Barangay",
                options = barangays.map { it.name },
                selected = barangays.firstOrNull { it.id == form.barangayId }?.name ?: "",
                onSelect = { name -> barangays.firstOrNull { it.name == name }?.let { b -> viewModel.update { it.copy(barangayId = b.id) } } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.purok,
                onValueChange = { v -> viewModel.update { it.copy(purok = v) } },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.contactNumber,
                onValueChange = { v -> viewModel.update { it.copy(contactNumber = v) } },
                label = { Text("Contact Number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.email,
                onValueChange = { v -> viewModel.update { it.copy(email = v) } },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            DropdownField(
                label = "Marital Status",
                options = MARITAL_STATUS_OPTIONS,
                selected = form.maritalStatus,
                onSelect = { v -> viewModel.update { it.copy(maritalStatus = v) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.emergencyContactName,
                onValueChange = { v -> viewModel.update { it.copy(emergencyContactName = v) } },
                label = { Text("Emergency Contact Person") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = form.emergencyContactNumber,
                onValueChange = { v -> viewModel.update { it.copy(emergencyContactNumber = v) } },
                label = { Text("Emergency Contact Number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Text("GPS Location", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row {
                var latText by remember(form.latitude) { mutableStateOf(form.latitude?.toString() ?: "") }
                var lonText by remember(form.longitude) { mutableStateOf(form.longitude?.toString() ?: "") }
                OutlinedTextField(
                    value = latText,
                    onValueChange = { v ->
                        latText = v
                        viewModel.update { it.copy(latitude = v.toDoubleOrNull()) }
                    },
                    label = { Text("Latitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { v ->
                        lonText = v
                        viewModel.update { it.copy(longitude = v.toDoubleOrNull()) }
                    },
                    label = { Text("Longitude") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        scope.launch {
                            LocationHelper.getCurrentLocation(context)?.let { viewModel.onLocationCaptured(it.latitude, it.longitude) }
                        }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Capture")
                }
                if (form.latitude != null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.onLocationReset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            DropdownField(
                label = "Skill Level",
                options = SKILL_OPTIONS,
                selected = form.skillLevel,
                onSelect = { v -> viewModel.update { it.copy(skillLevel = v) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            DropdownField(
                label = "Monitoring Status",
                options = STATUS_OPTIONS,
                selected = form.monitoringStatus,
                onSelect = { v -> viewModel.update { it.copy(monitoringStatus = v) } },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            DropdownField(
                label = "Assigned BS Conductor",
                options = listOf("None") + teachers.map { it.name },
                selected = teachers.firstOrNull { it.id == form.assignedTeacherId }?.name ?: "None",
                onSelect = { name ->
                    val teacher = teachers.firstOrNull { it.name == name }
                    viewModel.update { it.copy(assignedTeacherId = teacher?.id) }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))


            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Remarks / Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}
