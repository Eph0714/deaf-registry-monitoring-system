package com.deafregistry.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A "select from list only" field that opens a searchable picker dialog rather than an inline
 * dropdown menu - used where the option list can be long enough that scanning a plain dropdown is
 * awkward (e.g. barangays) and where "no manual typing of the final value" matters (the visible
 * field itself stays read-only; only the dialog's search box is free-text, and that's just a
 * filter, never itself a value that gets saved).
 */
@Composable
fun SearchableSelectField(
    label: String,
    options: List<Pair<Int, String>>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: ""

    OutlinedTextField(
        value = selectedLabel,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp).height(20.dp))
            } else {
                IconButton(onClick = { showDialog = true }, enabled = enabled) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select $label")
                }
            }
        },
        modifier = modifier
    )

    if (showDialog) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(query, options) {
            if (query.isBlank()) options else options.filter { it.second.contains(query, ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select $label") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (filtered.isEmpty()) {
                        Text(
                            "No matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(320.dp)) {
                            items(filtered, key = { it.first }) { (id, name) ->
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelect(id)
                                            showDialog = false
                                        }
                                        .padding(vertical = 14.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}
