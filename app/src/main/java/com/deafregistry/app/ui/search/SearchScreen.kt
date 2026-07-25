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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import com.deafregistry.app.ui.common.EmptyState
import com.deafregistry.app.ui.common.GenericViewModelFactory
import com.deafregistry.app.ui.common.SearchStateHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val viewModel: SearchViewModel = viewModel(
        factory = GenericViewModelFactory { SearchViewModel(ServiceLocator.deafIndividualRepository) }
    )
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    // Restores the last search after coming back to this screen - a fresh SearchViewModel starts
    // blank, so without this the search would silently reset every time you navigate away and back.
    LaunchedEffect(Unit) {
        if (query.isBlank() && SearchStateHolder.searchQuery.isNotBlank()) {
            viewModel.updateQuery(SearchStateHolder.searchQuery)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Search", onBack = onBack)
        }
    ) { padding: PaddingValues ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    viewModel.updateQuery(it)
                    SearchStateHolder.searchQuery = it
                },
                label = { Text("Name, municipality, barangay, conductor, skill, status") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )

            if (query.isBlank()) {
                EmptyState("Start typing to search the registry.")
            } else if (results.isEmpty()) {
                EmptyState("No matches found.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.uuid }) { individual ->
                        val isBsRecord = individual.monitoringStatus == "BS"
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { onOpenProfile(individual.uuid) },
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
                                    "${individual.municipalityName} • ${individual.barangayName} • ${individual.monitoringStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isBsRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isBsRecord) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
