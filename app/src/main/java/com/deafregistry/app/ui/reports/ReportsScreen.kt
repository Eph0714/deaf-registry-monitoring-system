package com.deafregistry.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.data.local.dao.MunicipalityVisitReportRow
import com.deafregistry.app.data.remote.dto.ByMunicipalityStatusDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.FullScreenLoading
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.ui.common.SearchStateHolder
import com.deafregistry.app.util.ExportUtils
import kotlinx.coroutines.launch
import android.widget.Toast

private data class CategoryRow(val label: String, val total: Int, val value: String, val extra: String = "")
private data class ReportCategory(val key: String, val label: String, val rows: (ReportsUiState) -> List<CategoryRow>)

// Matches the exact casing stored in monitoring_status (see DeafEditorScreen's STATUS_OPTIONS) -
// the local Room query behind the drill-down does a case-sensitive exact match.
private val MONITORING_STATUS_CHOICES = listOf("BS", "RV", "Transferred", "Unlocated")

private val CATEGORIES = listOf(
    ReportCategory("municipality", "By Municipality") { s ->
        s.byMunicipality.map { CategoryRow(it.municipality, it.total, it.municipality) }
    },
    ReportCategory("barangay", "By Barangay") { s ->
        s.byBarangay.map { CategoryRow("${it.municipality} / ${it.barangay}", it.total, it.barangay, it.municipality) }
    },
    ReportCategory("gender", "By Gender") { s ->
        s.byGender.map { CategoryRow(it.gender, it.total, it.gender) }
    },
    ReportCategory("skill", "By Skill Level") { s ->
        s.bySkill.map { CategoryRow(it.skillLevel, it.total, it.skillLevel) }
    },
    ReportCategory("status", "By Monitoring Status") { s ->
        s.byStatus.map { CategoryRow(it.monitoringStatus, it.total, it.monitoringStatus) }
    },
    ReportCategory("conductor", "By BS Conductor") { s ->
        s.byConductor.map { CategoryRow(it.conductor, it.total, it.conductor) }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, onOpenCategoryDetail: (category: String, value: String, extra: String) -> Unit) {
    val viewModel: ReportsViewModel = viewModel(
        factory = GenericViewModelFactory { ReportsViewModel(ServiceLocator.reportRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember {
        mutableStateOf(CATEGORIES.firstOrNull { it.key == SearchStateHolder.reportsCategoryKey } ?: CATEGORIES.first())
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var statusChoiceMenuExpanded by remember { mutableStateOf(false) }
    var selectedStatusChoice by remember { mutableStateOf(SearchStateHolder.reportsStatusChoice) }
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }
    var reportHeading by remember { mutableStateOf("Deaf Contact Record Municipality Report") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Reports", onBack = onBack)
        }
    ) { padding: PaddingValues ->
        when {
            state.isLoading -> FullScreenLoading()
            state.error != null -> EmptyState(state.error!!)
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                item { ReportSectionHeader("Total Registered", "${state.total}") }

                item {
                    Text("Search by category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCategory.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                            CATEGORIES.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.label) },
                                    onClick = {
                                        selectedCategory = category
                                        categoryMenuExpanded = false
                                        selectedStatusChoice = null
                                        SearchStateHolder.reportsCategoryKey = category.key
                                        SearchStateHolder.reportsStatusChoice = null
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedCategory.key == "status") {
                    item {
                        ExposedDropdownMenuBox(expanded = statusChoiceMenuExpanded, onExpandedChange = { statusChoiceMenuExpanded = it }) {
                            OutlinedTextField(
                                value = selectedStatusChoice ?: "All statuses",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Monitoring Status") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusChoiceMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().padding(top = 8.dp)
                            )
                            ExposedDropdownMenu(expanded = statusChoiceMenuExpanded, onDismissRequest = { statusChoiceMenuExpanded = false }) {
                                MONITORING_STATUS_CHOICES.forEach { choice ->
                                    DropdownMenuItem(
                                        text = { Text(choice) },
                                        onClick = {
                                            statusChoiceMenuExpanded = false
                                            selectedStatusChoice = choice
                                            SearchStateHolder.reportsStatusChoice = choice
                                            onOpenCategoryDetail("status", choice, "")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                val categoryRows = selectedCategory.rows(state)
                if (categoryRows.isEmpty()) {
                    item {
                        Text(
                            "No data for ${selectedCategory.label}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(categoryRows) { row ->
                        CategoryQtyButton(row) {
                            onOpenCategoryDetail(selectedCategory.key, row.value, row.extra)
                        }
                    }
                }

                item {
                    Text("By Municipality — Status", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                }
                items(state.byMunicipalityStatus) { row -> MunicipalityStatusCard(row) }

                item {
                    Text("Recent Visits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                }
                items(state.recentVisits) { visit ->
                    Text("${visit.fullName} — ${visit.visitDateTime} (${visit.conductorName ?: "—"})", style = MaterialTheme.typography.bodySmall)
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Not Visited (30+ days)", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(state.notVisited) { entry ->
                    Text("${entry.fullName} — ${entry.municipality}/${entry.barangay} (last: ${entry.lastVisit ?: "never"})", style = MaterialTheme.typography.bodySmall)
                }

                // Conductors can view reports but not export/print them - admin/super_admin only.
                if (isAdmin) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pendingExportFormat = "csv" }) { Text("Export CSV (Excel)") }
                            OutlinedButton(onClick = { pendingExportFormat = "pdf" }) { Text("Export PDF") }
                        }
                    }
                }
            }
        }
    }

    val exportFormat = pendingExportFormat
    if (exportFormat != null) {
        AlertDialog(
            onDismissRequest = { pendingExportFormat = null },
            title = { Text("Report Heading") },
            text = {
                Column {
                    Text(
                        "This report is grouped by municipality (A-Z), listing each deaf individual, their " +
                            "status, last visit date, and who visited them last.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = reportHeading,
                        onValueChange = { reportHeading = it },
                        label = { Text("Report title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportFormat = null
                    val heading = reportHeading.ifBlank { "Deaf Contact Record Municipality Report" }
                    scope.launch {
                        val rows = ServiceLocator.deafIndividualRepository.getMunicipalityVisitReport()
                        val header = listOf("Municipality", "Name", "Status", "Last Visit Date", "Visited By")
                        val dataRows = rows.map(::toExportRow)
                        val path = if (exportFormat == "csv") {
                            ExportUtils.exportCsv(context, "deaf_registry_report.csv", header, dataRows, title = heading)
                        } else {
                            ExportUtils.exportPdf(context, "deaf_registry_report.pdf", heading, header, dataRows)
                        }
                        Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { pendingExportFormat = null }) { Text("Cancel") } }
        )
    }
}

private fun toExportRow(row: MunicipalityVisitReportRow): List<String> = listOf(
    row.municipality, row.fullName, row.status, row.lastVisitDate ?: "Never", row.lastVisitedBy ?: "—"
)

@Composable
private fun ReportSectionHeader(label: String, value: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CategoryQtyButton(row: CategoryRow, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(row.label, modifier = Modifier.weight(1f, fill = true))
            Text("${row.total}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MunicipalityStatusCard(row: ByMunicipalityStatusDto) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(row.municipality, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.padding(top = 4.dp))
            StatusCountRow("BS", row.bs, highlight = true)
            StatusCountRow("RV", row.rv)
            StatusCountRow("Transferred", row.transferred)
            StatusCountRow("Unlocated", row.unlocated)
        }
    }
}

@Composable
private fun StatusCountRow(label: String, count: Int, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            "$count",
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
