package com.readr.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.AppPrefs
import com.readr.app.domain.model.DocumentFormat
import com.readr.app.domain.model.LibrarySort
import com.readr.app.domain.model.LibraryViewMode
import com.readr.app.domain.model.ReadrDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val documents: List<ReadrDocument> = emptyList(),
    val continueReading: List<ReadrDocument> = emptyList(),
    val favorites: List<ReadrDocument> = emptyList(),
    val recentImports: List<ReadrDocument> = emptyList(),
    val loading: Boolean = true,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.documentRepository
    private val prefs = container.preferencesRepository

    val searchQuery = MutableStateFlow("")
    val formatFilter = MutableStateFlow<DocumentFormat?>(null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val appPrefs = prefs.appPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPrefs())

    val uiState: StateFlow<LibraryUiState> = combine(
        repo.observeAll(),
        prefs.appPrefs,
        searchQuery.debounce(150),
        formatFilter,
    ) { docs, appPrefs, query, filter ->

        val continueReading = docs
            .filter { it.lastOpenedAt != null && it.progress > 0.02f && it.progress < 0.98f }
            .sortedByDescending { it.lastOpenedAt ?: 0 }
            .take(12)

        val favorites = docs.filter { it.favorite }

        val recentImports = docs
            .sortedByDescending { it.addedAt }
            .take(12)

        val comparator: Comparator<ReadrDocument> = when (appPrefs.librarySort) {
            LibrarySort.TITLE -> compareBy { it.title.lowercase() }
            LibrarySort.ADDED -> compareByDescending { it.addedAt }
            LibrarySort.LAST_OPENED -> compareByDescending { it.lastOpenedAt ?: 0 }
            LibrarySort.FORMAT -> compareBy({ it.format.name }, { it.title.lowercase() })
        }

        val visible = docs
            .filter { filter == null || it.format == filter }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    (it.author?.contains(query, ignoreCase = true) == true)
            }
            .sortedWith(comparator)

        LibraryUiState(
            documents = visible,
            continueReading = continueReading,
            favorites = favorites,
            recentImports = recentImports,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun setQuery(value: String) {
        searchQuery.value = value
    }

    fun setFilter(format: DocumentFormat?) {
        formatFilter.value = format
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { prefs.setLibrarySort(sort) }
    }

    fun setView(view: LibraryViewMode) {
        viewModelScope.launch { prefs.setLibraryView(view) }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val outcome = container.importer.import(uris)
            _importing.value = false
            _message.value = when {
                outcome.failures.isNotEmpty() && outcome.importedCount == 0 ->
                    outcome.failures.first()
                outcome.failures.isNotEmpty() ->
                    "Imported ${outcome.importedCount}, ${outcome.failures.size} failed: ${outcome.failures.first()}"
                else ->
                    "Imported ${outcome.importedCount} document${if (outcome.importedCount == 1) "" else "s"}"
            }
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            val current = repo.get(id)?.favorite ?: false
            repo.setFavorite(id, !current)
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repo.remove(id) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
