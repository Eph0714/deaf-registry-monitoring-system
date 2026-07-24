package com.deafregistry.app.ui.admin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBarangaysScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.referenceDataRepository
    val municipalities by repo.observeMunicipalities().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedMunicipalityId by remember { mutableStateOf<Int?>(null) }
    var barangays by remember { mutableStateOf(listOf<com.deafregistry.app.data.local.entity.BarangayEntity>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var municipalityMenuExpanded by remember { mutableStateOf(false) }

    suspend fun reload(municipalityId: Int) {
        barangays = repo.getBarangaysForMunicipality(municipalityId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Barangays", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (selectedMunicipalityId != null) { newName = ""; showAddDialog = true }
            }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ExposedDropdownMenuBox(expanded = municipalityMenuExpanded, onExpandedChange = { municipalityMenuExpanded = it }) {
                OutlinedTextField(
                    value = municipalities.firstOrNull { it.id == selectedMunicipalityId }?.name ?: "Select municipality",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Municipality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = municipalityMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = municipalityMenuExpanded, onDismissRequest = { municipalityMenuExpanded = false }) {
                    municipalities.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = {
                            selectedMunicipalityId = m.id
                            municipalityMenuExpanded = false
                            scope.launch { reload(m.id) }
                        })
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
                items(barangays, key = { it.id }) { b ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { repo.deleteBarangay(b.id) }
                                    runCatching { repo.refreshAll() }
                                    selectedMunicipalityId?.let { reload(it) }
                                }
                            }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Barangay") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    val municipalityId = selectedMunicipalityId ?: return@TextButton
                    scope.launch {
                        runCatching { repo.createBarangay(newName.trim(), municipalityId) }
                        runCatching { repo.refreshAll() }
                        reload(municipalityId)
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}
