package com.deafregistry.app.ui.reports

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.remote.dto.ByMunicipalityDto
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.util.ExportUtils
import com.deafregistry.app.util.friendlyMessage
import kotlinx.coroutines.launch

private val SLICE_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFF9A825), Color(0xFFC62828),
    Color(0xFF6A1B9A), Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF4E342E),
    Color(0xFF546E7A), Color(0xFF9E9D24)
)

/**
 * Municipality distribution dashboard - viewable by every role (matches ReportsScreen's existing
 * convention: conductors can view reports, export/print stays admin-only). Reuses the existing
 * By-Municipality report data and the existing ReportCategoryDetailScreen for click-through, so
 * this screen is purely the chart visualization + sort/export controls on top of data that
 * already existed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MunicipalityStatisticsScreen(onBack: () -> Unit, onOpenMunicipality: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isAdmin = ServiceLocator.sessionManager.isAdmin()
    var rows by remember { mutableStateOf(listOf<ByMunicipalityDto>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sortDescending by remember { mutableStateOf(true) }
    var chartTab by remember { mutableStateOf(0) } // 0 = bar, 1 = pie
    var pendingExportFormat by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { ServiceLocator.reportRepository.byMunicipality() }
            .onSuccess { rows = it; error = null }
            .onFailure { error = friendlyMessage(it) }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val sortedRows = if (sortDescending) rows.sortedByDescending { it.total } else rows.sortedBy { it.total }
    val total = rows.sumOf { it.total }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "Municipality Statistics",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { sortDescending = !sortDescending }) {
                        Icon(Icons.Default.Sort, contentDescription = if (sortDescending) "Sorted highest first" else "Sorted lowest first")
                    }
                    if (isAdmin) {
                        IconButton(onClick = { pendingExportFormat = "csv" }) {
                            Icon(Icons.Default.TableChart, contentDescription = "Export to Excel/CSV")
                        }
                        IconButton(onClick = { pendingExportFormat = "pdf" }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export to PDF / Print")
                        }
                    }
                }
            )
        }
    ) { padding: PaddingValues ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = { scope.launch { reload() } },
            modifier = Modifier.padding(padding)
        ) {
            if (rows.isEmpty() && !loading) {
                EmptyState(error ?: "No registered individuals yet.")
            } else {
                Column(Modifier.fillMaxSize()) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                        SegmentedButton(
                            selected = chartTab == 0,
                            onClick = { chartTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Bar Chart") }
                        SegmentedButton(
                            selected = chartTab == 1,
                            onClick = { chartTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Pie Chart") }
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    if (chartTab == 0) {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(sortedRows, key = { it.municipality }) { row ->
                                MunicipalityBarRow(
                                    row = row,
                                    maxValue = sortedRows.maxOf { it.total }.coerceAtLeast(1),
                                    onClick = { onOpenMunicipality(row.municipality) }
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            MunicipalityPieChart(rows = sortedRows, total = total.coerceAtLeast(1))
                            Spacer(Modifier.height(16.dp))
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(sortedRows.withIndex().toList(), key = { it.value.municipality }) { (index, row) ->
                                    val percent = if (total > 0) row.total * 100f / total else 0f
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { onOpenMunicipality(row.municipality) }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(SLICE_COLORS[index % SLICE_COLORS.size]))
                                        Spacer(Modifier.width(10.dp))
                                        Text(row.municipality, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text("${"%.1f".format(percent)}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
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
            title = { Text(if (exportFormat == "csv") "Export to Excel (CSV)" else "Export to PDF") },
            text = { Text("Export the municipality distribution shown here (currently sorted ${if (sortDescending) "highest to lowest" else "lowest to highest"}).") },
            confirmButton = {
                TextButton(onClick = {
                    pendingExportFormat = null
                    scope.launch {
                        val header = listOf("Municipality", "Registered Individuals")
                        val dataRows = sortedRows.map { listOf(it.municipality, it.total.toString()) }
                        runCatching {
                            if (exportFormat == "csv") {
                                ExportUtils.exportCsv(context, "municipality_statistics.csv", header, dataRows, title = "Municipality Statistics")
                            } else {
                                ExportUtils.exportPdf(context, "municipality_statistics.pdf", "Municipality Statistics", header, dataRows)
                            }
                        }.onFailure { error = friendlyMessage(it) }
                    }
                }) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { pendingExportFormat = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MunicipalityBarRow(row: ByMunicipalityDto, maxValue: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.municipality, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(row.total.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(row.total.toFloat() / maxValue.toFloat())
                        .height(10.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50.dp))
                )
            }
        }
    }
}

@Composable
private fun MunicipalityPieChart(rows: List<ByMunicipalityDto>, total: Int) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            val diameter = kotlin.math.min(size.width, size.height)
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            rows.forEachIndexed { index, row ->
                val sweep = 360f * row.total / total
                drawArc(
                    color = SLICE_COLORS[index % SLICE_COLORS.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = Size(diameter, diameter)
                )
                startAngle += sweep
            }
        }
    }
}
