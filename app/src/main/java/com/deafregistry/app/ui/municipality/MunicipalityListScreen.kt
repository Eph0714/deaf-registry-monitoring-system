package com.deafregistry.app.ui.municipality

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory

private val SKILL_OPTIONS = listOf("Skilled", "Semi-skilled", "Natural")
private val STATUS_OPTIONS = listOf("RV", "BS", "Transferred", "Unlocated")
private val GENDER_OPTIONS = listOf("Male", "Female", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MunicipalityListScreen(
    municipalityId: Int,
    municipalityName: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddNew: () -> Unit,
    initialBarangayId: Int? = null,
    barangayName: String? = null
) {
    val viewModel: MunicipalityListViewModel = viewModel(
        key = "municipality_$municipalityId",
        factory = GenericViewModelFactory {
            MunicipalityListViewModel(municipalityId, ServiceLocator.referenceDataRepository, ServiceLocator.deafIndividualRepository, initialBarangayId)
        }
    )
    val filters by viewModel.filters.collectAsState()
    val barangays by viewModel.barangays.collectAsState()
    val individuals by viewModel.individuals.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = if (!barangayName.isNullOrBlank()) "$municipalityName — $barangayName" else municipalityName,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("Search by name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            if (individuals.isEmpty()) {
                EmptyState("No registered individuals match the current filters.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(individuals, key = { it.uuid }) { individual ->
                        DeafIndividualRow(individual) { onOpenProfile(individual.uuid) }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            barangays = barangays.map { it.id to it.name },
            filters = filters,
            onBarangay = viewModel::updateBarangay,
            onSkill = viewModel::updateSkill,
            onStatus = viewModel::updateStatus,
            onGender = viewModel::updateGender,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
private fun DeafIndividualRow(individual: DeafIndividualEntity, onClick: () -> Unit) {
    val isBsRecord = individual.monitoringStatus == "BS"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isBsRecord) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                individual.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isBsRecord) FontWeight.Bold else FontWeight.Normal,
                color = Color.Black
            )
            Text(
                "${individual.barangayName} • ${individual.skillLevel} • ${individual.monitoringStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isBsRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isBsRecord) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun FilterDialog(
    barangays: List<Pair<Int, String>>,
    filters: MunicipalityFilters,
    onBarangay: (Int?) -> Unit,
    onSkill: (String?) -> Unit,
    onStatus: (String?) -> Unit,
    onGender: (String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter") },
        text = {
            Column {
                Text("Barangay", style = MaterialTheme.typography.labelLarge)
                ChipRow(
                    options = barangays.map { it.second },
                    selected = barangays.firstOrNull { it.first == filters.barangayId }?.second,
                    onSelect = { name -> onBarangay(barangays.firstOrNull { it.second == name }?.first) }
                )

                Text("Skill", style = MaterialTheme.typography.labelLarge)
                ChipRow(options = SKILL_OPTIONS, selected = filters.skillLevel, onSelect = onSkill)

                Text("Monitoring Status", style = MaterialTheme.typography.labelLarge)
                ChipRow(options = STATUS_OPTIONS, selected = filters.monitoringStatus, onSelect = onStatus)

                Text("Gender", style = MaterialTheme.typography.labelLarge)
                ChipRow(options = GENDER_OPTIONS, selected = filters.gender, onSelect = onGender)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClear) { Text("Clear all") } }
    )
}

@Composable
private fun ChipRow(options: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(option) }
            )
        }
    }
}
