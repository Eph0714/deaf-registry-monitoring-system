package com.deafregistry.app.ui.search

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.local.dao.DeafIndividualWithLastVisit
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.SearchStateHolder
import com.deafregistry.app.util.ExportUtils

private data class CategoryOption(val key: String, val label: String)

private val CATEGORIES = listOf(
    CategoryOption("all", "Show All"),
    CategoryOption("municipality", "Municipality"),
    CategoryOption("barangay", "Barangay"),
    CategoryOption("name", "Name"),
    CategoryOption("conductor", "BS Conductor"),
    CategoryOption("status", "Monitoring Status"),
    CategoryOption("skill", "Skill Level"),
    CategoryOption("lastVisit", "Last Date of Visit")
)

// Matches the exact casing stored in monitoring_status/skill_level (see DeafEditorScreen's
// STATUS_OPTIONS/SKILL_OPTIONS) - the local Room queries behind these sub-filters are a
// case-sensitive exact match.
private val MONITORING_STATUS_CHOICES = listOf("BS", "RV", "Transferred", "Unlocated")
private val SKILL_LEVEL_CHOICES = listOf("Skilled", "Semi-skilled", "Natural")

/**
 * The single landing screen for "Deaf Records": opens showing everyone, name ascending, with a
 * "Search by" category picker that re-groups the same list - Municipality/Barangay/BS
 * Conductor/Deaf Status group into ascending sections with names ascending inside each; Last
 * Date of Visit groups by day descending with names ascending inside each; Name/Show All stay a
 * flat ascending list. Also exportable as CSV/PDF, prompting for a filename/heading first.
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
    var category by remember {
        mutableStateOf(
            CATEGORIES.firstOrNull { it.key == SearchStateHolder.individualsCategoryKey }
                ?: CATEGORIES.firstOrNull { it.key == initialCategory }
                ?: CATEGORIES.first()
        )
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var statusChoiceMenuExpanded by remember { mutableStateOf(false) }
    var statusChoice by remember { mutableStateOf(SearchStateHolder.individualsStatusChoice) }
    var searchText by remember { mutableStateOf(SearchStateHolder.individualsSearchText) }
    var skillChoiceMenuExpanded by remember { mutableStateOf(false) }
    var skillChoice by remember { mutableStateOf(SearchStateHolder.individualsSkillChoice) }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }
    var exportHeading by remember { mutableStateOf(title) }
    val context = LocalContext.current

    val exportRows = currentExportRows(
        category.key,
        statusChoice.takeIf { category.key == "status" },
        skillChoice.takeIf { category.key == "skill" },
        searchText
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, onBack = onBack) },
        floatingActionButton = {
            // Conductors can add and edit deaf profiles too, just not delete them (see DeafProfileScreen).
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
                            onClick = {
                                category = option
                                categoryMenuExpanded = false
                                statusChoice = null
                                searchText = ""
                                skillChoice = null
                                SearchStateHolder.individualsCategoryKey = option.key
                                SearchStateHolder.individualsStatusChoice = null
                                SearchStateHolder.individualsSearchText = ""
                                SearchStateHolder.individualsSkillChoice = null
                            }
                        )
                    }
                }
            }

            if (category.key == "status") {
                ExposedDropdownMenuBox(
                    expanded = statusChoiceMenuExpanded,
                    onExpandedChange = { statusChoiceMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = statusChoice ?: "All statuses",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deaf Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusChoiceMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = statusChoiceMenuExpanded, onDismissRequest = { statusChoiceMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("All statuses") }, onClick = {
                            statusChoiceMenuExpanded = false
                            statusChoice = null
                            SearchStateHolder.individualsStatusChoice = null
                        })
                        MONITORING_STATUS_CHOICES.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice) },
                                onClick = {
                                    statusChoiceMenuExpanded = false
                                    statusChoice = choice
                                    SearchStateHolder.individualsStatusChoice = choice
                                }
                            )
                        }
                    }
                }
            }

            if (category.key == "skill") {
                ExposedDropdownMenuBox(
                    expanded = skillChoiceMenuExpanded,
                    onExpandedChange = { skillChoiceMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = skillChoice ?: "All skill levels",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Skill Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = skillChoiceMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = skillChoiceMenuExpanded, onDismissRequest = { skillChoiceMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("All skill levels") }, onClick = {
                            skillChoiceMenuExpanded = false
                            skillChoice = null
                            SearchStateHolder.individualsSkillChoice = null
                        })
                        SKILL_LEVEL_CHOICES.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice) },
                                onClick = {
                                    skillChoiceMenuExpanded = false
                                    skillChoice = choice
                                    SearchStateHolder.individualsSkillChoice = choice
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    SearchStateHolder.individualsSearchText = it
                },
                label = { Text("Search All") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { exportHeading = title; pendingExportFormat = "csv" }, modifier = Modifier.weight(1f)) {
                    Text("Export CSV")
                }
                OutlinedButton(onClick = { exportHeading = title; pendingExportFormat = "pdf" }, modifier = Modifier.weight(1f)) {
                    Text("Export PDF")
                }
            }
            Spacer(Modifier.padding(top = 4.dp))

            if (category.key == "lastVisit") {
                LastVisitList(searchText, onOpenProfile)
            } else {
                GroupedIndividualsList(
                    category.key,
                    statusChoice.takeIf { category.key == "status" },
                    skillChoice.takeIf { category.key == "skill" },
                    searchText,
                    onOpenProfile
                )
            }
        }
    }

    val exportFormat = pendingExportFormat
    if (exportFormat != null) {
        AlertDialog(
            onDismissRequest = { pendingExportFormat = null },
            title = { Text("File Name") },
            text = {
                OutlinedTextField(
                    value = exportHeading,
                    onValueChange = { exportHeading = it },
                    label = { Text("File name / heading") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportFormat = null
                    val heading = exportHeading.ifBlank { title }
                    val safeName = heading.replace(Regex("[^A-Za-z0-9-_ ]"), "").ifBlank { "deaf_records" }
                    val header = listOf("Name", "Barangay", "Municipality", "Status")
                    val path = if (exportFormat == "csv") {
                        ExportUtils.exportCsv(context, "$safeName.csv", header, exportRows, title = heading)
                    } else {
                        ExportUtils.exportPdf(context, "$safeName.pdf", heading, header, exportRows)
                    }
                    Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { pendingExportFormat = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun currentExportRows(categoryKey: String, statusFilter: String?, skillFilter: String?, searchText: String): List<List<String>> {
    return if (categoryKey == "lastVisit") {
        val flow = remember { ServiceLocator.deafIndividualRepository.observeAllActiveWithLastVisit() }
        val rows by flow.collectAsState(initial = emptyList<DeafIndividualWithLastVisit>())
        val filtered = if (searchText.isNotBlank()) {
            rows.filter { matchesSearch(it.individual, searchText) }
        } else rows
        filtered.map { toExportRow(it.individual) }
    } else {
        val flow = remember(categoryKey, statusFilter, skillFilter) { flowForCategory(categoryKey, statusFilter, skillFilter) }
        val individuals by flow.collectAsState(initial = emptyList())
        val filtered = if (searchText.isNotBlank()) {
            individuals.filter { matchesSearch(it, searchText) }
        } else individuals
        filtered.map { toExportRow(it) }
    }
}

private fun toExportRow(individual: DeafIndividualEntity): List<String> = listOf(
    individual.fullName, individual.barangayName, individual.municipalityName, individual.monitoringStatus
)

/** "Search All" - matches against every field shown in a row/export, not just the name. */
private fun matchesSearch(individual: DeafIndividualEntity, query: String): Boolean =
    individual.fullName.contains(query, ignoreCase = true) ||
        individual.barangayName.contains(query, ignoreCase = true) ||
        individual.municipalityName.contains(query, ignoreCase = true) ||
        individual.monitoringStatus.contains(query, ignoreCase = true) ||
        individual.skillLevel.contains(query, ignoreCase = true) ||
        (individual.assignedTeacherName?.contains(query, ignoreCase = true) == true)

private fun flowForCategory(categoryKey: String, statusFilter: String? = null, skillFilter: String? = null) = when {
    categoryKey == "status" && statusFilter != null -> ServiceLocator.deafIndividualRepository.observeByCategory(monitoringStatus = statusFilter)
    categoryKey == "skill" && skillFilter != null -> ServiceLocator.deafIndividualRepository.observeByCategory(skillLevel = skillFilter)
    categoryKey == "municipality" -> ServiceLocator.deafIndividualRepository.observeAllActiveByMunicipality()
    categoryKey == "barangay" -> ServiceLocator.deafIndividualRepository.observeAllActiveByBarangay()
    categoryKey == "conductor" -> ServiceLocator.deafIndividualRepository.observeAllActiveByConductor()
    categoryKey == "status" -> ServiceLocator.deafIndividualRepository.observeAllActiveByStatus()
    categoryKey == "skill" -> ServiceLocator.deafIndividualRepository.observeAllActiveBySkillLevel()
    else -> ServiceLocator.deafIndividualRepository.observeAllActive()
}

@Composable
private fun GroupedIndividualsList(
    categoryKey: String,
    statusFilter: String?,
    skillFilter: String?,
    searchText: String,
    onOpenProfile: (String) -> Unit
) {
    val flow = remember(categoryKey, statusFilter, skillFilter) { flowForCategory(categoryKey, statusFilter, skillFilter) }
    val rawIndividuals by flow.collectAsState(initial = emptyList())
    val individuals = if (searchText.isNotBlank()) {
        rawIndividuals.filter { matchesSearch(it, searchText) }
    } else rawIndividuals
    val lastVisitByUuid = rememberLastVisitByUuid()

    if (individuals.isEmpty()) {
        EmptyState("No registered individuals yet.")
        return
    }

    // A specific status/skill choice already fully filters the list - no need to re-group too.
    val groupKeyOf: ((DeafIndividualEntity) -> String)? = when {
        categoryKey == "status" && statusFilter != null -> null
        categoryKey == "skill" && skillFilter != null -> null
        categoryKey == "municipality" -> { it -> it.municipalityName }
        categoryKey == "barangay" -> { it -> "${it.municipalityName} / ${it.barangayName}" }
        categoryKey == "conductor" -> { it -> it.assignedTeacherName ?: "Unassigned" }
        categoryKey == "status" -> { it -> it.monitoringStatus }
        categoryKey == "skill" -> { it -> it.skillLevel }
        else -> null
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (groupKeyOf == null) {
            items(individuals, key = { it.uuid }) { individual ->
                IndividualRow(individual, lastVisitByUuid[individual.uuid]) { onOpenProfile(individual.uuid) }
            }
        } else {
            individuals.groupBy(groupKeyOf).forEach { (header, members) ->
                item(key = "header_$header") { GroupHeader(header, members.size) }
                items(members, key = { it.uuid }) { individual ->
                    IndividualRow(individual, lastVisitByUuid[individual.uuid]) { onOpenProfile(individual.uuid) }
                }
            }
        }
    }
}

/** Looked up by uuid and merged into every category's rows, not just the "Last Date of Visit" one. */
@Composable
private fun rememberLastVisitByUuid(): Map<String, String?> {
    val flow = remember { ServiceLocator.deafIndividualRepository.observeAllActiveWithLastVisit() }
    val rows by flow.collectAsState(initial = emptyList<DeafIndividualWithLastVisit>())
    return remember(rows) { rows.associate { it.individual.uuid to it.lastVisitDate } }
}

@Composable
private fun LastVisitList(searchText: String, onOpenProfile: (String) -> Unit) {
    val flow = remember { ServiceLocator.deafIndividualRepository.observeAllActiveWithLastVisit() }
    val rawRows by flow.collectAsState(initial = emptyList<DeafIndividualWithLastVisit>())
    val rows = if (searchText.isNotBlank()) {
        rawRows.filter { matchesSearch(it.individual, searchText) }
    } else rawRows

    if (rows.isEmpty()) {
        EmptyState("No registered individuals yet.")
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        rows.groupBy { it.lastVisitDate?.take(10) ?: "Never Visited" }.forEach { (header, members) ->
            item(key = "header_$header") { GroupHeader(header, members.size) }
            items(members, key = { it.individual.uuid }) { row ->
                IndividualRow(row.individual, row.lastVisitDate) { onOpenProfile(row.individual.uuid) }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String, count: Int) {
    Text(
        "$text ($count)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun IndividualRow(individual: DeafIndividualEntity, lastVisitDate: String? = null, onClick: () -> Unit) {
    // "Elephant" isn't a font Android ships or that we have a licensed file for, so BS records use
    // the heaviest available weight (Black) as the closest stand-in, plus the app's blue accent.
    val isBs = individual.monitoringStatus == "BS"
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    individual.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isBs) FontWeight.Black else FontWeight.Normal,
                    color = if (isBs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${individual.barangayName}, ${individual.municipalityName} • ${individual.monitoringStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isBs) FontWeight.Black else FontWeight.Normal,
                    color = if (isBs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                daysSinceVisitLabel(lastVisitDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun daysSinceVisitLabel(lastVisitDate: String?): String {
    if (lastVisitDate.isNullOrBlank()) return "Never visited"
    val days = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(lastVisitDate.take(10)), java.time.LocalDate.now())
    }.getOrNull() ?: return "—"
    return when {
        days <= 0 -> "Today"
        days == 1L -> "1 day ago"
        else -> "$days days ago"
    }
}
