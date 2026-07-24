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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllIndividualsScreen(
    title: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddNew: () -> Unit,
    sort: String = "name"
) {
    val flow = remember(sort) {
        when (sort) {
            "conductor" -> ServiceLocator.deafIndividualRepository.observeAllActiveByConductor()
            "age" -> ServiceLocator.deafIndividualRepository.observeAllActiveByAge()
            "lastVisit" -> ServiceLocator.deafIndividualRepository.observeAllActiveByLastVisit()
            else -> ServiceLocator.deafIndividualRepository.observeAllActive()
        }
    }
    val individuals by flow.collectAsState(initial = emptyList())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = title, onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        if (individuals.isEmpty()) {
            EmptyState("No registered individuals yet.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)) {
                items(individuals, key = { it.uuid }) { individual ->
                    IndividualRow(individual) { onOpenProfile(individual.uuid) }
                }
            }
        }
    }
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
