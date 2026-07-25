package com.deafregistry.app.ui.municipality

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.local.dao.DeafIndividualWithLastVisit
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.util.ExportUtils
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

private enum class GroupMode(val label: String) { MUNICIPALITY("Municipality"), BARANGAY("Barangay") }

private enum class SortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    AGE("Age"),
    DAYS_SINCE_VISIT("Days Since Last Visit"),
    LAST_VISIT_DATE("Last Visit Date"),
    MUNICIPALITY("Municipality"),
    BARANGAY("Barangay")
}

/**
 * Municipality module: directory, monitoring, and reporting for every municipality and barangay,
 * including ones with zero registered individuals. Reachable from the Dashboard's "Municipalities"
 * entry point. All data comes from local Room flows already used elsewhere (no new queries) - see
 * ReferenceDataRepository.observeMunicipalitiesWithCounts()/observeAllBarangaysWithCounts() and
 * DeafIndividualRepository.observeAllActiveWithLastVisit().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MunicipalityDirectoryScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    onAddNew: () -> Unit
) {
    val context = LocalContext.current

    var searchText by remember { mutableStateOf("") }
    var groupMode by remember { mutableStateOf(GroupMode.MUNICIPALITY) }
    var groupModeMenuExpanded by remember { mutableStateOf(false) }
    var municipalityFilter by remember { mutableStateOf<String?>(null) }
    var municipalityMenuExpanded by remember { mutableStateOf(false) }
    var barangayFilter by remember { mutableStateOf<String?>(null) }
    var barangayMenuExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    val municipalities by ServiceLocator.referenceDataRepository.observeMunicipalitiesWithCounts()
        .collectAsState(initial = emptyList())
    val allBarangays by ServiceLocator.referenceDataRepository.observeAllBarangaysWithCounts()
        .collectAsState(initial = emptyList())
    val individualsWithVisit by ServiceLocator.deafIndividualRepository.observeAllActiveWithLastVisit()
        .collectAsState(initial = emptyList<DeafIndividualWithLastVisit>())

    // Summary cards reflect the whole registry, not the current search/filter - a stable overview.
    val totalRegistered = individualsWithVisit.size
    val activeCount = individualsWithVisit.count { it.individual.monitoringStatus in ACTIVE_STATUSES }
    val inactiveCount = totalRegistered - activeCount

    val query = searchText.trim()
    val filtered = remember(individualsWithVisit, query, municipalityFilter, barangayFilter) {
        individualsWithVisit.filter { row ->
            val ind = row.individual
            (municipalityFilter == null || ind.municipalityName == municipalityFilter) &&
                (barangayFilter == null || ind.barangayName == barangayFilter) &&
                (query.isEmpty() ||
                    ind.fullName.contains(query, ignoreCase = true) ||
                    ind.municipalityName.contains(query, ignoreCase = true) ||
                    ind.barangayName.contains(query, ignoreCase = true) ||
                    ind.gender.contains(query, ignoreCase = true) ||
                    (ind.contactNumber?.contains(query, ignoreCase = true) == true))
        }
    }

    fun sorted(list: List<DeafIndividualWithLastVisit>): List<DeafIndividualWithLastVisit> = when (sortOption) {
        SortOption.NAME_ASC -> list.sortedBy { it.individual.fullName.lowercase() }
        SortOption.NAME_DESC -> list.sortedByDescending { it.individual.fullName.lowercase() }
        SortOption.AGE -> list.sortedWith(compareByDescending { calculateAge(it.individual.birthDate) ?: -1 })
        SortOption.DAYS_SINCE_VISIT -> list.sortedWith(compareByDescending { daysSinceVisit(it.lastVisitDate) ?: Long.MAX_VALUE })
        SortOption.LAST_VISIT_DATE -> list.sortedBy { it.lastVisitDate ?: "" }
        SortOption.MUNICIPALITY -> list.sortedBy { it.individual.municipalityName }
        SortOption.BARANGAY -> list.sortedBy { it.individual.barangayName }
    }

    // Municipality-grouped: every municipality (even zero-count), narrowed by the filter dropdown.
    val municipalityGroups = remember(municipalities, filtered, municipalityFilter, sortOption) {
        val byName = filtered.groupBy { it.individual.municipalityName }
        municipalities
            .filter { municipalityFilter == null || it.name == municipalityFilter }
            .map { m -> m.name to sorted(byName[m.name].orEmpty()) }
    }

    // Barangay-grouped: every barangay (even zero-count), narrowed by both filter dropdowns.
    // Keyed by "Municipality / Barangay" since barangay names aren't unique across municipalities.
    val barangayGroups = remember(allBarangays, filtered, municipalityFilter, barangayFilter, sortOption) {
        val byId = filtered.groupBy { it.individual.barangayId }
        allBarangays
            .filter { (municipalityFilter == null || it.municipalityName == municipalityFilter) && (barangayFilter == null || it.name == barangayFilter) }
            .map { b -> "${b.municipalityName} / ${b.name}" to sorted(byId[b.id].orEmpty()) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Municipalities", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SummaryChip("Municipalities", municipalities.size.toString()) }
                    item { SummaryChip("Barangays", allBarangays.size.toString()) }
                    item { SummaryChip("Registered Deaf", totalRegistered.toString()) }
                    item { SummaryChip("Active", activeCount.toString()) }
                    item { SummaryChip("Inactive", inactiveCount.toString()) }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Search by Name, Municipality, Barangay...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = groupModeMenuExpanded,
                        onExpandedChange = { groupModeMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = groupMode.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Group by") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupModeMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = groupModeMenuExpanded, onDismissRequest = { groupModeMenuExpanded = false }) {
                            GroupMode.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                                    groupMode = mode
                                    groupModeMenuExpanded = false
                                })
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = sortMenuExpanded,
                        onExpandedChange = { sortMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = sortOption.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sort by") },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                    sortOption = option
                                    sortMenuExpanded = false
                                })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = municipalityMenuExpanded,
                        onExpandedChange = { municipalityMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = municipalityFilter ?: "All Municipalities",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Municipality") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = municipalityMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = municipalityMenuExpanded, onDismissRequest = { municipalityMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All Municipalities") }, onClick = {
                                municipalityFilter = null
                                municipalityMenuExpanded = false
                            })
                            municipalities.forEach { m ->
                                DropdownMenuItem(text = { Text(m.name) }, onClick = {
                                    municipalityFilter = m.name
                                    municipalityMenuExpanded = false
                                })
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = barangayMenuExpanded,
                        onExpandedChange = { barangayMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = barangayFilter ?: "All Barangays",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Barangay") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = barangayMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = barangayMenuExpanded, onDismissRequest = { barangayMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All Barangays") }, onClick = {
                                barangayFilter = null
                                barangayMenuExpanded = false
                            })
                            allBarangays
                                .filter { municipalityFilter == null || it.municipalityName == municipalityFilter }
                                .forEach { b ->
                                    DropdownMenuItem(text = { Text("${b.name} (${b.municipalityName})") }, onClick = {
                                        barangayFilter = b.name
                                        barangayMenuExpanded = false
                                    })
                                }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val groups = if (groupMode == GroupMode.MUNICIPALITY) municipalityGroups else barangayGroups
            groups.forEach { (header, members) ->
                item(key = "muni_dir_header_$header") {
                    ExpandableGroupHeader(
                        title = header,
                        count = members.size,
                        expanded = header in expandedGroups,
                        onToggle = {
                            expandedGroups = if (header in expandedGroups) expandedGroups - header else expandedGroups + header
                        }
                    )
                }
                if (header in expandedGroups) {
                    if (members.isEmpty()) {
                        item(key = "muni_dir_empty_$header") {
                            Text(
                                "No registered individuals here yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                    } else {
                        items(members, key = { "muni_dir_row_${it.individual.uuid}" }) { row ->
                            IndividualDetailRow(
                                row = row,
                                showMunicipality = groupMode == GroupMode.BARANGAY,
                                onView = { onOpenProfile(row.individual.uuid) },
                                onEdit = { onEditProfile(row.individual.uuid) },
                                onPrint = {
                                    runCatching { printIndividual(context, row) }
                                        .onFailure { Toast.makeText(context, "Print failed: ${it.message}", Toast.LENGTH_LONG).show() }
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

private val ACTIVE_STATUSES = setOf("BS", "RV")

@Composable
private fun SummaryChip(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpandableGroupHeader(title: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$title ($count)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Expand")
        }
    }
}

@Composable
private fun IndividualDetailRow(
    row: DeafIndividualWithLastVisit,
    showMunicipality: Boolean,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit
) {
    val individual = row.individual
    val age = calculateAge(individual.birthDate)
    val days = daysSinceVisit(row.lastVisitDate)
    val dotColor = followUpColor(days)
    val isActive = individual.monitoringStatus in ACTIVE_STATUSES

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(individual.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${age?.let { "$it yrs" } ?: "Age —"} • ${individual.gender}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (showMunicipality) individual.municipalityName else individual.barangayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Last visit: ${row.lastVisitDate?.take(10) ?: "Never visited"}" + (days?.let { " ($it day${if (it == 1L) "" else "s"} ago)" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(individual.contactNumber ?: "No contact number", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (isActive) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onView) { Icon(Icons.Default.Visibility, contentDescription = "View") }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = onPrint) { Icon(Icons.Default.Print, contentDescription = "Print") }
                }
            }
        }
    }
}

private fun calculateAge(birthDate: String?): Int? {
    if (birthDate.isNullOrBlank()) return null
    return runCatching { Period.between(LocalDate.parse(birthDate.take(10)), LocalDate.now()).years }.getOrNull()
}

private fun daysSinceVisit(lastVisitDate: String?): Long? {
    if (lastVisitDate.isNullOrBlank()) return null
    return runCatching { ChronoUnit.DAYS.between(LocalDate.parse(lastVisitDate.take(10)), LocalDate.now()) }.getOrNull()
}

/** Green: 0-30 days, Yellow: 31-90 days, Red: over 90 days or never visited (treated as most urgent). */
private fun followUpColor(days: Long?): Color = when {
    days == null -> Color(0xFFE53935)
    days <= 30 -> Color(0xFF43A047)
    days <= 90 -> Color(0xFFFDD835)
    else -> Color(0xFFE53935)
}

private fun printIndividual(context: android.content.Context, row: DeafIndividualWithLastVisit) {
    val individual = row.individual
    val age = calculateAge(individual.birthDate)
    val days = daysSinceVisit(row.lastVisitDate)
    val rows = listOf(
        listOf("Full Name", individual.fullName),
        listOf("Age", age?.toString() ?: "—"),
        listOf("Gender", individual.gender),
        listOf("Municipality", individual.municipalityName),
        listOf("Barangay", individual.barangayName),
        listOf("Contact Number", individual.contactNumber ?: "—"),
        listOf("Monitoring Status", individual.monitoringStatus),
        listOf("Skill Level", individual.skillLevel),
        listOf("Last Visit", row.lastVisitDate?.take(10) ?: "Never visited"),
        listOf("Days Since Last Visit", days?.toString() ?: "—")
    )
    val safeName = individual.fullName.replace(Regex("[^A-Za-z0-9-_ ]"), "").ifBlank { "deaf_record" }
    ExportUtils.exportPdf(context, "$safeName.pdf", individual.fullName, listOf("Field", "Value"), rows)
}
