package com.deafregistry.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState

/**
 * Shows the deaf individuals behind one Reports drill-down (e.g. tapping "BS" under By
 * Monitoring Status, or a specific municipality under By Municipality). Reads from the local
 * Room cache like the rest of the app's browse screens - no network round trip needed since
 * report categories map directly onto fields already stored on each individual's record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCategoryDetailScreen(
    category: String,
    value: String,
    extra: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val flow = remember(category, value, extra) {
        when (category) {
            "municipality" -> ServiceLocator.deafIndividualRepository.observeByCategory(municipalityName = value)
            "barangay" -> ServiceLocator.deafIndividualRepository.observeByCategory(municipalityName = extra, barangayName = value)
            "gender" -> ServiceLocator.deafIndividualRepository.observeByCategory(gender = value)
            "skill" -> ServiceLocator.deafIndividualRepository.observeByCategory(skillLevel = value)
            "status" -> ServiceLocator.deafIndividualRepository.observeByCategory(monitoringStatus = value)
            "conductor" -> ServiceLocator.deafIndividualRepository.observeByCategory(teacherName = value)
            else -> ServiceLocator.deafIndividualRepository.observeByCategory()
        }
    }
    val individuals by flow.collectAsState(initial = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = value, onBack = onBack) }
    ) { padding: PaddingValues ->
        if (individuals.isEmpty()) {
            EmptyState("No records found for \"$value\".")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(individuals, key = { it.uuid }) { individual ->
                    CategoryIndividualRow(individual) { onOpenProfile(individual.uuid) }
                }
            }
        }
    }
}

@Composable
private fun CategoryIndividualRow(individual: DeafIndividualEntity, onClick: () -> Unit) {
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
