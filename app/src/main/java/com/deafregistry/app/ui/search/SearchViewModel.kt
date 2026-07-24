package com.deafregistry.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deafregistry.app.data.local.entity.DeafIndividualEntity
import com.deafregistry.app.data.repository.DeafIndividualRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(private val deafIndividualRepository: DeafIndividualRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<List<DeafIndividualEntity>> = _query
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else deafIndividualRepository.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(value: String) {
        _query.value = value
    }
}
