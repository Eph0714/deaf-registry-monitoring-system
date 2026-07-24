package com.deafregistry.app.ui.reports

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.data.remote.dto.ByMunicipalityStatusDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.FullScreenLoading
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.util.ExportUtils
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val viewModel: ReportsViewModel = viewModel(
        factory = GenericViewModelFactory { ReportsViewModel(ServiceLocator.reportRepository) }
    )
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

                item { ReportSection("By Municipality", state.byMunicipality.map { it.municipality to it.total }) }
                item {
                    Text("By Municipality — Status", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                }
                items(state.byMunicipalityStatus) { row -> MunicipalityStatusCard(row) }
                item { ReportSection("By Barangay", state.byBarangay.map { "${it.municipality} / ${it.barangay}" to it.total }) }
                item { ReportSection("By Gender", state.byGender.map { it.gender to it.total }) }
                item { ReportSection("By Skill Level", state.bySkill.map { it.skillLevel to it.total }) }
                item { ReportSection("By Monitoring Status", state.byStatus.map { it.monitoringStatus to it.total }) }
                item { ReportSection("By BS Conductor", state.byConductor.map { it.conductor to it.total }) }

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

                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val path = ExportUtils.exportCsv(
                                context, "deaf_registry_report.csv",
                                listOf("Municipality", "Total"),
                                state.byMunicipality.map { listOf(it.municipality, it.total.toString()) }
                            )
                            Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                        }) { Text("Export CSV (Excel)") }

                        OutlinedButton(onClick = {
                            val path = ExportUtils.exportPdf(
                                context, "deaf_registry_report.pdf", "Deaf Registry Summary Report",
                                listOf("Municipality", "Total"),
                                state.byMunicipality.map { listOf(it.municipality, it.total.toString()) }
                            )
                            Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                        }) { Text("Export PDF") }
                    }
                }
            }
        }
    }
}

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

@Composable
private fun ReportSection(title: String, rows: List<Pair<String, Int>>) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        rows.forEach { (label, total) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label)
                Text("$total")
            }
        }
    }
}
