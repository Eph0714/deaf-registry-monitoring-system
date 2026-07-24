package com.deafregistry.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.deafregistry.app.di.ServiceLocator
import com.deafregistry.app.ui.common.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageMunicipalitiesScreen(onBack: () -> Unit) {
    val repo = ServiceLocator.referenceDataRepository
    val municipalities by repo.observeMunicipalities().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = "Municipalities", onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newName = ""; showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
    ) { padding: PaddingValues ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            items(municipalities, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { repo.deleteMunicipality(m.id) }
                                runCatching { repo.refreshAll() }
                            }
                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Municipality") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    scope.launch {
                        runCatching { repo.createMunicipality(newName.trim()) }
                        runCatching { repo.refreshAll() }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}
