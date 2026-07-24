package com.deafregistry.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.local.dao.DeafIndividualWithLastVisit
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState

private data class CategoryOption(val key: String, val label: String)

private val CATEGORIES = listOf(
    CategoryOption("all", "Show All"),
    CategoryOption("municipality", "Municipality"),
    CategoryOption("barangay", "Barangay"),
    CategoryOption("name", "Name"),
    CategoryOption("conductor", "BS Conductor"),
    CategoryOption("lastVisit", "Last Date of Visit")
)

/**
 * The single landing screen for "Deaf Records": opens showing everyone, name ascending, with a
 * "Search by" category picker that re-groups the same list - Municipality/Barangay/BS Conductor
 * group into ascending sections with names ascending inside each; Last Date of Visit groups by
 * day descending with names ascending inside each; Name/Show All stay a flat ascending list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllIndividualsScreen(
    title: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddNew: () -> Unit,
    initialCategory: String = "all"
) {
    var category by remember { mutableStateOf(CATEGORIES.firstOrNull { it.key == initialCategory } ?: CATEGORIES.first()) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = category.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Search by") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    CATEGORIES.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { category = option; categoryMenuExpanded = false }
                        )
                    }
                }
            }

            if (category.key == "lastVisit") {
                LastVisitList(onOpenProfile)
            } else {
                GroupedIndividualsList(category.key, onOpenProfile)
            }
        }
    }
}

@Composable
private fun GroupedIndividualsList(categoryKey: String, onOpenProfile: (String) -> Unit) {
    val flow = remember(categoryKey) {
        when (categoryKey) {
            "municipality" -> ServiceLocator.deafIndividualRepository.observeAllActiveByMunicipality()
            "barangay" -> ServiceLocator.deafIndividualRepository.observeAllActiveByBarangay()
            "conductor" -> ServiceLocator.deafIndividualRepository.observeAllActiveByConductor()
            else -> ServiceLocator.deafIndividualRepository.observeAllActive()
        }
    }
    val individuals by flow.collectAsState(initial = emptyList())

    if (individuals.isEmpty()) {
        EmptyState("No registered individuals yet.")
        return
    }

    val groupKeyOf: ((DeafIndividualEntity) -> String)? = when (categoryKey) {
        "municipality" -> { it -> it.municipalityName }
        "barangay" -> { it -> "${it.municipalityName} / ${it.barangayName}" }
        "conductor" -> { it -> it.assignedTeacherName ?: "Unassigned" }
        else -> null
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (groupKeyOf == null) {
            items(individuals, key = { it.uuid }) { individual ->
                IndividualRow(individual) { onOpenProfile(individual.uuid) }
            }
        } else {
            individuals.groupBy(groupKeyOf).forEach { (header, members) ->
                item(key = "header_$header") { GroupHeader(header) }
                items(members, key = { it.uuid }) { individual ->
                    IndividualRow(individual) { onOpenProfile(individual.uuid) }
                }
            }
        }
    }
}

@Composable
private fun LastVisitList(onOpenProfile: (String) -> Unit) {
    val flow = remember { ServiceLocator.deafIndividualRepository.observeAllActiveWithLastVisit() }
    val rows by flow.collectAsState(initial = emptyList<DeafIndividualWithLastVisit>())

    if (rows.isEmpty()) {
        EmptyState("No registered individuals yet.")
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        rows.groupBy { it.lastVisitDate?.take(10) ?: "Never Visited" }.forEach { (header, members) ->
            item(key = "header_$header") { GroupHeader(header) }
            items(members, key = { it.individual.uuid }) { row ->
                IndividualRow(row.individual) { onOpenProfile(row.individual.uuid) }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun IndividualRow(individual: DeafIndividualEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(individual.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${individual.barangayName}, ${individual.municipalityName} • ${individual.monitoringStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
